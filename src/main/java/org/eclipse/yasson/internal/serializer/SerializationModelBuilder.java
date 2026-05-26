/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.yasson.internal.serializer;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import jakarta.json.bind.JsonbException;
import org.eclipse.yasson.internal.ComponentBindingResolver;
import org.eclipse.yasson.internal.JsonBindingContext;
import org.eclipse.yasson.internal.ReflectionHelper;
import org.eclipse.yasson.internal.components.AdapterBindingInfo;
import org.eclipse.yasson.internal.components.JsonbSerializerBinding;
import org.eclipse.yasson.internal.model.BeanPropertyDescriptor;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.model.customization.ComponentBindingCustomization;
import org.eclipse.yasson.internal.model.customization.SerializationCustomizer;
import org.eclipse.yasson.internal.model.customization.TypeInheritanceSettings;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;
import org.eclipse.yasson.internal.serializer.types.TypeBasedObjectSerializer;
import org.eclipse.yasson.internal.serializer.types.TypeSerializerRegistry;

/**
 * Create or obtain already created type serializer.
 */
public class SerializationModelBuilder {

    private final Map<Type, ModelMarshaller> explicitSerializers = new ConcurrentHashMap<>();

    private final Map<Type, ModelMarshaller> dynamicSerializers = new ConcurrentHashMap<>();

    private final JsonBindingContext jsonbBindingContext;

    /**
     * Create new instance.
     *
     * @param jsonbBindingContext jsonb context
     */
    public SerializationModelBuilder(JsonBindingContext jsonbBindingContext) {
        this.jsonbBindingContext = jsonbBindingContext;
    }

    /**
     * Wrap {@link ModelMarshaller} in the common set of serializers.
     *
     * @param modelMarshaller serializer to be wrapped
     * @param serializationCustomizer   component customization
     * @param jsonbBindingContext    jsonb context
     * @return wrapped serializer
     */
    public static ModelMarshaller wrapWithCommonSet(ModelMarshaller modelMarshaller, SerializationCustomizer serializationCustomizer, JsonBindingContext jsonbBindingContext) {
        return Stream.of(modelMarshaller).map(KeySerializer::new).map(marshallingFunction -> new NullValueSerializer(marshallingFunction, serializationCustomizer, jsonbBindingContext)).findFirst().get();
    }

    /**
     * Create new {@link ModelMarshaller} of the given type.
     *
     * @param targetType               type to be serialized
     * @param isRoot          whether it is a root value
     * @param useRootAdapter whether to resolve root adapter
     * @return type model serializer
     */
    public ModelMarshaller buildSerializerChain(Type targetType, boolean isRoot, boolean useRootAdapter) {
        Class<?> rawClass = ReflectionHelper.getRawType(targetType);
        ClassDescriptor classDescriptor = jsonbBindingContext.getMappingContext().getOrCreateClassModel(rawClass);
        LinkedList<Type> typeSequence = new LinkedList<>();
        return buildSerializerChain(typeSequence, targetType, classDescriptor.getClassCustomization(), isRoot, false, useRootAdapter);
    }

    /**
     * Create new {@link ModelMarshaller} of the given type.
     *
     * @param typeSequence                 chain of types used before the one currently processed
     * @param targetType                  type to be serialized
     * @param propertyCustomizer component customization
     * @param isRoot             whether it is a root value
     * @param keyFlag                 whether it is a key
     * @return type model serializer
     */
    public ModelMarshaller resolveSerializerChainRuntime(LinkedList<Type> typeSequence, Type targetType, SerializationCustomizer propertyCustomizer, boolean isRoot, boolean keyFlag) {
        if (typeSequence.contains(targetType)) {
            return new CircularReferenceSerializer(targetType);
        }
        //If the class instance and class of the field are the same and there has been generics specified for this field,
        //we need to use those instead of raw type.
        Class<?> rawClass = ReflectionHelper.getRawType(targetType);
        Class<?> lastRawClass = ReflectionHelper.getRawType(typeSequence.getLast());
        if (lastRawClass.equals(rawClass)) {
            return buildSerializerChainInternal(typeSequence, typeSequence.getLast(), propertyCustomizer, isRoot, keyFlag, true);
        }
        return buildSerializerChainInternal(typeSequence, targetType, propertyCustomizer, isRoot, keyFlag, true);
    }

    private ModelMarshaller buildSerializerChain(LinkedList<Type> typeSequence, Type targetType, SerializationCustomizer propertyCustomizer, boolean isRoot, boolean keyFlag, boolean useRootAdapter) {
        if (typeSequence.contains(targetType)) {
            return new CircularReferenceSerializer(targetType);
        }
        try {
            typeSequence.add(targetType);
            return buildSerializerChainInternal(typeSequence, targetType, propertyCustomizer, isRoot, keyFlag, useRootAdapter);
        } finally {
            typeSequence.removeLast();
        }
    }

    private ModelMarshaller buildSerializerChainInternal(LinkedList<Type> typeSequence, Type targetType, SerializationCustomizer propertyCustomizer, boolean isRoot, boolean keyFlag, boolean useRootAdapter) {
        if (explicitSerializers.containsKey(targetType)) {
            return explicitSerializers.get(targetType);
        }
        Class<?> rawClass = ReflectionHelper.getRawType(targetType);
        Optional<ModelMarshaller> optionalMarshaller = resolveUserSerializer(targetType, (ComponentBindingCustomization) propertyCustomizer);
        if (optionalMarshaller.isPresent()) {
            return optionalMarshaller.get();
        }
        if (useRootAdapter) {
            Optional<AdapterBindingInfo> adapterBindingOpt = resolveAdapterBinding(targetType, (ComponentBindingCustomization) propertyCustomizer);
            if (adapterBindingOpt.isPresent()) {
                AdapterBindingInfo adapterInfo = adapterBindingOpt.get();
                Type targetTypeLocal = adapterInfo.getToType();
                Class<?> rawTargetClass = ReflectionHelper.getRawType(targetTypeLocal);
                ModelMarshaller resolvedMarshaller = TypeSerializerRegistry.getTypeSerializer(rawTargetClass, propertyCustomizer, jsonbBindingContext);
                if (null == resolvedMarshaller) {
                    resolvedMarshaller = buildSerializerChain(targetTypeLocal, isRoot, !targetType.equals(targetTypeLocal));
                }
                AdapterMarshaller adapterMarshaller = new AdapterMarshaller(adapterInfo, resolvedMarshaller);
                RecursionDetector recursionDetector = new RecursionDetector(adapterMarshaller);
                NullValueSerializer nullValueSerializer = new NullValueSerializer(recursionDetector, propertyCustomizer, jsonbBindingContext);
                explicitSerializers.put(targetType, nullValueSerializer);
                return nullValueSerializer;
            }
        }
        ModelMarshaller resolvedMarshaller = null;
        if (!Object.class.equals(rawClass)) {
            resolvedMarshaller = TypeSerializerRegistry.getTypeSerializer(typeSequence, rawClass, propertyCustomizer, jsonbBindingContext, keyFlag);
        }
        if (null != resolvedMarshaller) {
            if (jsonbBindingContext.getConfigProperties().isStrictIJson() && isRoot) {
                throw new JsonbException(MessageProvider.getMessage(MessageConstants.IJSON_ENABLED_SINGLE_VALUE));
            }
            return resolvedMarshaller;
        }
        ClassDescriptor classDescriptor = jsonbBindingContext.getMappingContext().getOrCreateClassModel(rawClass);
        if (!Collection.class.isAssignableFrom(rawClass)) {
            if (!Map.class.isAssignableFrom(rawClass)) {
                if (!rawClass.isArray()) {
                    if (!(targetType instanceof GenericArrayType)) {
                        if (Optional.class.equals(rawClass)) {
                            return buildOptionalSerializer(typeSequence, targetType, propertyCustomizer, keyFlag);
                        }
                    } else {
                        return buildGenericArraySerializer(typeSequence, targetType, propertyCustomizer);
                    }
                } else {
                    return buildArraySerializer(typeSequence, rawClass, propertyCustomizer);
                }
            } else {
                return buildMapSerializer(typeSequence, targetType, propertyCustomizer);
            }
        } else {
            return buildCollectionSerializer(typeSequence, targetType, propertyCustomizer);
        }
        return buildObjectSerializer(typeSequence, targetType, classDescriptor);
    }

    private ModelMarshaller buildObjectSerializer(LinkedList<Type> typeSequence, Type targetType, ClassDescriptor classDescriptor) {
        LinkedHashMap<String, ModelMarshaller> propertyMarshallers = new LinkedHashMap<>();
        TypeInheritanceSettings inheritanceConfig = classDescriptor.getClassCustomization().getPolymorphismConfig();
        if (null != inheritanceConfig) {
            addPolymorphicProperty(inheritanceConfig, propertyMarshallers, classDescriptor);
        }
        for (BeanPropertyDescriptor propDescriptor : classDescriptor.getSortedProperties()) {
            if (propDescriptor.isReadable()) {
                String propName = propDescriptor.getWriteName();
                ModelMarshaller memberMarshaller = resolveMemberSerializer(typeSequence, propDescriptor.getPropertySerializationType(), propDescriptor.getCustomization(), false);
                propertyMarshallers.put(propName, new ValueGetterDelegatingSerializer(propDescriptor.getGetValueHandle(), memberMarshaller));
            }
        }
        ModelMarshaller objectMarshaller = new ObjectMarshaller(propertyMarshallers);
        RecursionDetector recursionDetector = new RecursionDetector(objectMarshaller);
        KeySerializer keySerializer = new KeySerializer(recursionDetector);
        NullVisibilityToggle nullToggle = new NullVisibilityToggle(false, keySerializer);
        NullValueSerializer nullValueSerializer = new NullValueSerializer(nullToggle, classDescriptor.getClassCustomization(), jsonbBindingContext);
        explicitSerializers.put(targetType, nullValueSerializer);
        return nullValueSerializer;
    }

    private void addPolymorphicProperty(TypeInheritanceSettings inheritanceConfig, LinkedHashMap<String, ModelMarshaller> propertyMarshallers, ClassDescriptor classDescriptor) {
        Class<?> rawClass = classDescriptor.getType();
        String typeKey = inheritanceConfig.getAliases().get(rawClass);
        ModelMarshaller marshallingFunction = createPolymorphicPropertySerializer(inheritanceConfig, typeKey);
        if (null != marshallingFunction) {
            if (null != inheritanceConfig.getParentConfig()) {
                addParentPolymorphicProperty(inheritanceConfig.getParentConfig(), propertyMarshallers, classDescriptor);
            }
            propertyMarshallers.put(inheritanceConfig.getFieldName(), marshallingFunction);
        }
        for (BeanPropertyDescriptor propDescriptor : classDescriptor.getSortedProperties()) {
            if (propertyMarshallers.containsKey(propDescriptor.getWriteName())) {
                throw new JsonbException("CHANGE naming conflict!");
            }
        }
    }

    private void addParentPolymorphicProperty(TypeInheritanceSettings inheritanceConfig, LinkedHashMap<String, ModelMarshaller> propertyMarshallers, ClassDescriptor classDescriptor) {
        Class<?> rawClass = classDescriptor.getType();
        TypeInheritanceSettings currentConfig = inheritanceConfig;
        LinkedHashMap<String, ModelMarshaller> additionsMap = new LinkedHashMap<>();
        while (null != currentConfig) {
            TypeInheritanceSettings nestedConfig = currentConfig;
            String typeKey = nestedConfig.getAliases().entrySet().stream().filter(mapPair -> mapPair.getKey().isAssignableFrom(rawClass)).map(Map.Entry::getValue).findFirst().orElse(null);
            if (null != typeKey) {
                ModelMarshaller marshallingFunction = createPolymorphicPropertySerializer(nestedConfig, typeKey);
                additionsMap.put(currentConfig.getFieldName(), marshallingFunction);
                currentConfig = currentConfig.getParentConfig();
            }
        }
        ListIterator<Map.Entry<String, ModelMarshaller>> listCursor = new ArrayList<>(additionsMap.entrySet()).listIterator(additionsMap.size());
        while (listCursor.hasPrevious()) {
            Map.Entry<String, ModelMarshaller> mapPair = listCursor.previous();
            propertyMarshallers.put(mapPair.getKey(), mapPair.getValue());
        }
    }

    private ModelMarshaller createPolymorphicPropertySerializer(TypeInheritanceSettings inheritanceConfig, String typeKey) {
        if (null != typeKey) {
            return (value, jsonWriter, context) -> jsonWriter.write(inheritanceConfig.getFieldName(), typeKey);
        }
        return null;
    }

    private ModelMarshaller buildCollectionSerializer(LinkedList<Type> typeSequence, Type targetType, SerializationCustomizer serializationCustomizer) {
        Type collectionElement = targetType instanceof ParameterizedType ? ((ParameterizedType) targetType).getActualTypeArguments()[0] : Object.class;
        ModelMarshaller resolvedMarshaller = resolveMemberSerializer(typeSequence, collectionElement, serializationCustomizer, false);
        CollectionMarshaller collectionMarshaller = new CollectionMarshaller(resolvedMarshaller);
        KeySerializer keySerializer = new KeySerializer(collectionMarshaller);
        NullVisibilityToggle nullToggle = new NullVisibilityToggle(true, keySerializer);
        return new NullValueSerializer(nullToggle, serializationCustomizer, jsonbBindingContext);
    }

    private ModelMarshaller buildMapSerializer(LinkedList<Type> typeSequence, Type targetType, SerializationCustomizer propertyCustomizer) {
        Type keyClass = targetType instanceof ParameterizedType ? ((ParameterizedType) targetType).getActualTypeArguments()[0] : Object.class;
        Type valueClass = targetType instanceof ParameterizedType ? ((ParameterizedType) targetType).getActualTypeArguments()[1] : Object.class;
        Type keyResolution = ReflectionHelper.determineType(typeSequence, keyClass);
        Class<?> componentClass = ReflectionHelper.getRawType(keyResolution);
        ModelMarshaller keyMarshaller = resolveMemberSerializer(typeSequence, keyClass, ClassSerializationConfig.emptyConfig(), true);
        ModelMarshaller valueMarshaller = resolveMemberSerializer(typeSequence, valueClass, propertyCustomizer, false);
        AbstractMapSerializer mapMarshaller = AbstractMapSerializer.createMapSerializer(componentClass, keyMarshaller, valueMarshaller);
        KeySerializer keySerializer = new KeySerializer(mapMarshaller);
        NullVisibilityToggle nullToggle = new NullVisibilityToggle(true, keySerializer);
        return new NullValueSerializer(nullToggle, propertyCustomizer, jsonbBindingContext);
    }

    private ModelMarshaller buildArraySerializer(LinkedList<Type> typeSequence, Class<?> arrayClass, SerializationCustomizer propertyCustomizer) {
        Class<?> componentClass = arrayClass.getComponentType();
        ModelMarshaller modelMarshaller = resolveMemberSerializer(typeSequence, componentClass, propertyCustomizer, false);
        ModelMarshaller arrayMarshaller = AbstractArraySerializer.createEncoder(arrayClass, jsonbBindingContext, modelMarshaller);
        KeySerializer keySerializer = new KeySerializer(arrayMarshaller);
        NullVisibilityToggle nullToggle = new NullVisibilityToggle(true, keySerializer);
        return new NullValueSerializer(nullToggle, propertyCustomizer, jsonbBindingContext);
    }

    private ModelMarshaller buildGenericArraySerializer(LinkedList<Type> typeSequence, Type targetType, SerializationCustomizer propertyCustomizer) {
        Class<?> arrayClass = ReflectionHelper.getRawType(targetType);
        Class<?> elementClass = ReflectionHelper.getRawType(((GenericArrayType) targetType).getGenericComponentType());
        ModelMarshaller modelMarshaller = resolveMemberSerializer(typeSequence, elementClass, propertyCustomizer, false);
        ModelMarshaller arrayMarshaller = AbstractArraySerializer.createEncoder(arrayClass, jsonbBindingContext, modelMarshaller);
        KeySerializer keySerializer = new KeySerializer(arrayMarshaller);
        NullVisibilityToggle nullToggle = new NullVisibilityToggle(true, keySerializer);
        return new NullValueSerializer(nullToggle, propertyCustomizer, jsonbBindingContext);
    }

    private ModelMarshaller buildOptionalSerializer(LinkedList<Type> typeSequence, Type targetType, SerializationCustomizer propertyCustomizer, boolean keyFlag) {
        Type optionalDescriptor = targetType instanceof ParameterizedType ? ((ParameterizedType) targetType).getActualTypeArguments()[0] : Object.class;
        ModelMarshaller modelMarshaller = resolveMemberSerializer(typeSequence, optionalDescriptor, propertyCustomizer, keyFlag);
        return new OptionalValueSerializer(modelMarshaller);
    }

    private ModelMarshaller resolveMemberSerializer(LinkedList<Type> typeSequence, Type targetType, SerializationCustomizer serializationCustomizer, boolean keyFlag) {
        Type determinedDescriptor = ReflectionHelper.determineType(typeSequence, targetType);
        Class<?> rawClass = ReflectionHelper.getRawType(determinedDescriptor);
        Optional<ModelMarshaller> optionalMarshaller = resolveUserSerializer(determinedDescriptor, (ComponentBindingCustomization) serializationCustomizer);
        if (optionalMarshaller.isPresent()) {
            return optionalMarshaller.get();
        }
        Optional<AdapterBindingInfo> adapterBindingOpt = resolveAdapterBinding(determinedDescriptor, (ComponentBindingCustomization) serializationCustomizer);
        if (adapterBindingOpt.isPresent()) {
            AdapterBindingInfo adapterInfo = adapterBindingOpt.get();
            Type targetTypeLocal = adapterInfo.getToType();
            Class<?> rawTargetClass = ReflectionHelper.getRawType(targetTypeLocal);
            ModelMarshaller resolvedMarshaller = TypeSerializerRegistry.getTypeSerializer(rawTargetClass, serializationCustomizer, jsonbBindingContext);
            if (null == resolvedMarshaller) {
                resolvedMarshaller = buildSerializerChain(targetTypeLocal, false, true);
            }
            AdapterMarshaller adapterMarshaller = new AdapterMarshaller(adapterInfo, resolvedMarshaller);
            return new NullValueSerializer(adapterMarshaller, serializationCustomizer, jsonbBindingContext);
        }
        ModelMarshaller resolvedMarshaller = TypeSerializerRegistry.getTypeSerializer(typeSequence, rawClass, serializationCustomizer, jsonbBindingContext, keyFlag);
        if (null == resolvedMarshaller) {
            //Final classes dont have any child classes. It is safe to assume that there will be instance of that specific class.
            boolean finalFlag = Modifier.isFinal(rawClass.getModifiers());
            if (!finalFlag && !Collection.class.isAssignableFrom(rawClass) && !Map.class.isAssignableFrom(rawClass)) {
                if (dynamicSerializers.containsKey(determinedDescriptor)) {
                    return dynamicSerializers.get(determinedDescriptor);
                }
                boolean abstractFlag = Modifier.isAbstract(rawClass.getModifiers());
                ModelMarshaller concreteMarshaller = null;
                if (!abstractFlag && !rawClass.equals(Object.class)) {
                    if (!explicitSerializers.containsKey(determinedDescriptor)) {
                        concreteMarshaller = buildSerializerChain(typeSequence, determinedDescriptor, serializationCustomizer, false, keyFlag, true);
                    } else {
                        concreteMarshaller = explicitSerializers.get(determinedDescriptor);
                    }
                }
                //Needs to be dynamically resolved with special cache since possible inheritance problem.
                if (!(determinedDescriptor instanceof Class)) {
                    typeSequence.add(determinedDescriptor);
                    resolvedMarshaller = TypeSerializerRegistry.getTypeSerializer(typeSequence, Object.class, serializationCustomizer, jsonbBindingContext, keyFlag);
                    typeSequence.removeLast();
                } else {
                    resolvedMarshaller = TypeSerializerRegistry.getTypeSerializer(typeSequence, Object.class, serializationCustomizer, jsonbBindingContext, keyFlag);
                }
                if (null != concreteMarshaller && resolvedMarshaller instanceof TypeBasedObjectSerializer) {
                    ((TypeBasedObjectSerializer) resolvedMarshaller).registerSpecificSerializer(rawClass, concreteMarshaller);
                }
                //Since typeSerializer is handled as Object currently, we need to wrap it with null checker (if it is not a key)
                if (!keyFlag) {
                    resolvedMarshaller = new NullValueSerializer(resolvedMarshaller, serializationCustomizer, jsonbBindingContext);
                }
                dynamicSerializers.put(targetType, resolvedMarshaller);
            } else {
                return buildSerializerChain(typeSequence, determinedDescriptor, serializationCustomizer, false, keyFlag, true);
            }
        }
        if (!keyFlag && resolvedMarshaller instanceof TypeBasedObjectSerializer) {
            resolvedMarshaller = new NullValueSerializer(resolvedMarshaller, serializationCustomizer, jsonbBindingContext);
        }
        return resolvedMarshaller;
    }

    private Optional<ModelMarshaller> resolveUserSerializer(Type targetType, ComponentBindingCustomization componentCustomizer) {
        final ComponentBindingResolver bindingResolver = jsonbBindingContext.getComponentMatcher();
        return bindingResolver.getSerializerBinding(targetType, componentCustomizer).map(JsonbSerializerBinding::getJsonbSerializer).map(UserProvidedSerializer::new).map(RecursionDetector::new).map(marshallingFunction -> SerializationModelBuilder.wrapWithCommonSet(marshallingFunction, (SerializationCustomizer) componentCustomizer, jsonbBindingContext));
    }

    private Optional<AdapterBindingInfo> resolveAdapterBinding(Type targetType, ComponentBindingCustomization componentCustomizer) {
        return jsonbBindingContext.getComponentMatcher().getSerializeAdapterBinding(targetType, componentCustomizer);
    }
}

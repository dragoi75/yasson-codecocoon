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

package org.eclipse.yasson.internal.deserializer;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DeserializationContextManager;
import org.eclipse.yasson.internal.JsonbConfigProperties;
import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.JsonbDateFormatter;
import org.eclipse.yasson.internal.JsonbNumberFormatter;
import org.eclipse.yasson.internal.ReflectionUtils;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.deserializer.types.TypeDeserializers;
import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.model.CreatorProfile;
import org.eclipse.yasson.internal.model.JsonbCreatorInvoker;
import org.eclipse.yasson.internal.model.PropertyModel;
import org.eclipse.yasson.internal.model.customization.ClassCustomization;
import org.eclipse.yasson.internal.model.customization.ComponentBoundCustomization;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.model.customization.PropertyCustomization;
import org.eclipse.yasson.internal.model.customization.TypeInheritanceConfiguration;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

import static jakarta.json.bind.JsonbConfig.PROPERTY_NAMING_STRATEGY;
import static jakarta.json.stream.JsonParser.Event;

/**
 * Creator of the deserialization models for deserialized types.
 * <br>
 * This class servers also as a cache for all previously created model deserializers.
 */
public class DeserializationModelCreator {

    private static final ModelUnmarshaller<Object> NULL_PROVIDER = (value, context) -> null;
    private static final Map<Class<?>, ModelUnmarshaller<Object>> DEFAULT_CREATOR_VALUES;
    private static final Set<JsonParser.Event> MAP_KEY_EVENTS = new HashSet<>();

    static {
        MAP_KEY_EVENTS.add(Event.KEY_NAME);
        MAP_KEY_EVENTS.addAll(PositionChecker.Checker.VALUES.getEvents());

        Map<Class<?>, ModelUnmarshaller<Object>> tmpValuesMap = new HashMap<>();

        tmpValuesMap.put(byte.class, (value, context) -> (byte) 0);
        tmpValuesMap.put(short.class, (value, context) -> (short) 0);
        tmpValuesMap.put(int.class, (value, context) -> 0);
        tmpValuesMap.put(long.class, (value, context) -> 0L);
        tmpValuesMap.put(float.class, (value, context) -> 0.0F);
        tmpValuesMap.put(double.class, (value, context) -> 0.0);
        tmpValuesMap.put(char.class, (value, context) -> '\u0000');
        tmpValuesMap.put(boolean.class, (value, context) -> false);
        tmpValuesMap.put(Optional.class, (value, context) -> Optional.empty());
        tmpValuesMap.put(OptionalInt.class, (value, context) -> OptionalInt.empty());
        tmpValuesMap.put(OptionalLong.class, (value, context) -> OptionalLong.empty());
        tmpValuesMap.put(OptionalDouble.class, (value, context) -> OptionalDouble.empty());

        DEFAULT_CREATOR_VALUES = Map.copyOf(tmpValuesMap);
    }

    private final Map<CachedItem, ModelUnmarshaller<JsonParser>> models = new ConcurrentHashMap<>();

    private final JsonbContext jsonbContext;
    private final Map<Class<?>, Class<?>> userTypeMapping;

    /**
     * Create new instance.
     *
     * @param jsonbContext jsonb context
     */
    public DeserializationModelCreator(JsonbContext jsonbContext) {
        this.jsonbContext = jsonbContext;
        this.userTypeMapping = jsonbContext.getConfigProperties().getUserTypeMapping();
    }

    /**
     * Starts deserializer creation process.
     *
     * @param type type the deserializer is created for
     * @return created deserializer
     */
    public ModelUnmarshaller<JsonParser> deserializerChain(Type type) {
        LinkedList<Type> chain = new LinkedList<>();
        ClassModel classModel = jsonbContext.getMappingContext().getOrCreateClassModel(ReflectionUtils.getRawType(type));
        return deserializerChain(chain, type, classModel.getClassCustomization(), classModel);
    }

    private ModelUnmarshaller<JsonParser> deserializerChain(LinkedList<Type> chain,
                                                            Type type,
                                                            Customization propertyCustomization,
                                                            ClassModel classModel) {
        if (chain.contains(type)) {
            return new CyclicReferenceDeserializer(type);
        }
        try {
            chain.add(type);
            return deserializerChainInternal(chain, type, propertyCustomization, classModel);
        } finally {
            chain.removeLast();
        }
    }

    private ModelUnmarshaller<JsonParser> deserializerChainInternal(LinkedList<Type> chain,
                                                                    Type type,
                                                                    Customization propertyCustomization,
                                                                    ClassModel classModel) {
        Class<?> rawType = classModel.getType();
        CachedItem cachedItem = createCachedItem(type, propertyCustomization);
        if (models.containsKey(cachedItem)) {
            return models.get(cachedItem);
        } else if (userTypeMapping.containsKey(rawType)) {
            Class<?> userTypeRaw = userTypeMapping.get(rawType);
            ModelUnmarshaller<JsonParser> deserializer = deserializerChain(userTypeRaw);
            models.put(cachedItem, deserializer);
            return deserializer;
        }
        Optional<AdapterBinding> adapterBinding = adapterBinding(type, (ComponentBoundCustomization) propertyCustomization);
        if (adapterBinding.isPresent()) {
            AdapterBinding adapter = adapterBinding.get();
            Class<?> toType = ReflectionUtils.getRawType(adapter.getToType());
            ClassModel targetModel = jsonbContext.getMappingContext().getOrCreateClassModel(toType);
            ModelUnmarshaller<JsonParser> typeDeserializer = typeDeserializer(toType,
                                                                              targetModel.getClassCustomization(),
                                                                              JustReturn.instance());
            if (typeDeserializer == null) {
                typeDeserializer = deserializerChain(adapter.getToType());
            }
            ModelUnmarshaller<JsonParser> targetAdapterModel = typeDeserializer;
            AdapterDeserializer adapterDeserializer = new AdapterDeserializer(adapter, JustReturn.instance());
            ModelUnmarshaller<JsonParser> adapterDeser = (parser, context) -> {
                Object fromJson = targetAdapterModel.unmarshal(parser, context);
                return adapterDeserializer.unmarshal(fromJson, context);
            };
            models.put(cachedItem, adapterDeser);
            return adapterDeser;
        }
        ModelUnmarshaller<JsonParser> typeDeserializer = typeDeserializer(rawType,
                                                                          propertyCustomization,
                                                                          JustReturn.instance());
        if (typeDeserializer != null) {
            models.put(cachedItem, typeDeserializer);
            return typeDeserializer;
        }
        if (Collection.class.isAssignableFrom(rawType)) {
            return createCollectionDeserializer(cachedItem, rawType, chain, propertyCustomization);
        } else if (Map.class.isAssignableFrom(rawType)) {
            return createMapDeserializer(cachedItem, rawType, chain, propertyCustomization);
        } else if (rawType.isArray()) {
            return createArrayDeserializer(cachedItem, rawType, chain, propertyCustomization);
        } else if (type instanceof GenericArrayType) {
            return createGenericArray(cachedItem, rawType, chain, propertyCustomization);
        } else if (Optional.class.isAssignableFrom(rawType)) {
            return createOptionalDeserializer(chain, type, propertyCustomization, cachedItem);
        } else {
            return createObjectDeserializer(chain, type, propertyCustomization, classModel, rawType, cachedItem);
        }
    }

    private ModelUnmarshaller<JsonParser> createObjectDeserializer(LinkedList<Type> chain,
                                                                   Type type,
                                                                   Customization propertyCustomization,
                                                                   ClassModel classModel,
                                                                   Class<?> rawType,
                                                                   CachedItem cachedItem) {
        ClassCustomization classCustomization = classModel.getClassCustomization();
        Optional<DeserializerBinding<?>> deserializerBinding = userDeserializer(type,
                                                                                (ComponentBoundCustomization) propertyCustomization);
        if (deserializerBinding.isPresent()) {
            UserDefinedDeserializer user = new UserDefinedDeserializer(deserializerBinding.get().getJsonbDeserializer(),
                                                                       JustReturn.instance(), type, classCustomization);
            models.put(cachedItem, user);
            return user;
        }
        JsonbCreatorInvoker creator = classCustomization.getCreator();
        boolean hasCreator = creator != null;
        List<String> params = hasCreator ? creatorParamsList(creator) : Collections.emptyList();
        Function<String, String> renamer = propertyRenamer();
        Map<String, ModelUnmarshaller<JsonParser>> processors = new LinkedHashMap<>();
        Map<String, ModelUnmarshaller<Object>> defaultCreatorValues = new HashMap<>();
        for (PropertyModel propertyModel : classModel.getSortedProperties()) {
            if (!propertyModel.isWritable() || params.contains(propertyModel.getReadName())) {
                continue;
            }
            ModelUnmarshaller<JsonParser> modelDeserializer = memberTypeProcessor(chain, propertyModel, hasCreator);
            processors.put(renamer.apply(propertyModel.getReadName()), modelDeserializer);
        }
        for (String s : params) {
            CreatorProfile creatorModel = creator.findParamByName(s);
            ModelUnmarshaller<JsonParser> modelDeserializer = typeProcessor(chain,
                                                                            creatorModel.getType(),
                                                                            creatorModel.getCustomization(),
                                                                            JustReturn.instance());
            String parameterName = renamer.apply(creatorModel.getName());
            processors.put(parameterName, modelDeserializer);
            if (creatorModel.getCustomization().isRequired()) {
                defaultCreatorValues.put(parameterName, new RequiredCreatorParameter(parameterName));
            } else {
                Class<?> rawParamType = ReflectionUtils.getRawType(creatorModel.getType());
                defaultCreatorValues.put(parameterName, DEFAULT_CREATOR_VALUES.getOrDefault(rawParamType, NULL_PROVIDER));
            }
        }
        ModelUnmarshaller<JsonParser> instanceCreator;
        TypeInheritanceConfiguration typeInheritanceConfiguration = classCustomization.getPolymorphismConfig();
        Set<String> ignoredProperties = collectIgnoredProperties(typeInheritanceConfiguration);
        boolean failOnUnknownProperties = jsonbContext.getConfigProperties().getConfigFailOnUnknownProperties();
        if (hasCreator) {
            instanceCreator = new JsonbCreatorInstantiator(processors, defaultCreatorValues, creator, rawType, renamer,
                                                           failOnUnknownProperties, ignoredProperties);
        } else {
            ModelUnmarshaller<JsonParser> typeWrapper = new PojoDeserializer(processors, renamer, rawType,
                                                                               failOnUnknownProperties, ignoredProperties);
            instanceCreator = new DefaultObjectInstanceCreator(typeWrapper, rawType,
                                                               classModel.getDefaultConstructor());
        }
        PositionChecker positionChecker = new PositionChecker(instanceCreator, rawType, Event.START_OBJECT);
        if (typeInheritanceConfiguration != null && !typeInheritanceConfiguration.isInherited()) {
            instanceCreator = new InheritanceInstanceCreator(rawType, this, typeInheritanceConfiguration, positionChecker);
            positionChecker = new PositionChecker(instanceCreator, rawType, Event.START_OBJECT);
        }
        ModelUnmarshaller<JsonParser> nullChecker = new NullCheckDeserializer(positionChecker, JustReturn.instance());
        models.put(cachedItem, nullChecker);
        return nullChecker;
    }

    private ModelUnmarshaller<JsonParser> createCollectionDeserializer(CachedItem cachedItem,
                                                                       Class<?> rawType,
                                                                       LinkedList<Type> chain,
                                                                       Customization propertyCustomization) {
        Type type = cachedItem.type;
        Type colType = type instanceof ParameterizedType
                ? ((ParameterizedType) type).getActualTypeArguments()[0]
                : Object.class;
        colType = ReflectionUtils.resolveType(chain, colType);
        ModelUnmarshaller<JsonParser> typeProcessor = typeProcessor(chain,
                                                                    colType,
                                                                    propertyCustomization,
                                                                    JustReturn.instance());
        CollectionDeserializer collectionDeserializer = new CollectionDeserializer(typeProcessor);
        CollectionInstanceCreator instanceDeserializer = new CollectionInstanceCreator(collectionDeserializer, type);
        PositionChecker positionChecker = new PositionChecker(instanceDeserializer, rawType, Event.START_ARRAY);
        NullCheckDeserializer nullChecker = new NullCheckDeserializer(positionChecker, JustReturn.instance());
        models.put(cachedItem, nullChecker);
        return nullChecker;
    }

    private ModelUnmarshaller<JsonParser> createMapDeserializer(CachedItem cachedItem,
                                                                Class<?> rawType,
                                                                LinkedList<Type> chain,
                                                                Customization propertyCustomization) {
        Type type = cachedItem.type;
        Type keyType = type instanceof ParameterizedType
                ? ((ParameterizedType) type).getActualTypeArguments()[0]
                : Object.class;
        Type valueType = type instanceof ParameterizedType
                ? ((ParameterizedType) type).getActualTypeArguments()[1]
                : Object.class;
        ModelUnmarshaller<JsonParser> keyProcessor = typeProcessor(chain,
                                                                   keyType,
                                                                   ClassCustomization.empty(),
                                                                   JustReturn.instance(),
                                                                   MAP_KEY_EVENTS);
        ModelUnmarshaller<JsonParser> valueProcessor = typeProcessor(chain,
                                                                     valueType,
                                                                     propertyCustomization,
                                                                     JustReturn.instance());

        MapDeserializer mapDeserializer = new MapDeserializer(keyProcessor, valueProcessor);
        MapInstanceCreator mapInstanceCreator = new MapInstanceCreator(mapDeserializer,
                                                                       jsonbContext.getConfigProperties(),
                                                                       rawType);
        PositionChecker positionChecker = new PositionChecker(mapInstanceCreator, rawType, PositionChecker.Checker.CONTAINER);
        NullCheckDeserializer nullChecker = new NullCheckDeserializer(positionChecker, JustReturn.instance());
        models.put(cachedItem, nullChecker);
        return nullChecker;
    }

    private ModelUnmarshaller<JsonParser> createArrayDeserializer(CachedItem cachedItem,
                                                                  Class<?> rawType,
                                                                  LinkedList<Type> chain,
                                                                  Customization propertyCustomization) {
        JsonbConfigProperties configProperties = jsonbContext.getConfigProperties();
        if (rawType.equals(byte[].class) && !configProperties.getBinaryDataStrategy().equals(BinaryDataStrategy.BYTE)) {
            String strategy = configProperties.getBinaryDataStrategy();
            ModelUnmarshaller<JsonParser> typeProcessor = typeProcessor(chain,
                                                                        String.class,
                                                                        propertyCustomization,
                                                                        JustReturn.instance());
            ModelUnmarshaller<JsonParser> base64Deserializer = ArrayInstanceCreator.createBase64Deserializer(strategy,
                                                                                                             typeProcessor);
            NullCheckDeserializer nullChecker = new NullCheckDeserializer(base64Deserializer, JustReturn.instance());
            models.put(cachedItem, nullChecker);
            return nullChecker;
        }
        Class<?> arrayType = rawType.getComponentType();
        ModelUnmarshaller<JsonParser> typeProcessor = typeProcessor(chain,
                                                                    arrayType,
                                                                    propertyCustomization,
                                                                    JustReturn.instance());
        return createArrayCommonDeserializer(cachedItem, rawType, arrayType, typeProcessor);
    }

    private ModelUnmarshaller<JsonParser> createGenericArray(CachedItem cachedItem,
                                                             Class<?> rawType,
                                                             LinkedList<Type> chain,
                                                             Customization propertyCustomization) {
        GenericArrayType type = (GenericArrayType) cachedItem.type;
        Class<?> component = ReflectionUtils.getRawType(type.getGenericComponentType());
        ModelUnmarshaller<JsonParser> typeProcessor = typeProcessor(chain,
                                                                    type.getGenericComponentType(),
                                                                    propertyCustomization,
                                                                    JustReturn.instance());
        return createArrayCommonDeserializer(cachedItem, rawType, component, typeProcessor);
    }

    private ModelUnmarshaller<JsonParser> createArrayCommonDeserializer(CachedItem cachedItem,
                                                                        Class<?> rawType,
                                                                        Class<?> component,
                                                                        ModelUnmarshaller<JsonParser> typeProcessor) {
        ArrayDeserializer arrayDeserializer = new ArrayDeserializer(typeProcessor);
        ArrayInstanceCreator arrayInstanceCreator = ArrayInstanceCreator.create(rawType, component, arrayDeserializer);
        PositionChecker positionChecker = new PositionChecker(arrayInstanceCreator, rawType, Event.START_ARRAY);
        NullCheckDeserializer nullChecker = new NullCheckDeserializer(positionChecker, JustReturn.instance());
        models.put(cachedItem, nullChecker);
        return nullChecker;
    }

    private OptionalDeserializer createOptionalDeserializer(LinkedList<Type> chain,
                                                            Type type,
                                                            Customization propertyCustomization,
                                                            CachedItem cachedItem) {
        Type colType = type instanceof ParameterizedType
                ? ((ParameterizedType) type).getActualTypeArguments()[0]
                : Object.class;
        ModelUnmarshaller<JsonParser> typeProcessor = typeProcessor(chain, colType, propertyCustomization, JustReturn.instance());
        OptionalDeserializer optionalDeserializer = new OptionalDeserializer(typeProcessor, JustReturn.instance());
        models.put(cachedItem, optionalDeserializer);
        return optionalDeserializer;
    }

    private Set<String> collectIgnoredProperties(TypeInheritanceConfiguration typeInheritanceConfiguration) {
        Set<String> ignoredProperties = new HashSet<>();
        if (typeInheritanceConfiguration != null) {
            TypeInheritanceConfiguration current = typeInheritanceConfiguration;
            while (current != null) {
                ignoredProperties.add(current.getFieldName());
                current = current.getParentConfig();
            }
        }
        return ignoredProperties;
    }

    private Function<String, String> propertyRenamer() {
        boolean isCaseInsensitive = jsonbContext.getConfig()
                .getProperty(PROPERTY_NAMING_STRATEGY)
                .filter(prop -> prop.equals(PropertyNamingStrategy.CASE_INSENSITIVE))
                .isPresent();

        return isCaseInsensitive
                ? String::toLowerCase
                : value -> value;
    }

    private Optional<AdapterBinding> adapterBinding(Type type, ComponentBoundCustomization classCustomization) {
        return jsonbContext.getComponentMatcher().getDeserializeAdapterBinding(type, classCustomization);
    }

    private Optional<DeserializerBinding<?>> userDeserializer(Type type, ComponentBoundCustomization classCustomization) {
        return jsonbContext.getComponentMatcher().getDeserializerBinding(type, classCustomization);
    }

    private List<String> creatorParamsList(JsonbCreatorInvoker creator) {
        return Arrays.stream(creator.getParams()).map(CreatorProfile::getName).collect(Collectors.toList());
    }

    private ModelUnmarshaller<JsonParser> memberTypeProcessor(LinkedList<Type> chain,
                                                              PropertyModel propertyModel,
                                                              boolean hasCreator) {
        ModelUnmarshaller<Object> memberDeserializer;
        Type type = propertyModel.getPropertyDeserializationType();
        memberDeserializer = new ValueSetterDeserializer(propertyModel.getSetValueHandle());
        if (hasCreator) {
            memberDeserializer = new DeferredDeserializer(memberDeserializer);
        }
        return typeProcessor(chain, type, propertyModel.getCustomization(), memberDeserializer);
    }

    private ModelUnmarshaller<JsonParser> typeProcessor(LinkedList<Type> chain,
                                                        Type type,
                                                        Customization customization,
                                                        ModelUnmarshaller<Object> memberDeserializer) {
        return typeProcessor(chain, type, customization, memberDeserializer, PositionChecker.Checker.VALUES.getEvents());
    }

    private ModelUnmarshaller<JsonParser> typeProcessor(LinkedList<Type> chain,
                                                        Type type,
                                                        Customization customization,
                                                        ModelUnmarshaller<Object> memberDeserializer,
                                                        Set<Event> events) {
        Type resolved = ReflectionUtils.resolveType(chain, type);
        Class<?> rawType = ReflectionUtils.getRawType(resolved);
        Optional<DeserializerBinding<?>> deserializerBinding = userDeserializer(resolved,
                                                                                (ComponentBoundCustomization) customization);
        if (deserializerBinding.isPresent()) {
            //TODO remove or not? fix for deserializer cycle
            //            ModelDeserializer<JsonParser> exactType = createNewChain(chain, memberDeserializer, rawType,
            //            resolved, customization);
            //            return new UserDefinedDeserializer(deserializerBinding.get().getJsonbDeserializer(),
            //                                               exactType,
            //                                               memberDeserializer,
            //                                               resolved,
            //                                               customization);
            return new UserDefinedDeserializer(deserializerBinding.get().getJsonbDeserializer(),
                                               memberDeserializer,
                                               resolved,
                                               customization);
        }
        Optional<AdapterBinding> adapterBinding = adapterBinding(resolved, (ComponentBoundCustomization) customization);
        if (adapterBinding.isPresent()) {
            AdapterBinding adapter = adapterBinding.get();
            ModelUnmarshaller<JsonParser> typeDeserializer = typeDeserializer(ReflectionUtils.getRawType(adapter.getToType()),
                                                                              customization,
                                                                              JustReturn.instance(), events);
            if (typeDeserializer == null) {
                typeDeserializer = deserializerChain(adapter.getToType());
            }
            ModelUnmarshaller<JsonParser> targetAdapterModel = typeDeserializer;

            AdapterDeserializer adapterDeserializer = new AdapterDeserializer(adapter, memberDeserializer);
            return (parser, context) -> {
                DeserializationContextManager newContext = new DeserializationContextManager(context);
                Object fromJson = targetAdapterModel.unmarshal(parser, newContext);
                return adapterDeserializer.unmarshal(fromJson, context);
            };
        }
        ModelUnmarshaller<JsonParser> typeDeserializer = typeDeserializer(rawType, customization, memberDeserializer, events);
        if (typeDeserializer == null) {
            Class<?> implClass = resolveImplClass(rawType, customization);
            return createNewChain(chain, memberDeserializer, implClass, resolved, customization);
        }
        return typeDeserializer;
    }

    private ModelUnmarshaller<JsonParser> createNewChain(LinkedList<Type> chain,
                                                         ModelUnmarshaller<Object> memberDeserializer,
                                                         Class<?> rawType,
                                                         Type type,
                                                         Customization propertyCustomization) {
        ClassModel classModel = jsonbContext.getMappingContext().getOrCreateClassModel(rawType);
        ModelUnmarshaller<JsonParser> modelDeserializer = deserializerChain(chain, type, propertyCustomization, classModel);
        return new ContextSwitcher(memberDeserializer, modelDeserializer);
    }

    private ModelUnmarshaller<JsonParser> typeDeserializer(Class<?> rawType,
                                                           Customization customization,
                                                           ModelUnmarshaller<Object> delegate) {
        return typeDeserializer(rawType, customization, delegate, PositionChecker.Checker.VALUES.getEvents());
    }

    private ModelUnmarshaller<JsonParser> typeDeserializer(Class<?> rawType,
                                                           Customization customization,
                                                           ModelUnmarshaller<Object> delegate,
                                                           Set<JsonParser.Event> events) {
        return TypeDeserializers
                .getTypeDeserializer(rawType, customization, jsonbContext.getConfigProperties(), delegate, events);
    }

    private Class<?> resolveImplClass(Class<?> rawType, Customization customization) {
        if (rawType.isInterface()) {
            Class<?> implementationClass = null;
            //annotation
            if (customization instanceof PropertyCustomization) {
                implementationClass = ((PropertyCustomization) customization).getImplementationClass();
            }
            //JsonbConfig
            if (implementationClass == null) {
                implementationClass = jsonbContext.getConfigProperties().getUserTypeMapping().get(rawType);
            }
            if (implementationClass != null) {
                if (!rawType.isAssignableFrom(implementationClass)) {
                    throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.IMPL_CLASS_INCOMPATIBLE,
                                                                 implementationClass,
                                                                 rawType));
                }
                return implementationClass;
            }
        }
        return rawType;
    }

    private CachedItem createCachedItem(Type type, Customization customization) {
        return new CachedItem(type, customization.getDeserializeNumberFormatter(), customization.getDeserializeDateFormatter());
    }

    private static final class CachedItem {

        private final Type type;
        private final JsonbNumberFormatter numberFormatter;
        private final JsonbDateFormatter dateFormatter;

        CachedItem(Type type, JsonbNumberFormatter numberFormatter, JsonbDateFormatter dateFormatter) {
            this.type = type;
            this.numberFormatter = numberFormatter;
            this.dateFormatter = dateFormatter;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CachedItem that = (CachedItem) o;
            return Objects.equals(type, that.type)
                    && Objects.equals(numberFormatter, that.numberFormatter)
                    && Objects.equals(dateFormatter, that.dateFormatter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, numberFormatter, dateFormatter);
        }

        @Override
        public String toString() {
            return "CachedItem{"
                    + "type=" + type
                    + ", numberFormatter=" + numberFormatter
                    + ", dateFormatter=" + dateFormatter
                    + '}';
        }
    }

}

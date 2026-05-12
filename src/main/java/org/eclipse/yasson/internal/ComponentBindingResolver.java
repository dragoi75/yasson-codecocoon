/*
 * Copyright (c) 2016, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;

import org.eclipse.yasson.internal.components.AbstractComponentBinding;
import org.eclipse.yasson.internal.components.AdapterBindingInfo;
import org.eclipse.yasson.internal.components.ComponentBindings;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.components.JsonbSerializerBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBindingCustomization;

/**
 * Searches for a registered components or Serializer for a given type.
 */
public class ComponentBindingResolver {

    private final JsonBindingContext bindingContext;

    /**
     * Flag for searching for generic serializers and adapters in runtime.
     */
    private volatile boolean useGenerics;

    private final ConcurrentMap<Type, ComponentBindings> userDefinedBindings;

    /**
     * Create component matcher.
     *
     * @param bindingContext mandatory
     */
    ComponentBindingResolver(JsonBindingContext bindingContext) {
        Objects.requireNonNull(bindingContext);
        this.bindingContext = bindingContext;
        userDefinedBindings = new ConcurrentHashMap<>();
        initialize();
    }

    /**
     * Called during context creation, introspecting user components provided with JsonbConfig.
     */
    void initialize() {
        final JsonbSerializer<?>[] serializerArray = (JsonbSerializer<?>[]) bindingContext.getConfig()
                .getProperty(JsonbConfig.SERIALIZERS).orElseGet(() -> new JsonbSerializer<?>[] {});
        for (JsonbSerializer<?> itemSerializer : serializerArray) {
            JsonbSerializerBinding<?> serializerBind = inspectSerializerBinding(itemSerializer.getClass(), itemSerializer);
            registerSerializer(serializerBind.getBindingType(), serializerBind);
        }
        final JsonbDeserializer<?>[] deserializerArray = (JsonbDeserializer<?>[]) bindingContext.getConfig()
                .getProperty(JsonbConfig.DESERIALIZERS).orElseGet(() -> new JsonbDeserializer<?>[] {});
        for (JsonbDeserializer<?> itemDeserializer : deserializerArray) {
            DeserializerBinding<?> deserialBind = inspectDeserializerBinding(itemDeserializer.getClass(), itemDeserializer);
            registerDeserializer(deserialBind.getBindingType(), deserialBind);
        }

        final JsonbAdapter<?, ?>[] adapterArray = (JsonbAdapter<?, ?>[]) bindingContext.getConfig().getProperty(JsonbConfig.ADAPTERS)
                .orElseGet(() -> new JsonbAdapter<?, ?>[] {});
        for (JsonbAdapter<?, ?> singleAdapter : adapterArray) {
            AdapterBindingInfo adapterBindInfo = inspectAdapterBinding(singleAdapter.getClass(), singleAdapter);
            registerAdapter(adapterBindInfo.getBindingType(), adapterBindInfo);
        }
    }

    private ComponentBindings getBindingInfo(Type targetType) {
        return userDefinedBindings
                .compute(targetType, (candidateType, bindingData) -> bindingData != null ? bindingData : new ComponentBindings(candidateType));
    }

    private void registerSerializer(Type targetType, JsonbSerializerBinding<?> itemSerializer) {
        userDefinedBindings.computeIfPresent(targetType, (type, bindingList) -> {
            if (bindingList.getSerializer() != null) {
                return bindingList;
            }
            registerGenericType(targetType);
            return new ComponentBindings(targetType, itemSerializer, bindingList.getDeserializer(), bindingList.getAdapterInfo());
        });
    }

    private void registerDeserializer(Type targetType, DeserializerBinding<?> itemDeserializer) {
        userDefinedBindings.computeIfPresent(targetType, (type, bindingList) -> {
            if (bindingList.getDeserializer() != null) {
                return bindingList;
            }
            registerGenericType(targetType);
            return new ComponentBindings(targetType, bindingList.getSerializer(), itemDeserializer, bindingList.getAdapterInfo());
        });
    }

    private void registerAdapter(Type targetType, AdapterBindingInfo singleAdapter) {
        userDefinedBindings.computeIfPresent(targetType, (type, bindingList) -> {
            if (bindingList.getAdapterInfo() != null) {
                return bindingList;
            }
            registerGenericType(targetType);
            return new ComponentBindings(targetType, bindingList.getSerializer(), bindingList.getDeserializer(), singleAdapter);
        });
    }

    /**
     * If type is not parametrized runtime component resolution doesn't has to happen.
     *
     * @param targetType component binding type
     */
    private void registerGenericType(Type targetType) {
        if (targetType instanceof ParameterizedType && !useGenerics) {
            useGenerics = true;
        }
    }

    /**
     * Lookup serializer binding for a given property runtime type.
     *
     * @param propertyType runtime type of a property
     * @param bindingCustomization       with component info
     * @return serializer optional
     */
    public Optional<JsonbSerializerBinding<?>> getSerializerBinding(Type propertyType,
                                                                    ComponentBindingCustomization bindingCustomization) {

        if (bindingCustomization == null || bindingCustomization.getSerializerBinding() == null) {
            return findComponentBinding(propertyType, ComponentBindings::getSerializer);
        }
        return Optional.of(bindingCustomization.getSerializerBinding());
    }

    /**
     * Lookup deserializer binding for a given property runtime type.
     *
     * @param propertyType runtime type of a property
     * @param bindingCustomization       customization with component info
     * @return serializer optional
     */
    public Optional<DeserializerBinding<?>> getDeserializerBinding(Type propertyType,
                                                                   ComponentBindingCustomization bindingCustomization) {
        if (bindingCustomization == null || bindingCustomization.getDeserializerBinding() == null) {
            return findComponentBinding(propertyType, ComponentBindings::getDeserializer);
        }
        return Optional.of(bindingCustomization.getDeserializerBinding());
    }

    /**
     * Get components from property model (if declared by annotation and runtime type matches),
     * or return components searched by runtime type.
     *
     * @param propertyType runtime type not null
     * @param bindingCustomization       customization with component info
     * @return components info if present
     */
    public Optional<AdapterBindingInfo> getSerializeAdapterBinding(Type propertyType,
                                                                   ComponentBindingCustomization bindingCustomization) {
        if (bindingCustomization == null || bindingCustomization.getSerializeAdapterBinding() == null) {
            return findComponentBinding(propertyType, ComponentBindings::getAdapterInfo);
        }
        return Optional.of(bindingCustomization.getSerializeAdapterBinding());
    }

    /**
     * Get components from property model (if declared by annotation and runtime type matches),
     * or return components searched by runtime type.
     *
     * @param propertyType runtime type not null
     * @param bindingCustomization       customization with component info
     * @return components info if present
     */
    public Optional<AdapterBindingInfo> getDeserializeAdapterBinding(Type propertyType,
                                                                     ComponentBindingCustomization bindingCustomization) {
        if (bindingCustomization == null || bindingCustomization.getDeserializeAdapterBinding() == null) {
            return findComponentBinding(propertyType, ComponentBindings::getAdapterInfo);
        }
        return Optional.of(bindingCustomization.getDeserializeAdapterBinding());
    }

    private <T extends AbstractComponentBinding> Optional<T> findComponentBinding(Type targetType, Function<ComponentBindings, T> bindingMapper) {
        // First check if there is an exact match
        ComponentBindings componentBindings = userDefinedBindings.get(targetType);
        if (componentBindings != null) {
            Optional<T> foundBinding = getMatchingBinding(targetType, componentBindings, bindingMapper);
            if (foundBinding.isPresent()) {
               return foundBinding;
            }
        }
        
        Optional<Class<?>> resolvedClass = ReflectionHelper.getOptionalRawType(targetType);
        if (resolvedClass.isPresent()) {
            // Check if any interfaces have a match
            for (Class<?> interfaceType : resolvedClass.get().getInterfaces()) {
                ComponentBindings interfaceBindings = userDefinedBindings.get(interfaceType);
                if (interfaceBindings != null) {
                  Optional<T> foundBinding = getMatchingBinding(interfaceType, interfaceBindings, bindingMapper);
                  if (foundBinding.isPresent()) {
                      return foundBinding;
                  }
                }
            }
            
            // check if the superclass has a match
            Class<?> parentClass = resolvedClass.get().getSuperclass();
            if (parentClass != null && parentClass != Object.class) {
                Optional<T> parentBinding = findComponentBinding(parentClass, bindingMapper);
                if (parentBinding.isPresent()) {
                    return parentBinding;
                }
            }
        }
        
        return Optional.empty();
    }
    
    private <T> Optional<T> getMatchingBinding(Type targetType, ComponentBindings componentBindings, Function<ComponentBindings, T> bindingMapper) {
        final T resultInstance = bindingMapper.apply(componentBindings);
        if (resultInstance != null && matchesType(targetType, componentBindings.getBindingType())) {
            return Optional.of(resultInstance);
        }
        return Optional.empty();
    }

    private boolean matchesType(Type targetType, Type bindingType) {
        if (bindingType.equals(targetType)) {
            return true;
        }

        if (bindingType instanceof Class && targetType instanceof Class) {
            return ((Class<?>) bindingType).isAssignableFrom((Class<?>) targetType);
        }

        //don't try to runtime generic scan if not needed
        if (!useGenerics) {
            return false;
        }

        return targetType instanceof ParameterizedType && bindingType instanceof ParameterizedType
                && ReflectionHelper.getRawType(bindingType).isAssignableFrom(ReflectionHelper.getRawType(targetType))
                && matchTypeParameters((ParameterizedType) targetType, (ParameterizedType) bindingType);
    }

    /**
     * If runtimeType to adapt is a ParametrizedType, check all type args to match against components args.
     */
    private boolean matchTypeParameters(ParameterizedType expectedType, ParameterizedType boundType) {
        final Type[] expectedTypeArgs = expectedType.getActualTypeArguments();
        final Type[] adapterTypeArgs = boundType.getActualTypeArguments();
        if (expectedTypeArgs.length != adapterTypeArgs.length) {
            return false;
        }
        for (int index = 0; index < expectedTypeArgs.length; index++) {
            Type adapterArg = adapterTypeArgs[index];
            if (!expectedTypeArgs[index].equals(adapterArg)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Introspect components generic information and put resolved types into metadata wrapper.
     *
     * @param adapterImplementation class of an components
     * @param adapterObj     components instance
     * @return introspected info with resolved typevar types.
     */
    AdapterBindingInfo inspectAdapterBinding(Class<? extends JsonbAdapter> adapterImplementation, JsonbAdapter adapterObj) {
        final ParameterizedType adapterParamType = ReflectionHelper.locateParameterizedType(adapterImplementation, JsonbAdapter.class);
        final Type[] adapterArgs = adapterParamType.getActualTypeArguments();
        Type fromType = resolveTypeArgument(adapterArgs[0], adapterImplementation);
        Type toType = resolveTypeArgument(adapterArgs[1], adapterImplementation);
        final ComponentBindings bindingsSet = getBindingInfo(fromType);
        if (bindingsSet.getAdapterInfo() != null && bindingsSet.getAdapterInfo().getAdapter().getClass()
                .equals(adapterImplementation)) {
            return bindingsSet.getAdapterInfo();
        }
        JsonbAdapter createdAdapter = adapterObj != null
                ? adapterObj
                : bindingContext.getComponentInstanceCreator().getOrCreateComponent(adapterImplementation);
        return new AdapterBindingInfo(fromType, toType, createdAdapter);
    }

    /**
     * If an instance of deserializerClass is present in context and is bound for same type, return that instance.
     * Otherwise create new instance and set it to context.
     *
     * @param deserImplClass class of deserializer
     * @param adapterObj          instance to use if not cached already
     * @return wrapper used in property models
     */
    @SuppressWarnings("unchecked")
    DeserializerBinding inspectDeserializerBinding(Class<? extends JsonbDeserializer> deserImplClass,
                                                   JsonbDeserializer adapterObj) {
        final ParameterizedType deserializerParamType = ReflectionHelper
                .locateParameterizedType(deserImplClass, JsonbDeserializer.class);
        Type deserBindingType = resolveTypeArgument(deserializerParamType.getActualTypeArguments()[0], deserImplClass);
        final ComponentBindings bindingsSet = getBindingInfo(deserBindingType);
        if (bindingsSet.getDeserializer() != null && bindingsSet.getDeserializer().getClass()
                .equals(deserImplClass)) {
            return bindingsSet.getDeserializer();
        } else {
            JsonbDeserializer itemDeserializer = adapterObj != null ? adapterObj : bindingContext.getComponentInstanceCreator()
                    .getOrCreateComponent(deserImplClass);
            return new DeserializerBinding(deserBindingType, itemDeserializer);
        }
    }

    /**
     * If an instance of serializerClass is present in context and is bound for same type, return that instance.
     * Otherwise create new instance and set it to context.
     *
     * @param serializerImplClass class of deserializer
     * @param adapterObj        instance to use if not cached
     * @return wrapper used in property models
     */
    @SuppressWarnings("unchecked")
    JsonbSerializerBinding inspectSerializerBinding(Class<? extends JsonbSerializer> serializerImplClass, JsonbSerializer adapterObj) {
        final ParameterizedType serializerParamType = ReflectionHelper
                .locateParameterizedType(serializerImplClass, JsonbSerializer.class);
        Type serializerBinding = resolveTypeArgument(serializerParamType.getActualTypeArguments()[0], serializerImplClass);
        final ComponentBindings bindingsSet = getBindingInfo(serializerBinding);
        if (bindingsSet.getSerializer() != null && bindingsSet.getSerializer().getClass().equals(serializerImplClass)) {
            return bindingsSet.getSerializer();
        } else {
            JsonbSerializer itemSerializer = adapterObj != null ? adapterObj : bindingContext.getComponentInstanceCreator()
                    .getOrCreateComponent(serializerImplClass);
            return new JsonbSerializerBinding(serializerBinding, itemSerializer);
        }

    }

    private Type resolveTypeArgument(Type adapterArg, Type converterType) {
        if (adapterArg instanceof ParameterizedType) {
            return ReflectionHelper.resolveGenericTypeArguments((ParameterizedType) adapterArg, converterType);
        } else if (adapterArg instanceof TypeVariable) {
            LinkedList<Type> typeLinkedList = new LinkedList<>();
            typeLinkedList.add(converterType);
            return ReflectionHelper.resolveItemTypeVariable(typeLinkedList, (TypeVariable<?>) adapterArg, true);
        } else {
            return adapterArg;
        }
    }

}

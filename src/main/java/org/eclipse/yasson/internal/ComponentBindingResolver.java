/*
 * Copyright (c) 2016, 2021 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;

import org.eclipse.yasson.internal.components.AdapterBindingEntry;
import org.eclipse.yasson.internal.components.BaseComponentBinding;
import org.eclipse.yasson.internal.components.ComponentBindingInfo;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.components.SerializerBindingEntry;
import org.eclipse.yasson.internal.model.customization.ComponentSerializationBindingProvider;

/**
 * Searches for a registered components or Serializer for a given type.
 */
public class ComponentBindingResolver {

    private final JsonbRuntimeContext runtimeContext;

    /**
     * Flag for searching for generic serializers and adapters in runtime.
     */
    private volatile boolean useGenerics;

    private final ConcurrentMap<Type, ComponentBindingInfo> userBindings;

    /**
     * Create component matcher.
     *
     * @param runtimeContext mandatory
     */
    ComponentBindingResolver(JsonbRuntimeContext runtimeContext) {
        Objects.requireNonNull(runtimeContext);
        this.runtimeContext = runtimeContext;
        userBindings = new ConcurrentHashMap<>();
        initialize();
    }

    /**
     * Called during context creation, introspecting user components provided with JsonbConfig.
     */
    void initialize() {
        final JsonbSerializer<?>[] serializerArray = (JsonbSerializer<?>[]) runtimeContext.getConfig()
                .getProperty(JsonbConfig.SERIALIZERS).orElseGet(() -> new JsonbSerializer<?>[] {});
        for (JsonbSerializer serInstance : serializerArray) {
            SerializerBindingEntry bindingEntry = inspectSerializerBinding(serInstance.getClass(), serInstance);
            registerSerializer(bindingEntry.getBindingType(), bindingEntry);
        }
        final JsonbDeserializer<?>[] deserializerArray = (JsonbDeserializer<?>[]) runtimeContext.getConfig()
                .getProperty(JsonbConfig.DESERIALIZERS).orElseGet(() -> new JsonbDeserializer<?>[] {});
        for (JsonbDeserializer deserInstance : deserializerArray) {
            JsonbDeserializerBinding deserBindingEntry = inspectDeserializerBinding(deserInstance.getClass(), deserInstance);
            registerDeserializer(deserBindingEntry.getBindingType(), deserBindingEntry);
        }

        final JsonbAdapter<?, ?>[] adapterArray = (JsonbAdapter<?, ?>[]) runtimeContext.getConfig().getProperty(JsonbConfig.ADAPTERS)
                .orElseGet(() -> new JsonbAdapter<?, ?>[] {});
        for (JsonbAdapter<?, ?> adapterInstance : adapterArray) {
            AdapterBindingEntry adapterEntry = inspectAdapterBinding(adapterInstance.getClass(), adapterInstance);
            registerAdapter(adapterEntry.getBindingType(), adapterEntry);
        }
    }

    private ComponentBindingInfo getBindingInfo(Type targetType) {
        return userBindings
                .compute(targetType, (candidateType, bindingDetails) -> bindingDetails != null ? bindingDetails : new ComponentBindingInfo(candidateType));
    }

    private void registerSerializer(Type targetType, SerializerBindingEntry serInstance) {
        userBindings.computeIfPresent(targetType, (type, bindingList) -> {
            if (bindingList.getSerializer() != null) {
                return bindingList;
            }
            registerGenericType(targetType);
            return new ComponentBindingInfo(targetType, serInstance, bindingList.getDeserializer(), bindingList.getAdapterInfo());
        });
    }

    private void registerDeserializer(Type targetType, JsonbDeserializerBinding deserInstance) {
        userBindings.computeIfPresent(targetType, (type, bindingList) -> {
            if (bindingList.getDeserializer() != null) {
                return bindingList;
            }
            registerGenericType(targetType);
            return new ComponentBindingInfo(targetType, bindingList.getSerializer(), deserInstance, bindingList.getAdapterInfo());
        });
    }

    private void registerAdapter(Type targetType, AdapterBindingEntry adapterInstance) {
        userBindings.computeIfPresent(targetType, (type, bindingList) -> {
            if (bindingList.getAdapterInfo() != null) {
                return bindingList;
            }
            registerGenericType(targetType);
            return new ComponentBindingInfo(targetType, bindingList.getSerializer(), bindingList.getDeserializer(), adapterInstance);
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
     * @param serializationProvider       with component info
     * @return serializer optional
     */
    public Optional<SerializerBindingEntry<?>> getSerializerBinding(Type propertyType,
                                                                    ComponentSerializationBindingProvider serializationProvider) {

        if (serializationProvider == null || serializationProvider.getSerializerBinding() == null) {
            return findComponentBinding(propertyType, ComponentBindingInfo::getSerializer);
        }
        return Optional.of(serializationProvider.getSerializerBinding());
    }

    /**
     * Lookup deserializer binding for a given property runtime type.
     *
     * @param propertyType runtime type of a property
     * @param serializationProvider       customization with component info
     * @return serializer optional
     */
    public Optional<JsonbDeserializerBinding<?>> getDeserializerBinding(Type propertyType,
                                                                        ComponentSerializationBindingProvider serializationProvider) {
        if (serializationProvider == null || serializationProvider.getDeserializerBinding() == null) {
            return findComponentBinding(propertyType, ComponentBindingInfo::getDeserializer);
        }
        return Optional.of(serializationProvider.getDeserializerBinding());
    }

    /**
     * Get components from property model (if declared by annotation and runtime type matches),
     * or return components searched by runtime type.
     *
     * @param propertyType runtime type not null
     * @param serializationProvider       customization with component info
     * @return components info if present
     */
    public Optional<AdapterBindingEntry> getSerializeAdapterBinding(Type propertyType,
                                                                    ComponentSerializationBindingProvider serializationProvider) {
        if (serializationProvider == null || serializationProvider.getSerializeAdapterBinding() == null) {
            return findComponentBinding(propertyType, ComponentBindingInfo::getAdapterInfo);
        }
        return Optional.of(serializationProvider.getSerializeAdapterBinding());
    }

    /**
     * Get components from property model (if declared by annotation and runtime type matches),
     * or return components searched by runtime type.
     *
     * @param propertyType runtime type not null
     * @param serializationProvider       customization with component info
     * @return components info if present
     */
    public Optional<AdapterBindingEntry> getDeserializeAdapterBinding(Type propertyType,
                                                                      ComponentSerializationBindingProvider serializationProvider) {
        if (serializationProvider == null || serializationProvider.getDeserializeAdapterBinding() == null) {
            return findComponentBinding(propertyType, ComponentBindingInfo::getAdapterInfo);
        }
        return Optional.of(serializationProvider.getDeserializeAdapterBinding());
    }

    private <T extends BaseComponentBinding> Optional<T> findComponentBinding(Type searchType, Function<ComponentBindingInfo, T> bindingMapper) {
        // First check if there is an exact match
        ComponentBindingInfo bindingInfo = userBindings.get(searchType);
        if (bindingInfo != null) {
            Optional<T> optionalBinding = getMatchingBinding(searchType, bindingInfo, bindingMapper);
            if (optionalBinding.isPresent()) {
               return optionalBinding;
            }
        }
        
        Optional<Class<?>> resolvedClass = ReflectiveTypeResolver.getOptionalRawType(searchType);
        if (resolvedClass.isPresent()) {
            // Check if any interfaces have a match
            for (Class<?> interfaceType : resolvedClass.get().getInterfaces()) {
                ComponentBindingInfo interfaceBinding = userBindings.get(interfaceType);
                if (interfaceBinding != null) {
                  Optional<T> optionalBinding = getMatchingBinding(interfaceType, interfaceBinding, bindingMapper);
                  if (optionalBinding.isPresent()) {
                      return optionalBinding;
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
    
    private <T> Optional<T> getMatchingBinding(Type searchType, ComponentBindingInfo bindingInfo, Function<ComponentBindingInfo, T> bindingMapper) {
        final T entity = bindingMapper.apply(bindingInfo);
        if (entity != null && matchesType(searchType, bindingInfo.getBindingType())) {
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    private boolean matchesType(Type searchType, Type declaredBindingType) {
        if (declaredBindingType.equals(searchType)) {
            return true;
        }

        if (declaredBindingType instanceof Class && searchType instanceof Class) {
            return ((Class<?>) declaredBindingType).isAssignableFrom((Class) searchType);
        }

        //don't try to runtime generic scan if not needed
        if (!useGenerics) {
            return false;
        }

        return searchType instanceof ParameterizedType && declaredBindingType instanceof ParameterizedType
                && ReflectiveTypeResolver.getRawType(declaredBindingType).isAssignableFrom(ReflectiveTypeResolver.getRawType(searchType))
                && matchTypeParams((ParameterizedType) searchType, (ParameterizedType) declaredBindingType);
    }

    /**
     * If runtimeType to adapt is a ParametrizedType, check all type args to match against components args.
     */
    private boolean matchTypeParams(ParameterizedType expectedType, ParameterizedType adapterBound) {
        final Type[] expectedTypeArguments = expectedType.getActualTypeArguments();
        final Type[] adapterTypeArguments = adapterBound.getActualTypeArguments();
        if (expectedTypeArguments.length != adapterTypeArguments.length) {
            return false;
        }
        for (int idx = 0; idx < expectedTypeArguments.length; idx++) {
            Type typeArg = adapterTypeArguments[idx];
            if (!expectedTypeArguments[idx].equals(typeArg)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Introspect components generic information and put resolved types into metadata wrapper.
     *
     * @param adapterImplClass class of an components
     * @param adapterInstance     components instance
     * @return introspected info with resolved typevar types.
     */
    AdapterBindingEntry inspectAdapterBinding(Class<? extends JsonbAdapter> adapterImplClass, JsonbAdapter adapterInstance) {
        final ParameterizedType adapterParamType = ReflectiveTypeResolver.findParameterizedInterface(adapterImplClass, JsonbAdapter.class);
        final Type[] adapterTypeArgs = adapterParamType.getActualTypeArguments();
        Type sourceType = resolveTypeArgument(adapterTypeArgs[0], adapterImplClass);
        Type targetType = resolveTypeArgument(adapterTypeArgs[1], adapterImplClass);
        final ComponentBindingInfo bindingsInfo = getBindingInfo(sourceType);
        if (bindingsInfo.getAdapterInfo() != null && bindingsInfo.getAdapterInfo().getAdapter().getClass()
                .equals(adapterImplClass)) {
            return bindingsInfo.getAdapterInfo();
        }
        JsonbAdapter createdAdapter = adapterInstance != null
                ? adapterInstance
                : runtimeContext.getComponentInstanceCreator().getOrCreateComponent(adapterImplClass);
        return new AdapterBindingEntry(sourceType, targetType, createdAdapter);
    }

    /**
     * If an instance of deserializerClass is present in context and is bound for same type, return that instance.
     * Otherwise create new instance and set it to context.
     *
     * @param deserializerImplClass class of deserializer
     * @param adapterInstance          instance to use if not cached already
     * @return wrapper used in property models
     */
    @SuppressWarnings("unchecked")
    JsonbDeserializerBinding inspectDeserializerBinding(Class<? extends JsonbDeserializer> deserializerImplClass,
                                                        JsonbDeserializer adapterInstance) {
        final ParameterizedType deserializerParamType = ReflectiveTypeResolver
                .findParameterizedInterface(deserializerImplClass, JsonbDeserializer.class);
        Type bindingTypeForDeserializer = resolveTypeArgument(deserializerParamType.getActualTypeArguments()[0], deserializerImplClass);
        final ComponentBindingInfo bindingsInfo = getBindingInfo(bindingTypeForDeserializer);
        if (bindingsInfo.getDeserializer() != null && bindingsInfo.getDeserializer().getClass()
                .equals(deserializerImplClass)) {
            return bindingsInfo.getDeserializer();
        } else {
            JsonbDeserializer deserInstance = adapterInstance != null ? adapterInstance : runtimeContext.getComponentInstanceCreator()
                    .getOrCreateComponent(deserializerImplClass);
            return new JsonbDeserializerBinding(bindingTypeForDeserializer, deserInstance);
        }
    }

    /**
     * If an instance of serializerClass is present in context and is bound for same type, return that instance.
     * Otherwise create new instance and set it to context.
     *
     * @param serializerImplClass class of deserializer
     * @param adapterInstance        instance to use if not cached
     * @return wrapper used in property models
     */
    @SuppressWarnings("unchecked")
    SerializerBindingEntry inspectSerializerBinding(Class<? extends JsonbSerializer> serializerImplClass, JsonbSerializer adapterInstance) {
        final ParameterizedType serializerParamType = ReflectiveTypeResolver
                .findParameterizedInterface(serializerImplClass, JsonbSerializer.class);
        Type serializerBoundType = resolveTypeArgument(serializerParamType.getActualTypeArguments()[0], serializerImplClass.getClass());
        final ComponentBindingInfo bindingsInfo = getBindingInfo(serializerBoundType);
        if (bindingsInfo.getSerializer() != null && bindingsInfo.getSerializer().getClass().equals(serializerImplClass)) {
            return bindingsInfo.getSerializer();
        } else {
            JsonbSerializer serInstance = adapterInstance != null ? adapterInstance : runtimeContext.getComponentInstanceCreator()
                    .getOrCreateComponent(serializerImplClass);
            return new SerializerBindingEntry(serializerBoundType, serInstance);
        }

    }

    private Type resolveTypeArgument(Type adapterArg, Type targetType) {
        if (adapterArg instanceof ParameterizedType) {
            return ReflectiveTypeResolver.resolveGenericArguments((ParameterizedType) adapterArg, targetType);
        } else if (adapterArg instanceof TypeVariable) {
            return ReflectiveTypeResolver
                    .resolveItemTypeVariable(new RuntimeTypeContainer(null, targetType), (TypeVariable<?>) adapterArg, true);
        } else {
            return adapterArg;
        }
    }

}

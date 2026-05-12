/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.components.ComponentBindingBase;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.components.JsonbSerializerBinding;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.components.ComponentBindings;
import org.eclipse.yasson.internal.model.customization.ComponentBoundCustomization;

/**
 * Searches for a registered components or Serializer for a given type.
 */
public class ComponentBindingRegistry {

    private final JsonbRuntimeContext runtimeContext;

    /**
     * Flag for searching for generic serializers and adapters in runtime.
     */
    private volatile boolean useGenerics;

    private final ConcurrentMap<Type, ComponentBindings> customComponents;

    /**
     * Create component matcher.
     *
     * @param runtimeContext mandatory
     */
    ComponentBindingRegistry(JsonbRuntimeContext runtimeContext) {
        Objects.requireNonNull(runtimeContext);
        this.runtimeContext = runtimeContext;
        customComponents = new ConcurrentHashMap<>();
        initialize();
    }

    /**
     * Called during context creation, introspecting user components provided with JsonbConfig.
     */
    void initialize() {
        final JsonbSerializer<?>[] serializerArray = (JsonbSerializer<?>[]) runtimeContext.getConfig()
                .getProperty(JsonbConfig.SERIALIZERS).orElseGet(() -> new JsonbSerializer<?>[] {});
        for (JsonbSerializer serializerInstance : serializerArray) {
            JsonbSerializerBinding serializerRegistration = inspectSerializerBinding(serializerInstance.getClass(), serializerInstance);
            registerSerializer(serializerRegistration.getBindingType(), serializerRegistration);
        }
        final JsonbDeserializer<?>[] deserializerArray = (JsonbDeserializer<?>[]) runtimeContext.getConfig()
                .getProperty(JsonbConfig.DESERIALIZERS).orElseGet(() -> new JsonbDeserializer<?>[] {});
        for (JsonbDeserializer deserializerInstance : deserializerArray) {
            JsonbDeserializerBinding deserializerRegistration = inspectDeserializerBinding(deserializerInstance.getClass(), deserializerInstance);
            registerDeserializer(deserializerRegistration.getBindingType(), deserializerRegistration);
        }

        final JsonbAdapter<?, ?>[] adapterArray = (JsonbAdapter<?, ?>[]) runtimeContext.getConfig().getProperty(JsonbConfig.ADAPTERS)
                .orElseGet(() -> new JsonbAdapter<?, ?>[] {});
        for (JsonbAdapter<?, ?> adapterInstance : adapterArray) {
            TypeAdapterBinding adapterRegistration = inspectAdapterBinding(adapterInstance.getClass(), adapterInstance);
            registerAdapter(adapterRegistration.getBindingType(), adapterRegistration);
        }
    }

    private ComponentBindings getBindingInfo(Type runtimeType) {
        return customComponents
                .compute(runtimeType, (resolvedType, bindingDetails) -> bindingDetails != null ? bindingDetails : new ComponentBindings(resolvedType));
    }

    private void registerSerializer(Type targetType, JsonbSerializerBinding serializerInstance) {
        customComponents.computeIfPresent(targetType, (type, currentBindings) -> {
            if (currentBindings.getSerializer() != null) {
                return currentBindings;
            }
            markGeneric(targetType);
            return new ComponentBindings(targetType, serializerInstance, currentBindings.getDeserializer(), currentBindings.getAdapterInfo());
        });
    }

    private void registerDeserializer(Type targetType, JsonbDeserializerBinding deserializerInstance) {
        customComponents.computeIfPresent(targetType, (type, currentBindings) -> {
            if (currentBindings.getDeserializer() != null) {
                return currentBindings;
            }
            markGeneric(targetType);
            return new ComponentBindings(targetType, currentBindings.getSerializer(), deserializerInstance, currentBindings.getAdapterInfo());
        });
    }

    private void registerAdapter(Type targetType, TypeAdapterBinding adapterInstance) {
        customComponents.computeIfPresent(targetType, (type, currentBindings) -> {
            if (currentBindings.getAdapterInfo() != null) {
                return currentBindings;
            }
            markGeneric(targetType);
            return new ComponentBindings(targetType, currentBindings.getSerializer(), currentBindings.getDeserializer(), adapterInstance);
        });
    }

    /**
     * If type is not parametrized runtime component resolution doesn't has to happen.
     *
     * @param targetType component binding type
     */
    private void markGeneric(Type targetType) {
        if (targetType instanceof ParameterizedType && !useGenerics) {
            useGenerics = true;
        }
    }

    /**
     * Lookup serializer binding for a given property runtime type.
     *
     * @param runtimePropertyType runtime type of a property
     * @param bindingCustomization       with component info
     * @return serializer optional
     */
    @SuppressWarnings("unchecked")
    public Optional<JsonbSerializerBinding<?>> getSerializerBinding(Type runtimePropertyType,
                                                                    ComponentBoundCustomization bindingCustomization) {

        if (bindingCustomization == null || bindingCustomization.getSerializerBinding() == null) {
            return findComponentBinding(runtimePropertyType, ComponentBindings::getSerializer);
        }
        return Optional.of(bindingCustomization.getSerializerBinding());
    }

    /**
     * Lookup deserializer binding for a given property runtime type.
     *
     * @param runtimePropertyType runtime type of a property
     * @param bindingCustomization       customization with component info
     * @return serializer optional
     */
    @SuppressWarnings("unchecked")
    public Optional<JsonbDeserializerBinding<?>> getDeserializerBinding(Type runtimePropertyType,
                                                                        ComponentBoundCustomization bindingCustomization) {
        if (bindingCustomization == null || bindingCustomization.getDeserializerBinding() == null) {
            return findComponentBinding(runtimePropertyType, ComponentBindings::getDeserializer);
        }
        return Optional.of(bindingCustomization.getDeserializerBinding());
    }

    /**
     * Get components from property model (if declared by annotation and runtime type matches),
     * or return components searched by runtime type.
     *
     * @param runtimePropertyType runtime type not null
     * @param bindingCustomization       customization with component info
     * @return components info if present
     */
    public Optional<TypeAdapterBinding> getSerializeAdapterBinding(Type runtimePropertyType,
                                                                   ComponentBoundCustomization bindingCustomization) {
        if (bindingCustomization == null || bindingCustomization.getSerializeAdapterBinding() == null) {
            return findComponentBinding(runtimePropertyType, ComponentBindings::getAdapterInfo);
        }
        return Optional.of(bindingCustomization.getSerializeAdapterBinding());
    }

    /**
     * Get components from property model (if declared by annotation and runtime type matches),
     * or return components searched by runtime type.
     *
     * @param runtimePropertyType runtime type not null
     * @param bindingCustomization       customization with component info
     * @return components info if present
     */
    public Optional<TypeAdapterBinding> getDeserializeAdapterBinding(Type runtimePropertyType,
                                                                     ComponentBoundCustomization bindingCustomization) {
        if (bindingCustomization == null || bindingCustomization.getDeserializeAdapterBinding() == null) {
            return findComponentBinding(runtimePropertyType, ComponentBindings::getAdapterInfo);
        }
        return Optional.of(bindingCustomization.getDeserializeAdapterBinding());
    }

    private <T extends ComponentBindingBase> Optional<T> findComponentBinding(Type lookupType, Function<ComponentBindings, T> bindingsMapper) {
        for (ComponentBindings bindingsHolder : customComponents.values()) {
            final T element = bindingsMapper.apply(bindingsHolder);
            if (element != null && isMatch(lookupType, bindingsHolder.getBindingType())) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private boolean isMatch(Type lookupType, Type bindingType) {
        if (bindingType.equals(lookupType)) {
            return true;
        }

        if (bindingType instanceof Class && lookupType instanceof Class) {
            return ((Class<?>) bindingType).isAssignableFrom((Class) lookupType);
        }

        //don't try to runtime generic scan if not needed
        if (!useGenerics) {
            return false;
        }

        return lookupType instanceof ParameterizedType && bindingType instanceof ParameterizedType
                && ReflectionTypeResolver.getRawType(bindingType).isAssignableFrom(ReflectionTypeResolver.getRawType(lookupType))
                && matchTypeParams((ParameterizedType) lookupType, (ParameterizedType) bindingType);
    }

    /**
     * If runtimeType to adapt is a ParametrizedType, check all type args to match against components args.
     */
    private boolean matchTypeParams(ParameterizedType expectedType, ParameterizedType boundType) {
        final Type[] expectedTypeArgs = expectedType.getActualTypeArguments();
        final Type[] adapterTypeArgs = boundType.getActualTypeArguments();
        if (expectedTypeArgs.length != adapterTypeArgs.length) {
            return false;
        }
        for (int idx = 0; idx < expectedTypeArgs.length; idx++) {
            Type adapterArg = adapterTypeArgs[idx];
            if (!expectedTypeArgs[idx].equals(adapterArg)) {
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
    TypeAdapterBinding inspectAdapterBinding(Class<? extends JsonbAdapter> adapterImplementation, JsonbAdapter adapterObj) {
        final ParameterizedType adapterParamType = ReflectionTypeResolver.findParameterizedInterface(adapterImplementation, JsonbAdapter.class);
        final Type[] adapterTypeArgs = adapterParamType.getActualTypeArguments();
        Type sourceType = resolveTypeArgument(adapterTypeArgs[0], adapterImplementation);
        Type targetType = resolveTypeArgument(adapterTypeArgs[1], adapterImplementation);
        final ComponentBindings bindingsHolder = getBindingInfo(sourceType);
        if (bindingsHolder.getAdapterInfo() != null && bindingsHolder.getAdapterInfo().getAdapter().getClass()
                .equals(adapterImplementation)) {
            return bindingsHolder.getAdapterInfo();
        }
        JsonbAdapter createdAdapter = adapterObj != null
                ? adapterObj
                : runtimeContext.getComponentInstanceCreator().getOrCreateComponent(adapterImplementation);
        return new TypeAdapterBinding(sourceType, targetType, createdAdapter);
    }

    /**
     * If an instance of deserializerClass is present in context and is bound for same type, return that instance.
     * Otherwise create new instance and set it to context.
     *
     * @param deserializerImplementation class of deserializer
     * @param adapterObj          instance to use if not cached already
     * @return wrapper used in property models
     */
    @SuppressWarnings("unchecked")
    JsonbDeserializerBinding inspectDeserializerBinding(Class<? extends JsonbDeserializer> deserializerImplementation,
                                                        JsonbDeserializer adapterObj) {
        final ParameterizedType deserializerParamType = ReflectionTypeResolver
                .findParameterizedInterface(deserializerImplementation, JsonbDeserializer.class);
        Type bindingType = resolveTypeArgument(deserializerParamType.getActualTypeArguments()[0], deserializerImplementation);
        final ComponentBindings bindingsHolder = getBindingInfo(bindingType);
        if (bindingsHolder.getDeserializer() != null && bindingsHolder.getDeserializer().getClass()
                .equals(deserializerImplementation)) {
            return bindingsHolder.getDeserializer();
        } else {
            JsonbDeserializer deserializerInstance = adapterObj != null ? adapterObj : runtimeContext.getComponentInstanceCreator()
                    .getOrCreateComponent(deserializerImplementation);
            return new JsonbDeserializerBinding(bindingType, deserializerInstance);
        }
    }

    /**
     * If an instance of serializerClass is present in context and is bound for same type, return that instance.
     * Otherwise create new instance and set it to context.
     *
     * @param serializerImplementation class of deserializer
     * @param adapterObj        instance to use if not cached
     * @return wrapper used in property models
     */
    @SuppressWarnings("unchecked")
    JsonbSerializerBinding inspectSerializerBinding(Class<? extends JsonbSerializer> serializerImplementation, JsonbSerializer adapterObj) {
        final ParameterizedType serializerParamType = ReflectionTypeResolver
                .findParameterizedInterface(serializerImplementation, JsonbSerializer.class);
        Type bindingType = resolveTypeArgument(serializerParamType.getActualTypeArguments()[0], serializerImplementation.getClass());
        final ComponentBindings bindingsHolder = getBindingInfo(bindingType);
        if (bindingsHolder.getSerializer() != null && bindingsHolder.getSerializer().getClass().equals(serializerImplementation)) {
            return bindingsHolder.getSerializer();
        } else {
            JsonbSerializer serializerInstance = adapterObj != null ? adapterObj : runtimeContext.getComponentInstanceCreator()
                    .getOrCreateComponent(serializerImplementation);
            return new JsonbSerializerBinding(bindingType, serializerInstance);
        }

    }

    private Type resolveTypeArgument(Type adapterArg, Type adapterTargetType) {
        if (adapterArg instanceof ParameterizedType) {
            return ReflectionTypeResolver.resolveGenericArguments((ParameterizedType) adapterArg, adapterTargetType);
        } else if (adapterArg instanceof TypeVariable) {
            return ReflectionTypeResolver
                    .resolveItemTypeVariable(new RuntimeTypeHolder(null, adapterTargetType), (TypeVariable<?>) adapterArg, true);
        } else {
            return adapterArg;
        }
    }

}

/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.components.AbstractComponentBinding;
import org.eclipse.yasson.internal.components.DeserializerBinder;
import org.eclipse.yasson.internal.components.SerializerBindingEntry;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.components.ComponentBindings;
import org.eclipse.yasson.internal.model.customization.ComponentBindingCustomization;

import javax.json.bind.JsonbConfig;
import javax.json.bind.adapter.JsonbAdapter;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.bind.serializer.JsonbSerializer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Searches for a registered components or Serializer for a given type.
 *
 * @author Roman Grigoriadi
 */
public class ComponentBindingResolver {

    private final JsonbRuntimeContext runtimeContext;

    /**
     * Flag for searching for generic serializers and adapters in runtime.
     */
    private volatile boolean genericEnabled;

    /**
     * Supplier for component binging.
     * @param <T> component binding class
     */
    private interface ComponentProvider<T extends AbstractComponentBinding> {

        T getComponent(ComponentBindings componentBindings);
    }

    private final ConcurrentMap<Type, ComponentBindings> customComponentMap;

    /**
     * Create component matcher.
     * @param runtimeContext mandatory
     */
    ComponentBindingResolver(JsonbRuntimeContext runtimeContext) {
        Objects.requireNonNull(runtimeContext);
        this.runtimeContext = runtimeContext;
        customComponentMap = new ConcurrentHashMap<>();
        initialize();
    }

    /**
     * Called during context creation, introspecting user components provided with JsonbConfig.
     */
    void initialize() {
        final JsonbSerializer<?>[] serializerArray = (JsonbSerializer<?>[]) runtimeContext.getConfig().getProperty(JsonbConfig.SERIALIZERS).orElseGet(()->new JsonbSerializer<?>[]{});
        for (JsonbSerializer serializerInstance : serializerArray) {
            SerializerBindingEntry serializerEntry = inspectSerializerBinding(serializerInstance.getClass(), serializerInstance);
            registerSerializer(serializerEntry.getBindingType(), serializerEntry);
        }
        final JsonbDeserializer<?>[] deserializerArray = (JsonbDeserializer<?>[]) runtimeContext.getConfig().getProperty(JsonbConfig.DESERIALIZERS).orElseGet(()->new JsonbDeserializer<?>[]{});
        for (JsonbDeserializer deserializerInstance : deserializerArray) {
            DeserializerBinder deserializerEntry = inspectDeserializerBinding(deserializerInstance.getClass(), deserializerInstance);
            registerDeserializer(deserializerEntry.getBindingType(), deserializerEntry);
        }

        final JsonbAdapter<?, ?>[] adapterArray = (JsonbAdapter<?, ?>[]) runtimeContext.getConfig().getProperty(JsonbConfig.ADAPTERS).orElseGet(()->new JsonbAdapter<?, ?>[]{});
        for (JsonbAdapter<?, ?> adapterInstance : adapterArray) {
            TypeAdapterBinding adapterEntry = inspectAdapterBinding(adapterInstance.getClass(), adapterInstance);
            registerAdapter(adapterEntry.getBindingType(), adapterEntry);
        }
    }

    private ComponentBindings getBindingInfo(Type runtimeType) {
        return customComponentMap.compute(runtimeType, (lambdaType, bindingDetails) -> bindingDetails != null ? bindingDetails : new ComponentBindings(lambdaType));
    }

    private void registerSerializer(Type targetType, SerializerBindingEntry serializerInstance) {
        customComponentMap.computeIfPresent(targetType, (type, bindingList) -> {
            if (bindingList.getSerializer() != null) {
                return bindingList;
            }
            registerGenericType(targetType);
            return new ComponentBindings(targetType, serializerInstance, bindingList.getDeserializer(), bindingList.getAdapterInfo());
        });
    }

    private void registerDeserializer(Type targetType, DeserializerBinder deserializerInstance) {
        customComponentMap.computeIfPresent(targetType, (type, bindingList) -> {
            if (bindingList.getDeserializer() != null) {
                return bindingList;
            }
            registerGenericType(targetType);
            return new ComponentBindings(targetType, bindingList.getSerializer(), deserializerInstance, bindingList.getAdapterInfo());
        });
    }

    private void registerAdapter(Type targetType, TypeAdapterBinding adapterInstance) {
        customComponentMap.computeIfPresent(targetType, (type, bindingList) -> {
            if (bindingList.getAdapterInfo() != null) {
                return bindingList;
            }
            registerGenericType(targetType);
            return new ComponentBindings(targetType, bindingList.getSerializer(), bindingList.getDeserializer(), adapterInstance);
        });
    }

    /**
     * If type is not parametrized runtime component resolution doesn't has to happen.
     *
     * @param targetType component binding type
     */
    private void registerGenericType(Type targetType) {
        if (targetType instanceof ParameterizedType && !genericEnabled) {
            genericEnabled = true;
        }
    }

    /**
     * Lookup serializer binding for a given property runtime type.
     * @param propRuntimeType runtime type of a property
     * @param bindingCustomization with component info
     * @return serializer optional
     */
    @SuppressWarnings("unchecked")
    public Optional<SerializerBindingEntry<?>> getSerializerBinding(Type propRuntimeType, ComponentBindingCustomization bindingCustomization) {

        if (bindingCustomization == null || bindingCustomization.getSerializerBinding() == null) {
            return findComponentBinding(propRuntimeType, ComponentBindings::getSerializer);
        }
        return getComponentBinding(propRuntimeType, bindingCustomization.getSerializerBinding());
    }

    /**
     * Lookup deserializer binding for a given property runtime type.
     * @param propRuntimeType runtime type of a property
     * @param bindingCustomization customization with component info
     * @return serializer optional
     */
    @SuppressWarnings("unchecked")
    public Optional<DeserializerBinder<?>> getDeserializerBinding(Type propRuntimeType, ComponentBindingCustomization bindingCustomization) {
        if (bindingCustomization == null || bindingCustomization.getDeserializerBinding() == null) {
            return findComponentBinding(propRuntimeType, ComponentBindings::getDeserializer);
        }
        return Optional.of(bindingCustomization.getDeserializerBinding());
    }

    /**
     * Get components from property model (if declared by annotation and runtime type matches),
     * or return components searched by runtime type
     *
     * @param propRuntimeType runtime type not null
     * @param bindingCustomization customization with component info
     * @return components info if present
     */
    public Optional<TypeAdapterBinding> getAdapterBinding(Type propRuntimeType, ComponentBindingCustomization bindingCustomization) {
        if (bindingCustomization == null || bindingCustomization.getAdapterBinding() == null) {
            return findComponentBinding(propRuntimeType, ComponentBindings::getAdapterInfo);
        }
        return getComponentBinding(propRuntimeType, bindingCustomization.getAdapterBinding());
    }

    private <T extends AbstractComponentBinding> Optional<T> getComponentBinding(Type propRuntimeType, T bindingInstance) {
        //need runtime check, ParameterizedType property may have generic components assigned which is not compatible
        //for given runtime type
        if (matchesType(propRuntimeType, bindingInstance.getBindingType())) {
            return Optional.of(bindingInstance);
        }
        return Optional.empty();
    }

    private <T extends AbstractComponentBinding> Optional<T> findComponentBinding(Type targetRuntimeType, ComponentProvider<T> componentSupplier) {
        for (ComponentBindings bindingsForComponent : customComponentMap.values()) {
            final T element = componentSupplier.getComponent(bindingsForComponent);
            if (element != null && matchesType(targetRuntimeType, bindingsForComponent.getBindingType())) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private boolean matchesType(Type targetRuntimeType, Type bindingType) {
        if (bindingType.equals(targetRuntimeType)) {
            return true;
        }

        if (bindingType instanceof Class && targetRuntimeType instanceof Class) {
            return ((Class<?>) bindingType).isAssignableFrom((Class) targetRuntimeType);
        }

        //don't try to runtime generic scan if not needed
        if (!genericEnabled) {
            return false;
        }

        return targetRuntimeType instanceof ParameterizedType && bindingType instanceof ParameterizedType &&
                ReflectionTypeResolver.getRawType(bindingType).isAssignableFrom(ReflectionTypeResolver.getRawType(targetRuntimeType)) &&
                matchGenericArguments((ParameterizedType) targetRuntimeType, (ParameterizedType) bindingType);
    }

    /**
     * If runtimeType to adapt is a ParametrizedType, check all type args to match against components args.
     */
    private boolean matchGenericArguments(ParameterizedType expectedGenericType, ParameterizedType boundParamType) {
        final Type[] expectedTypeArguments = expectedGenericType.getActualTypeArguments();
        final Type[] adapterTypeArguments = boundParamType.getActualTypeArguments();
        if (expectedTypeArguments.length != adapterTypeArguments.length) {
            return false;
        }
        for(int index = 0; index < expectedTypeArguments.length; index++) {
            Type adapterArgument = adapterTypeArguments[index];
            if (!expectedTypeArguments[index].equals(adapterArgument)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Introspect components generic information and put resolved types into metadata wrapper.
     *
     * @param adapterImplClass class of an components
     * @param adapterObj components instance
     * @return introspected info with resolved typevar types.
     */
    TypeAdapterBinding inspectAdapterBinding(Class<? extends JsonbAdapter> adapterImplClass, JsonbAdapter adapterObj) {
        final ParameterizedType adapterParameterizedType = ReflectionTypeResolver.locateParameterizedType(adapterImplClass, JsonbAdapter.class);
        final Type[] adapterTypeArgs = adapterParameterizedType.getActualTypeArguments();
        Type sourceType = resolveTypeArgument(adapterTypeArgs[0], adapterImplClass);
        Type targetType = resolveTypeArgument(adapterTypeArgs[1], adapterImplClass);
        final ComponentBindings bindingsForComponent = getBindingInfo(sourceType);
        if (bindingsForComponent.getAdapterInfo() != null && bindingsForComponent.getAdapterInfo().getAdapter().getClass().equals(adapterImplClass)) {
            return bindingsForComponent.getAdapterInfo();
        }
        JsonbAdapter createdAdapter = adapterObj != null ? adapterObj : runtimeContext.getComponentInstanceCreator().getOrCreateComponent(adapterImplClass);
        return new TypeAdapterBinding(sourceType, targetType, createdAdapter);
    }

    /**
     * If an instance of deserializerClass is present in context and is bound for same type, return that instance.
     * Otherwise create new instance and set it to context.
     *
     * @param deserializerImplClass class of deserializer
     * @param adapterObj instance to use if not cached already
     * @return wrapper used in property models
     */
    @SuppressWarnings("unchecked")
    DeserializerBinder inspectDeserializerBinding(Class<? extends JsonbDeserializer> deserializerImplClass, JsonbDeserializer adapterObj) {
        final ParameterizedType deserializerParameterizedType = ReflectionTypeResolver.locateParameterizedType(deserializerImplClass, JsonbDeserializer.class);
        Type deserializerTargetType = resolveTypeArgument(deserializerParameterizedType.getActualTypeArguments()[0], deserializerImplClass);
        final ComponentBindings bindingsForComponent = getBindingInfo(deserializerTargetType);
        if (bindingsForComponent.getDeserializer() != null && bindingsForComponent.getDeserializer().getClass().equals(deserializerImplClass)) {
            return bindingsForComponent.getDeserializer();
        } else {
            JsonbDeserializer deserializerInstance = adapterObj != null ? adapterObj : runtimeContext.getComponentInstanceCreator()
                    .getOrCreateComponent(deserializerImplClass);
            return new DeserializerBinder(deserializerTargetType, deserializerInstance);
        }
    }

    /**
     * If an instance of serializerClass is present in context and is bound for same type, return that instance.
     * Otherwise create new instance and set it to context.
     *
     * @param serializerImplClass class of deserializer
     * @param adapterObj instance to use if not cached
     * @return wrapper used in property models
     */
    @SuppressWarnings("unchecked")
    SerializerBindingEntry inspectSerializerBinding(Class<? extends JsonbSerializer> serializerImplClass, JsonbSerializer adapterObj) {
        final ParameterizedType serializerParameterizedType = ReflectionTypeResolver.locateParameterizedType(serializerImplClass, JsonbSerializer.class);
        Type serializerTargetType = resolveTypeArgument(serializerParameterizedType.getActualTypeArguments()[0], serializerImplClass.getClass());
        final ComponentBindings bindingsForComponent = getBindingInfo(serializerTargetType);
        if (bindingsForComponent.getSerializer() != null && bindingsForComponent.getSerializer().getClass().equals(serializerImplClass)) {
            return bindingsForComponent.getSerializer();
        } else {
            JsonbSerializer serializerInstance = adapterObj != null ? adapterObj : runtimeContext.getComponentInstanceCreator()
                    .getOrCreateComponent(serializerImplClass);
            return new SerializerBindingEntry(serializerTargetType, serializerInstance);
        }

    }


    private Type resolveTypeArgument(Type adapterArgumentType, Type adapterResolvedType) {
        if(adapterArgumentType instanceof ParameterizedType) {
            return ReflectionTypeResolver.resolveParameterizedArguments((ParameterizedType) adapterArgumentType, adapterResolvedType);
        } else if (adapterArgumentType instanceof TypeVariable) {
            return ReflectionTypeResolver.resolveVariableTypeForItem(new RuntimeTypeHolder(null, adapterResolvedType), (TypeVariable<?>) adapterArgumentType);
        } else {
            return adapterArgumentType;
        }
    }

}

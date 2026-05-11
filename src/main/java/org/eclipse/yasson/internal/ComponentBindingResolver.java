/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.components.AbstractComponentBinding;
import org.eclipse.yasson.internal.components.AdapterBindingDescriptor;
import org.eclipse.yasson.internal.components.ComponentBindings;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBindingCustomizer;
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
    private volatile boolean genericsEnabled;

    /**
     * Supplier for component binging.
     * @param <T> component binding class
     */
    private interface ComponentProvider<T extends AbstractComponentBinding> {

        T getComponent(ComponentBindings componentBindings);
    }

    private final ConcurrentMap<Type, ComponentBindings> userBindingsMap;

    /**
     * Create component matcher.
     * @param runtimeContext mandatory
     */
    ComponentBindingResolver(JsonbRuntimeContext runtimeContext) {
        Objects.requireNonNull(runtimeContext);
        this.runtimeContext = runtimeContext;
        userBindingsMap = new ConcurrentHashMap<>();
        initialize();
    }

    /**
     * Called during context creation, introspecting user components provided with JsonbConfig.
     */
    void initialize() {
        final JsonbSerializer<?>[] serializerArray = (JsonbSerializer<?>[]) runtimeContext.getConfig().getProperty(JsonbConfig.SERIALIZERS).orElseGet(() -> new JsonbSerializer<?>[] {});
        for (JsonbSerializer serInstance : serializerArray) {
            SerializerBinding serBinding = analyzeSerializerBinding(serInstance.getClass(), serInstance);
            registerSerializer(serBinding.getBindingType(), serBinding);
        }
        final JsonbDeserializer<?>[] deserializerArray = (JsonbDeserializer<?>[]) runtimeContext.getConfig().getProperty(JsonbConfig.DESERIALIZERS).orElseGet(() -> new JsonbDeserializer<?>[] {});
        for (JsonbDeserializer deserInstance : deserializerArray) {
            JsonbDeserializerBinding deserializerDescriptor = analyzeDeserializerBinding(deserInstance.getClass(), deserInstance);
            registerDeserializer(deserializerDescriptor.getBindingType(), deserializerDescriptor);
        }
        final JsonbAdapter<?, ?>[] adapterArray = (JsonbAdapter<?, ?>[]) runtimeContext.getConfig().getProperty(JsonbConfig.ADAPTERS).orElseGet(() -> new JsonbAdapter<?, ?>[] {});
        for (JsonbAdapter<?, ?> adapterInstance : adapterArray) {
            AdapterBindingDescriptor adapterDescriptor = analyzeAdapterBinding(adapterInstance.getClass(), adapterInstance);
            registerAdapter(adapterDescriptor.getBindingType(), adapterDescriptor);
        }
    }

    private ComponentBindings getBindingInfo(Type targetType) {
        return userBindingsMap.compute(targetType, (otherType, info) -> null != info ? info : new ComponentBindings(otherType));
    }

    private void registerSerializer(Type targetType, SerializerBinding serInstance) {
        userBindingsMap.computeIfPresent(targetType, (type, bindingList) -> {
            if (null != bindingList.getSerializer()) {
                return bindingList;
            }
            markGeneric(targetType);
            return new ComponentBindings(targetType, serInstance, bindingList.getDeserializer(), bindingList.getAdapterInfo());
        });
    }

    private void registerDeserializer(Type targetType, JsonbDeserializerBinding deserInstance) {
        userBindingsMap.computeIfPresent(targetType, (type, bindingList) -> {
            if (null != bindingList.getDeserializer()) {
                return bindingList;
            }
            markGeneric(targetType);
            return new ComponentBindings(targetType, bindingList.getSerializer(), deserInstance, bindingList.getAdapterInfo());
        });
    }

    private void registerAdapter(Type targetType, AdapterBindingDescriptor adapterInstance) {
        userBindingsMap.computeIfPresent(targetType, (type, bindingList) -> {
            if (null != bindingList.getAdapterInfo()) {
                return bindingList;
            }
            markGeneric(targetType);
            return new ComponentBindings(targetType, bindingList.getSerializer(), bindingList.getDeserializer(), adapterInstance);
        });
    }

    /**
     * If type is not parametrized runtime component resolution doesn't has to happen.
     *
     * @param targetType component binding type
     */
    private void markGeneric(Type targetType) {
        if (targetType instanceof ParameterizedType && !genericsEnabled) {
            genericsEnabled = true;
        }
    }

    /**
     * Lookup serializer binding for a given property runtime type.
     * @param propertyTypeRuntime runtime type of a property
     * @param bindingCustomizer with component info
     * @return serializer optional
     */
    @SuppressWarnings("unchecked")
    public Optional<SerializerBinding<?>> getSerializerBinding(Type propertyTypeRuntime, ComponentBindingCustomizer bindingCustomizer) {
        if (null == bindingCustomizer || null == bindingCustomizer.getSerializerBinding()) {
            return findComponentBinding(propertyTypeRuntime, ComponentBindings::getSerializer);
        }
        return Optional.of(bindingCustomizer.getSerializerBinding());
    }

    /**
     * Lookup deserializer binding for a given property runtime type.
     * @param propertyTypeRuntime runtime type of a property
     * @param bindingCustomizer customization with component info
     * @return serializer optional
     */
    @SuppressWarnings("unchecked")
    public Optional<JsonbDeserializerBinding<?>> getDeserializerBinding(Type propertyTypeRuntime, ComponentBindingCustomizer bindingCustomizer) {
        if (null == bindingCustomizer || null == bindingCustomizer.getDeserializerBinding()) {
            return findComponentBinding(propertyTypeRuntime, ComponentBindings::getDeserializer);
        }
        return Optional.of(bindingCustomizer.getDeserializerBinding());
    }

    /**
     * Get components from property model (if declared by annotation and runtime type matches),
     * or return components searched by runtime type
     *
     * @param propertyTypeRuntime runtime type not null
     * @param bindingCustomizer customization with component info
     * @return components info if present
     */
    public Optional<AdapterBindingDescriptor> getAdapterBinding(Type propertyTypeRuntime, ComponentBindingCustomizer bindingCustomizer) {
        if (null == bindingCustomizer || null == bindingCustomizer.getAdapterBinding()) {
            return findComponentBinding(propertyTypeRuntime, ComponentBindings::getAdapterInfo);
        }
        return Optional.of(bindingCustomizer.getAdapterBinding());
    }

    private <T extends AbstractComponentBinding> Optional<T> getComponentBinding(Type propertyTypeRuntime, T componentInstance) {
        //need runtime check, ParameterizedType property may have generic components assigned which is not compatible
        //for given runtime type
        if (isCompatible(propertyTypeRuntime, componentInstance.getBindingType())) {
            return Optional.of(componentInstance);
        }
        return Optional.empty();
    }

    private <T extends AbstractComponentBinding> Optional<T> findComponentBinding(Type searchRuntimeType, ComponentProvider<T> provider) {
        for (ComponentBindings bindingsCollection : userBindingsMap.values()) {
            final T element = provider.getComponent(bindingsCollection);
            if (null != element && isCompatible(searchRuntimeType, bindingsCollection.getBindingType())) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private boolean isCompatible(Type searchRuntimeType, Type bindingType) {
        if (bindingType.equals(searchRuntimeType)) {
            return true;
        }
        if (bindingType instanceof Class && searchRuntimeType instanceof Class) {
            return ((Class<?>) bindingType).isAssignableFrom((Class) searchRuntimeType);
        }
        //don't try to runtime generic scan if not needed
        if (!genericsEnabled) {
            return false;
        }
        return searchRuntimeType instanceof ParameterizedType && bindingType instanceof ParameterizedType && ReflectiveTypeUtils.getRawType(bindingType).isAssignableFrom(ReflectiveTypeUtils.getRawType(searchRuntimeType)) && matchGenericTypeArguments((ParameterizedType) searchRuntimeType, (ParameterizedType) bindingType);
    }

    /**
     * If runtimeType to adapt is a ParametrizedType, check all type args to match against components args.
     */
    private boolean matchGenericTypeArguments(ParameterizedType expectedType, ParameterizedType boundType) {
        final Type[] expectedTypeArgs = expectedType.getActualTypeArguments();
        final Type[] adapterTypeArgs = boundType.getActualTypeArguments();
        if (adapterTypeArgs.length != expectedTypeArgs.length) {
            return false;
        }
        int index = 0;
        while (expectedTypeArgs.length > index) {
            Type adapterArgType = adapterTypeArgs[index];
            if (!expectedTypeArgs[index].equals(adapterArgType)) {
                return false;
            }
            index += 1;
        }
        return true;
    }

    /**
     * Introspect components generic information and put resolved types into metadata wrapper.
     *
     * @param adapterImpl class of an components
     * @param adapterObj components instance
     * @return introspected info with resolved typevar types.
     */
    AdapterBindingDescriptor analyzeAdapterBinding(Class<? extends JsonbAdapter> adapterImpl, JsonbAdapter adapterObj) {
        final ParameterizedType adapterActualType = ReflectiveTypeUtils.findParameterizedInterface(adapterImpl, JsonbAdapter.class);
        final Type[] adapterTypeArgs = adapterActualType.getActualTypeArguments();
        Type sourceType = resolveTypeArgument(adapterTypeArgs[0], adapterImpl);
        Type targetType = resolveTypeArgument(adapterTypeArgs[1], adapterImpl);
        final ComponentBindings bindingsCollection = getBindingInfo(sourceType);
        if (null != bindingsCollection.getAdapterInfo() && bindingsCollection.getAdapterInfo().getAdapter().getClass().equals(adapterImpl)) {
            return bindingsCollection.getAdapterInfo();
        }
        JsonbAdapter createdAdapter = null != adapterObj ? adapterObj : runtimeContext.getComponentInstanceCreator().getOrCreateComponent(adapterImpl);
        return new AdapterBindingDescriptor(sourceType, targetType, createdAdapter);
    }

    /**
     * If an instance of deserializerClass is present in context and is bound for same type, return that instance.
     * Otherwise create new instance and set it to context.
     *
     * @param deserImpl class of deserializer
     * @param adapterObj instance to use if not cached already
     * @return wrapper used in property models
     */
    @SuppressWarnings("unchecked")
    JsonbDeserializerBinding analyzeDeserializerBinding(Class<? extends JsonbDeserializer> deserImpl, JsonbDeserializer adapterObj) {
        final ParameterizedType deserializerActualType = ReflectiveTypeUtils.findParameterizedInterface(deserImpl, JsonbDeserializer.class);
        Type deserBindingType = resolveTypeArgument(deserializerActualType.getActualTypeArguments()[0], deserImpl);
        final ComponentBindings bindingsCollection = getBindingInfo(deserBindingType);
        if (null == bindingsCollection.getDeserializer() || !bindingsCollection.getDeserializer().getClass().equals(deserImpl)) {
            JsonbDeserializer deserInstance = null != adapterObj ? adapterObj : runtimeContext.getComponentInstanceCreator().getOrCreateComponent(deserImpl);
            return new JsonbDeserializerBinding(deserBindingType, deserInstance);
        } else {
            return bindingsCollection.getDeserializer();
        }
    }

    /**
     * If an instance of serializerClass is present in context and is bound for same type, return that instance.
     * Otherwise create new instance and set it to context.
     *
     * @param serializerImpl class of deserializer
     * @param adapterObj instance to use if not cached
     * @return wrapper used in property models
     */
    @SuppressWarnings("unchecked")
    SerializerBinding analyzeSerializerBinding(Class<? extends JsonbSerializer> serializerImpl, JsonbSerializer adapterObj) {
        final ParameterizedType serializerActualType = ReflectiveTypeUtils.findParameterizedInterface(serializerImpl, JsonbSerializer.class);
        Type serializerBinding = resolveTypeArgument(serializerActualType.getActualTypeArguments()[0], serializerImpl.getClass());
        final ComponentBindings bindingsCollection = getBindingInfo(serializerBinding);
        if (null == bindingsCollection.getSerializer() || !bindingsCollection.getSerializer().getClass().equals(serializerImpl)) {
            JsonbSerializer serInstance = null != adapterObj ? adapterObj : runtimeContext.getComponentInstanceCreator().getOrCreateComponent(serializerImpl);
            return new SerializerBinding(serializerBinding, serInstance);
        } else {
            return bindingsCollection.getSerializer();
        }
    }

    private Type resolveTypeArgument(Type adapterArg, Type adapterGeneric) {
        if (!(adapterArg instanceof ParameterizedType)) {
            if (!(adapterArg instanceof TypeVariable)) {
                return adapterArg;
            } else {
                return ReflectiveTypeUtils.resolveItemTypeVariable(new RuntimeTypeHolder(null, adapterGeneric), (TypeVariable<?>) adapterArg);
            }
        } else {
            return ReflectiveTypeUtils.resolveTypeParameters((ParameterizedType) adapterArg, adapterGeneric);
        }
    }
}

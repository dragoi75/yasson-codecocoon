/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2020 Payara Foundation and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.ComponentMatcher;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.ReflectionHelper;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBoundCustomization;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.model.customization.PropertyCustomization;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

/**
 * Builder for currently processed items by unmarshaller.
 */
public class JsonDeserializerBuilder extends GenericSerializerBuilder<JsonDeserializerBuilder> {

    /**
     * Value type of JSON event.
     */
    private JsonParser.Event parsedEvent;

    /**
     * Creates a new builder.
     *
     * @param runtimeContext Context.
     */
    public JsonDeserializerBuilder(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
    }

    /**
     * Sets value type.
     *
     * @param valueEvent last json event for constructed deserializer.
     * @return Updated object.
     */
    public JsonDeserializerBuilder setJsonValueType(JsonParser.Event valueEvent) {
        this.parsedEvent = valueEvent;
        return this;
    }

    /**
     * Build an fully initialized item.
     *
     * @return built item
     */
    public JsonbDeserializer<?> buildDeserializer() {
        setRuntimeType(determineRuntimeType());
        Class<?> targetType = ReflectionHelper.getRawType(getRuntimeType());
        Optional<AdapterBinding> adapterBindingOpt = Optional.empty();
        Customization customConfig = getCustomization();
        if (null == customConfig || customConfig instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization componentCustomization = (ComponentBoundCustomization) customConfig;
            //First check if user deserializer is registered for such type
            final ComponentMatcher componentFilter = getJsonbContext().getComponentMatcher();
            Optional<DeserializerBinding<?>> customDeserializerOpt = componentFilter.getDeserializerBinding(getRuntimeType(), componentCustomization);
            if (customDeserializerOpt.isPresent()) {
                return new UserDeserializerDeserializer<>(this, customDeserializerOpt.get());
            }
            //Second user components is registered.
            Optional<AdapterBinding> adapterOptional = componentFilter.getDeserializeAdapterBinding(getRuntimeType(), componentCustomization);
            if (adapterOptional.isPresent()) {
                adapterBindingOpt = adapterOptional;
                setRuntimeType(adapterBindingOpt.get().getToType());
                setWrapper(new AdaptedObjectDeserializer<>(adapterBindingOpt.get(), (ContainerDeserializerBase<?>) getWrapper()));
                targetType = ReflectionHelper.getRawType(getRuntimeType());
            }
        }
        if (targetType == Optional.class) {
            return new OptionalObjectDeserializer(this);
        }
        //In case of Base64 json value would be string and recognition by JsonValueType would not work
        if (isByteArray(targetType)) {
            String strategyName = getJsonbContext().getConfigProperties().getBinaryDataStrategy();
            switch(strategyName) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArrayDeserializer(this);
                default:
                    return new ByteArrayBase64Deserializer(customConfig);
            }
        }
        if (isCharArray(targetType)) {
            return new CharArrayDeserializer(this);
        }
        //Third deserializer is a supported value type to deserialize to JSON_VALUE
        if (isJsonValueEvent(parsedEvent)) {
            final Optional<AbstractValueTypeDeserializer<?>> supportedDeserializerOpt = getSupportedTypeDeserializer(targetType);
            if (!supportedDeserializerOpt.isPresent()) {
                if (JsonParser.Event.VALUE_NULL == parsedEvent) {
                    return NullDeserializer.INSTANCE;
                }
                throw new JsonbException(Messages.getMessage(MessageKeys.DESERIALIZE_VALUE_ERROR, getRuntimeType()));
            }
            return wrapWithAdapter(adapterBindingOpt, supportedDeserializerOpt.get());
        }
        JsonbDeserializer<?> resultDeserializer;
        if (JsonParser.Event.START_ARRAY != parsedEvent) {
            if (JsonParser.Event.START_OBJECT == parsedEvent) {
                if (!JsonValue.class.isAssignableFrom(targetType)) {
                    if (!Map.class.isAssignableFrom(targetType)) {
                        if (!targetType.isInterface()) {
                            if (adapterBindingOpt.isPresent()) {
                                setRuntimeType(adapterBindingOpt.get().getToType());
                                targetType = ReflectionHelper.getRawType(getRuntimeType());
                            }
                            setClassModel(getClassModel(targetType));
                            resultDeserializer = new ObjectDeserializer<>(this);
                            return wrapWithAdapter(adapterBindingOpt, resultDeserializer);
                        } else {
                            Class<?> targetMappedType = getInterfaceMappedType(targetType);
                            if (null == targetMappedType) {
                                throw new JsonbException(Messages.getMessage(MessageKeys.INFER_TYPE_FOR_UNMARSHALL, targetType.getName()));
                            }
                            setRuntimeType(targetMappedType);
                            setClassModel(getClassModel(targetMappedType));
                            return new ObjectDeserializer<>(this);
                        }
                    } else {
                        final JsonbDeserializer<?> mapEntriesDeserializer = new MapInstanceDeserializer<>(this);
                        return wrapWithAdapter(adapterBindingOpt, mapEntriesDeserializer);
                    }
                } else {
                    return wrapWithAdapter(adapterBindingOpt, new JsonObjectDeserializer(this));
                }
            }
        } else {
            if (!JsonValue.class.isAssignableFrom(targetType)) {
                if (!Map.class.isAssignableFrom(targetType)) {
                    if (!targetType.isArray() && !(getRuntimeType() instanceof GenericArrayType)) {
                        if (!Collection.class.isAssignableFrom(targetType)) {
                            throw new JsonbException("Can't deserialize JSON array into: " + getRuntimeType());
                        } else {
                            resultDeserializer = new CollectionDeserializer<>(this);
                            return wrapWithAdapter(adapterBindingOpt, resultDeserializer);
                        }
                    } else {
                        resultDeserializer = createArrayElementDeserializer(targetType.getComponentType());
                        return wrapWithAdapter(adapterBindingOpt, resultDeserializer);
                    }
                } else {
                    final JsonbDeserializer<?> mapEntriesDeserializer = new MapEntriesArrayDeserializer<>(this);
                    return wrapWithAdapter(adapterBindingOpt, mapEntriesDeserializer);
                }
            } else {
                return wrapWithAdapter(adapterBindingOpt, new JsonArrayDeserializer(this));
            }
        }
        throw new JsonbException("unresolved type for deserialization: " + getRuntimeType());
    }

    /**
     * Checks if event is a value event.
     *
     * @param valueEvent JSON event to check.
     * @return True if one of value events.
     */
    public static boolean isJsonValueEvent(JsonParser.Event valueEvent) {
        switch(valueEvent) {
            case VALUE_NULL:
            case VALUE_FALSE:
            case VALUE_TRUE:
            case VALUE_NUMBER:
            case VALUE_STRING:
                return true;
            default:
                return false;
        }
    }

    private Optional<AbstractValueTypeDeserializer<?>> getSupportedTypeDeserializer(Class<?> targetType) {
        final Optional<? extends SerializerProviderWrapper> supportedSerializerOpt = DefaultSerializers.getInstance().findValueSerializerProvider(targetType);
        if (supportedSerializerOpt.isPresent()) {
            return Optional.of(supportedSerializerOpt.get().getDeserializerProvider().provideDeserializer(getCustomization()));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private JsonbDeserializer<?> wrapWithAdapter(Optional<AdapterBinding> adapterBindingOpt, JsonbDeserializer<?> deserializerItem) {
        final Optional<JsonbDeserializer<?>> adaptedDeserializerOpt = adapterBindingOpt.map(adapterInfo -> {
            setAdaptedItemCaptor((AdaptedObjectDeserializer) getWrapper(), deserializerItem);
            return (JsonbDeserializer<?>) getWrapper();
        });
        return adaptedDeserializerOpt.orElse(deserializerItem);
    }

    private <T, A> void setAdaptedItemCaptor(AdaptedObjectDeserializer<T, A> decorator, JsonbDeserializer<T> adaptedDeserializer) {
        decorator.setAdaptedTypeDeserializer(adaptedDeserializer);
    }

    private Type determineRuntimeType() {
        Type resolvedType = ReflectionHelper.resolveActualType(getWrapper(), null != getGenericType() ? getGenericType() : getRuntimeType());
        //Try to infer best from JSON event.
        if (Object.class == resolvedType) {
            switch(parsedEvent) {
                case VALUE_FALSE:
                case VALUE_TRUE:
                    return Boolean.class;
                case VALUE_NUMBER:
                    return BigDecimal.class;
                case VALUE_STRING:
                    return String.class;
                case START_ARRAY:
                    return ArrayList.class;
                case START_OBJECT:
                    return getJsonbContext().getConfigProperties().getDefaultMapImplType();
                case VALUE_NULL:
                    return Object.class;
                default:
                    throw new IllegalStateException("Can't infer deserialization type type: " + parsedEvent);
            }
        }
        return resolvedType;
    }

    private Class<?> getInterfaceMappedType(Class<?> ifaceType) {
        if (ifaceType.isInterface()) {
            Class<?> implClass = null;
            //annotation
            if (getCustomization() instanceof PropertyCustomization) {
                implClass = ((PropertyCustomization) getCustomization()).getImplementationClass();
            }
            //JsonbConfig
            if (null == implClass) {
                implClass = getJsonbContext().getConfigProperties().getUserTypeMapping().get(ifaceType);
            }
            if (null != implClass) {
                if (!ifaceType.isAssignableFrom(implClass)) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.IMPL_CLASS_INCOMPATIBLE, implClass, ifaceType));
                }
                return implClass;
            }
        }
        return null;
    }

    /**
     * Instance is not created in case of array items, because, we don't know how long it should be
     * till parser ends parsing.
     */
    private JsonbDeserializer<?> createArrayElementDeserializer(Class<?> elementType) {
        if (byte.class != elementType) {
            if (short.class != elementType) {
                if (int.class != elementType) {
                    if (long.class != elementType) {
                        if (float.class != elementType) {
                            if (double.class != elementType) {
                                if (boolean.class != elementType) {
                                    return new ObjectArrayDeserializer(this);
                                } else {
                                    return new BooleanArrayDeserializer(this);
                                }
                            } else {
                                return new DoubleArrayDeserializer(this);
                            }
                        } else {
                            return new FloatArrayDeserializer(this);
                        }
                    } else {
                        return new LongArrayDeserializer(this);
                    }
                } else {
                    return new IntArrayDeserializer(this);
                }
            } else {
                return new ShortArrayDeserializer(this);
            }
        } else {
            return new ByteArrayDeserializer(this);
        }
    }

    private boolean isByteArray(Class<?> targetType) {
        return targetType.isArray() && Byte.TYPE == targetType.getComponentType();
    }

    private boolean isCharArray(Class<?> targetType) {
        return targetType.isArray() && Character.TYPE == targetType.getComponentType();
    }
}

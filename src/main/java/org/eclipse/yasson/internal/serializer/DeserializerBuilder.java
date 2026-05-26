/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved.
 *  Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 *  Sebastien Rius
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.serializer;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.json.JsonValue;
import javax.json.bind.JsonbException;
import javax.json.bind.config.BinaryDataStrategy;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import org.eclipse.yasson.internal.ComponentMatcher;
import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.ReflectionUtils;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBoundCustomization;
import org.eclipse.yasson.internal.model.customization.PropertyCustomization;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

/**
 * Builder for currently processed items by unmarshaller.
 *
 * @author Roman Grigoriadi
 * @author Sebastien Rius
 */
public class DeserializerBuilder extends AbstractSerializerBuilder<DeserializerBuilder> {

    /**
     * Value type of JSON event.
     */
    private JsonParser.Event jsonEvent;

    private <T, A> void setAdaptedItemCaptor(AdaptedObjectDeserializer<T, A> decoratorItem, JsonbDeserializer<T> adaptedItem) {
        decoratorItem.setAdaptedTypeDeserializer(adaptedItem);
    }

    private Optional<AbstractValueTypeDeserializer<?>> getSupportedTypeDeserializer(Class<?> rawType) {
        final Optional<? extends SerializerProviderWrapper> supportedTypeDeserializerOptional = DefaultSerializers.getInstance().findValueSerializerProvider(rawType);
        if (supportedTypeDeserializerOptional.isPresent()) {
            return Optional.of(supportedTypeDeserializerOptional.get().getDeserializerProvider().provideDeserializer(customization));
        }
        return Optional.empty();
    }

    private Class<?> getInterfaceMappedType(Class<?> interfaceType) {
        if (interfaceType.isInterface()) {
            Class implementationClass = null;
            //annotation
            if (customization instanceof PropertyCustomization) {
                implementationClass = ((PropertyCustomization) customization).getImplementationClass();
            }
            //JsonbConfig
            if (null == implementationClass) {
                implementationClass = jsonbContext.getConfigProperties().getUserTypeMapping().get(interfaceType);
            }
            if (null != implementationClass) {
                if (!interfaceType.isAssignableFrom(implementationClass)) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.IMPL_CLASS_INCOMPATIBLE, implementationClass, interfaceType));
                }
                return implementationClass;
            }
        }
        return null;
    }

    private boolean isCharArray(Class<?> rawType) {
        return rawType.isArray() && Character.TYPE == rawType.getComponentType();
    }

    private boolean isByteArray(Class<?> rawType) {
        return rawType.isArray() && Byte.TYPE == rawType.getComponentType();
    }

    private Type resolveRuntimeType() {
        Type result = ReflectionUtils.resolveType(wrapper, null != genericType ? genericType : runtimeType);
        //Try to infer best from JSON event.
        if (Object.class == result) {
            switch(jsonEvent) {
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
                    return jsonbContext.getConfigProperties().getDefaultMapImplType();
                case VALUE_NULL:
                    return Object.class;
                default:
                    throw new IllegalStateException("Can't infer deserialization type type: " + jsonEvent);
            }
        }
        return result;
    }

    /**
     * Instance is not created in case of array items, because, we don't know how long it should be
     * till parser ends parsing.
     */
    private JsonbDeserializer<?> createArrayItem(Class<?> componentType) {
        if (byte.class != componentType) {
            if (short.class != componentType) {
                if (int.class != componentType) {
                    if (long.class != componentType) {
                        if (float.class != componentType) {
                            if (double.class != componentType) {
                                return new ObjectArrayDeserializer(this);
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

    /**
     * Checks if event is a value event.
     *
     * @param event JSON event to check.
     * @return True if one of value events.
     */
    public static boolean isJsonValueEvent(JsonParser.Event event) {
        switch(event) {
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

    /**
     * Build an fully initialized item.
     *
     * @return built item
     */
    @SuppressWarnings("unchecked")
    public JsonbDeserializer<?> build() {
        runtimeType = resolveRuntimeType();
        Class<?> rawType = ReflectionUtils.getRawType(getRuntimeType());
        Optional<AdapterBinding> adapterInfoOptional = Optional.empty();
        if (null == customization || customization instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization componentBoundCustomization = (ComponentBoundCustomization) customization;
            //First check if user deserializer is registered for such type
            final ComponentMatcher componentMatcher = jsonbContext.getComponentMatcher();
            Optional<DeserializerBinding<?>> userDeserializer = componentMatcher.getDeserializerBinding(getRuntimeType(), componentBoundCustomization);
            if (userDeserializer.isPresent()) {
                return new UserDeserializerDeserializer<>(this, userDeserializer.get());
            }
            //Second user components is registered.
            Optional<AdapterBinding> adapterBinding = componentMatcher.getAdapterBinding(getRuntimeType(), componentBoundCustomization);
            if (adapterBinding.isPresent()) {
                adapterInfoOptional = adapterBinding;
                runtimeType = adapterInfoOptional.get().getToType();
                wrapper = new AdaptedObjectDeserializer<>(adapterInfoOptional.get(), (AbstractContainerDeserializer<?>) wrapper);
                rawType = ReflectionUtils.getRawType(getRuntimeType());
            }
        }
        if (rawType == Optional.class) {
            return new OptionalObjectDeserializer(this);
        }
        //In case of Base64 json value would be string and recognition by JsonValueType would not work
        if (isByteArray(rawType)) {
            String strategy = jsonbContext.getConfigProperties().getBinaryDataStrategy();
            switch(strategy) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArrayDeserializer(this);
                default:
                    return new ByteArrayBase64Deserializer(customization);
            }
        }
        if (isCharArray(rawType)) {
            return new CharArrayDeserializer(this);
        }
        //Third deserializer is a supported value type to deserialize to JSON_VALUE
        if (isJsonValueEvent(jsonEvent)) {
            final Optional<AbstractValueTypeDeserializer<?>> supportedTypeDeserializer = getSupportedTypeDeserializer(rawType);
            if (!supportedTypeDeserializer.isPresent()) {
                if (JsonParser.Event.VALUE_NULL == jsonEvent) {
                    return NullDeserializer.INSTANCE;
                }
                throw new JsonbException(Messages.getMessage(MessageKeys.DESERIALIZE_VALUE_ERROR, getRuntimeType()));
            }
            return wrapAdapted(adapterInfoOptional, supportedTypeDeserializer.get());
        }
        JsonbDeserializer<?> deserializer;
        if (JsonParser.Event.START_ARRAY != jsonEvent) {
            if (JsonParser.Event.START_OBJECT == jsonEvent) {
                if (!JsonValue.class.isAssignableFrom(rawType)) {
                    if (!Map.class.isAssignableFrom(rawType)) {
                        if (!rawType.isInterface()) {
                            if (adapterInfoOptional.isPresent()) {
                                runtimeType = adapterInfoOptional.get().getToType();
                                rawType = ReflectionUtils.getRawType(getRuntimeType());
                            }
                            classModel = getClassModel(rawType);
                            deserializer = new ObjectDeserializer<>(this);
                            return wrapAdapted(adapterInfoOptional, deserializer);
                        } else {
                            Class<?> mappedType = getInterfaceMappedType(rawType);
                            if (null == mappedType) {
                                throw new JsonbException(Messages.getMessage(MessageKeys.INFER_TYPE_FOR_UNMARSHALL, rawType.getName()));
                            }
                            runtimeType = mappedType;
                            classModel = getClassModel(mappedType);
                            return new ObjectDeserializer<>(this);
                        }
                    } else {
                        final JsonbDeserializer<?> mapDeserializer = new MapDeserializer(this);
                        return wrapAdapted(adapterInfoOptional, mapDeserializer);
                    }
                } else {
                    return wrapAdapted(adapterInfoOptional, new JsonObjectDeserializer(this));
                }
            }
        } else {
            if (!JsonValue.class.isAssignableFrom(rawType)) {
                if (!rawType.isArray() && !(getRuntimeType() instanceof GenericArrayType)) {
                    if (!Collection.class.isAssignableFrom(rawType)) {
                        throw new JsonbException("Can't deserialize JSON array into: " + getRuntimeType());
                    } else {
                        deserializer = new CollectionDeserializer<>(this);
                        return wrapAdapted(adapterInfoOptional, deserializer);
                    }
                } else {
                    deserializer = createArrayItem(rawType.getComponentType());
                    return wrapAdapted(adapterInfoOptional, deserializer);
                }
            } else {
                return wrapAdapted(adapterInfoOptional, new JsonArrayDeserializer(this));
            }
        }
        throw new JsonbException("unresolved type for deserialization: " + getRuntimeType());
    }

    /**
     * Creates a new builder.
     *
     * @param jsonbContext Context.
     */
    public DeserializerBuilder(JsonbContext jsonbContext) {
        super(jsonbContext);
    }

    /**
     * Sets value type.
     *
     * @param event last json event for constructed deserializer.
     * @return Updated object.
     */
    public DeserializerBuilder withJsonValueType(JsonParser.Event event) {
        this.jsonEvent = event;
        return this;
    }

    @SuppressWarnings("unchecked")
    private JsonbDeserializer<?> wrapAdapted(Optional<AdapterBinding> adapterInfoOptional, JsonbDeserializer<?> item) {
        final Optional<JsonbDeserializer<?>> adaptedDeserializerOptional = adapterInfoOptional.map(adapterInfo -> {
            setAdaptedItemCaptor((AdaptedObjectDeserializer) wrapper, item);
            return (JsonbDeserializer<?>) wrapper;
        });
        return adaptedDeserializerOptional.orElse(item);
    }

}

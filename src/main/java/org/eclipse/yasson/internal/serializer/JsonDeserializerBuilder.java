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
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.ReflectionTypeUtils;
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
public class JsonDeserializerBuilder extends BaseSerializerBuilder<JsonDeserializerBuilder> {

    /**
     * Value type of JSON event.
     */
    private JsonParser.Event parsedEvent;

    @SuppressWarnings("unchecked")
    private JsonbDeserializer<?> wrapWithAdapter(Optional<AdapterBinding> adapterBindingOpt, JsonbDeserializer<?> deserializerItem) {
        final Optional<JsonbDeserializer<?>> adaptedDeserializerOpt = adapterBindingOpt.map(adapterInfo -> {
            setAdaptedItemCaptor((AdaptedObjectDeserializer) wrapper, deserializerItem);
            return (JsonbDeserializer<?>) wrapper;
        });
        return adaptedDeserializerOpt.orElse(deserializerItem);
    }

    private boolean isByteArray(Class<?> targetType) {
        return targetType.isArray() && Byte.TYPE == targetType.getComponentType();
    }

    private Class<?> getInterfaceMappedType(Class<?> ifaceType) {
        if (ifaceType.isInterface()) {
            Class implClass = null;
            //annotation
            if (customization instanceof PropertyCustomization) {
                implClass = ((PropertyCustomization) customization).getImplementationClass();
            }
            //JsonbConfig
            if (null == implClass) {
                implClass = jsonbContext.getConfigProperties().getUserTypeMapping().get(ifaceType);
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
     * Checks if event is a value event.
     *
     * @param incomingEvent JSON event to check.
     * @return True if one of value events.
     */
    public static boolean isJsonValueEvent(JsonParser.Event incomingEvent) {
        switch(incomingEvent) {
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

    private boolean isCharArray(Class<?> targetType) {
        return targetType.isArray() && Character.TYPE == targetType.getComponentType();
    }

    /**
     * Instance is not created in case of array items, because, we don't know how long it should be
     * till parser ends parsing.
     */
    private JsonbDeserializer<?> createArrayItemDeserializer(Class<?> elementType) {
        if (byte.class != elementType) {
            if (short.class != elementType) {
                if (int.class != elementType) {
                    if (long.class != elementType) {
                        if (float.class != elementType) {
                            if (double.class != elementType) {
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
     * Creates a new builder.
     *
     * @param runtimeContext Context.
     */
    public JsonDeserializerBuilder(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
    }

    /**
     * Build an fully initialized item.
     *
     * @return built item
     */
    @SuppressWarnings("unchecked")
    public JsonbDeserializer<?> buildDeserializer() {
        runtimeType = determineRuntimeType();
        Class<?> targetType = ReflectionTypeUtils.getRawType(getRuntimeType());
        Optional<AdapterBinding> adapterBindingOpt = Optional.empty();
        if (null == customization || customization instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization componentCustomization = (ComponentBoundCustomization) customization;
            //First check if user deserializer is registered for such type
            final ComponentMatcher componentSelector = jsonbContext.getComponentMatcher();
            Optional<DeserializerBinding<?>> customDeserializerOpt = componentSelector.getDeserializerBinding(getRuntimeType(), componentCustomization);
            if (customDeserializerOpt.isPresent()) {
                return new UserDeserializerDeserializer<>(this, customDeserializerOpt.get());
            }
            //Second user components is registered.
            Optional<AdapterBinding> optionalAdapterBinding = componentSelector.getAdapterBinding(getRuntimeType(), componentCustomization);
            if (optionalAdapterBinding.isPresent()) {
                adapterBindingOpt = optionalAdapterBinding;
                runtimeType = adapterBindingOpt.get().getToType();
                wrapper = new AdaptedObjectDeserializer<>(adapterBindingOpt.get(), (BaseContainerDeserializer<?>) wrapper);
                targetType = ReflectionTypeUtils.getRawType(getRuntimeType());
            }
        }
        if (targetType == Optional.class) {
            return new OptionalObjectDeserializer(this);
        }
        //In case of Base64 json value would be string and recognition by JsonValueType would not work
        if (isByteArray(targetType)) {
            String mappingStrategy = jsonbContext.getConfigProperties().getBinaryDataStrategy();
            switch(mappingStrategy) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArrayDeserializer(this);
                default:
                    return new ByteArrayBase64Deserializer(customization);
            }
        }
        if (isCharArray(targetType)) {
            return new CharArrayDeserializer(this);
        }
        //Third deserializer is a supported value type to deserialize to JSON_VALUE
        if (isJsonValueEvent(parsedEvent)) {
            final Optional<AbstractValueTypeDeserializer<?>> valueTypeDeserializerOpt = getSupportedTypeDeserializer(targetType);
            if (!valueTypeDeserializerOpt.isPresent()) {
                if (JsonParser.Event.VALUE_NULL == parsedEvent) {
                    return NullDeserializer.INSTANCE;
                }
                throw new JsonbException(Messages.getMessage(MessageKeys.DESERIALIZE_VALUE_ERROR, getRuntimeType()));
            }
            return wrapWithAdapter(adapterBindingOpt, valueTypeDeserializerOpt.get());
        }
        JsonbDeserializer<?> resolvedDeserializer;
        if (JsonParser.Event.START_ARRAY != parsedEvent) {
            if (JsonParser.Event.START_OBJECT == parsedEvent) {
                if (!JsonValue.class.isAssignableFrom(targetType)) {
                    if (!Map.class.isAssignableFrom(targetType)) {
                        if (!targetType.isInterface()) {
                            if (adapterBindingOpt.isPresent()) {
                                runtimeType = adapterBindingOpt.get().getToType();
                                targetType = ReflectionTypeUtils.getRawType(getRuntimeType());
                            }
                            classModel = getClassModel(targetType);
                            resolvedDeserializer = new ObjectDeserializer<>(this);
                            return wrapWithAdapter(adapterBindingOpt, resolvedDeserializer);
                        } else {
                            Class<?> mappedTargetType = getInterfaceMappedType(targetType);
                            if (null == mappedTargetType) {
                                throw new JsonbException(Messages.getMessage(MessageKeys.INFER_TYPE_FOR_UNMARSHALL, targetType.getName()));
                            }
                            runtimeType = mappedTargetType;
                            classModel = getClassModel(mappedTargetType);
                            return new ObjectDeserializer<>(this);
                        }
                    } else {
                        final JsonbDeserializer<?> mapTypeDeserializer = new MapDeserializer(this);
                        return wrapWithAdapter(adapterBindingOpt, mapTypeDeserializer);
                    }
                } else {
                    return wrapWithAdapter(adapterBindingOpt, new JsonObjectDeserializer(this));
                }
            }
        } else {
            if (!JsonValue.class.isAssignableFrom(targetType)) {
                if (!targetType.isArray() && !(getRuntimeType() instanceof GenericArrayType)) {
                    if (!Collection.class.isAssignableFrom(targetType)) {
                        throw new JsonbException("Can't deserialize JSON array into: " + getRuntimeType());
                    } else {
                        resolvedDeserializer = new CollectionInstanceDeserializer<>(this);
                        return wrapWithAdapter(adapterBindingOpt, resolvedDeserializer);
                    }
                } else {
                    resolvedDeserializer = createArrayItemDeserializer(targetType.getComponentType());
                    return wrapWithAdapter(adapterBindingOpt, resolvedDeserializer);
                }
            } else {
                return wrapWithAdapter(adapterBindingOpt, new JsonArrayDeserializer(this));
            }
        }
        throw new JsonbException("unresolved type for deserialization: " + getRuntimeType());
    }

    /**
     * Sets value type.
     *
     * @param incomingEvent last json event for constructed deserializer.
     * @return Updated object.
     */
    public JsonDeserializerBuilder setJsonValueType(JsonParser.Event incomingEvent) {
        this.parsedEvent = incomingEvent;
        return this;
    }

    private <T, A> void setAdaptedItemCaptor(AdaptedObjectDeserializer<T, A> decorator, JsonbDeserializer<T> adaptedDeserializer) {
        decorator.setAdaptedTypeDeserializer(adaptedDeserializer);
    }

    private Optional<AbstractValueTypeDeserializer<?>> getSupportedTypeDeserializer(Class<?> targetType) {
        final Optional<? extends SerializerProviderWrapper> supportedDeserializerProviderOpt = DefaultSerializers.getInstance().findValueSerializerProvider(targetType);
        if (supportedDeserializerProviderOpt.isPresent()) {
            return Optional.of(supportedDeserializerProviderOpt.get().getDeserializerProvider().provideDeserializer(customization));
        }
        return Optional.empty();
    }

    private Type determineRuntimeType() {
        Type runtimeType = ReflectionTypeUtils.resolveGenericType(wrapper, null != genericType ? genericType : this.runtimeType);
        //Try to infer best from JSON event.
        if (Object.class == runtimeType) {
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
                    return jsonbContext.getConfigProperties().getDefaultMapImplType();
                case VALUE_NULL:
                    return Object.class;
                default:
                    throw new IllegalStateException("Can't infer deserialization type type: " + parsedEvent);
            }
        }
        return runtimeType;
    }

}

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
import org.eclipse.yasson.internal.ComponentBindingRegistry;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBoundCustomization;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.model.customization.PropertyCustomization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Builder for currently processed items by unmarshaller.
 */
public class JsonDeserializerBuilder extends SerializerBuilderBase<JsonDeserializerBuilder> {

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
     * @param inputEvent last json event for constructed deserializer.
     * @return Updated object.
     */
    public JsonDeserializerBuilder withJsonEvent(JsonParser.Event inputEvent) {
        this.parsedEvent = inputEvent;
        return this;
    }

    /**
     * Build an fully initialized item.
     *
     * @return built item
     */
    public JsonbDeserializer<?> buildDeserializer() {
        setRuntimeType(inferRuntimeType());
        Class<?> resolvedClass = ReflectionTypeResolver.getRawType(getRuntimeType());
        Optional<TypeAdapterBinding> adapterBindingOpt = Optional.empty();
        Customization customConfig = getCustomization();
        if (null == customConfig || customConfig instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization boundCustomization = (ComponentBoundCustomization) customConfig;
            //First check if user deserializer is registered for such type
            final ComponentBindingRegistry componentRegistry = getJsonbContext().getComponentMatcher();
            Optional<JsonbDeserializerBinding<?>> customDeserializerOpt = componentRegistry.getDeserializerBinding(getRuntimeType(), boundCustomization);
            if (customDeserializerOpt.isPresent()) {
                return new UserDeserializerDeserializer<>(this, customDeserializerOpt.get());
            }
            //Second user components is registered.
            Optional<TypeAdapterBinding> typeAdapterOpt = componentRegistry.getDeserializeAdapterBinding(getRuntimeType(), boundCustomization);
            if (typeAdapterOpt.isPresent()) {
                adapterBindingOpt = typeAdapterOpt;
                setRuntimeType(adapterBindingOpt.get().getToType());
                setWrapper(new AdaptedObjectDeserializer<>(adapterBindingOpt.get(), (AbstractContainerDeserializer<?>) getWrapper()));
                resolvedClass = ReflectionTypeResolver.getRawType(getRuntimeType());
            }
        }
        if (resolvedClass == Optional.class) {
            return new OptionalObjectDeserializer(this);
        }
        //In case of Base64 json value would be string and recognition by JsonValueType would not work
        if (isByteArray(resolvedClass)) {
            String deserializationStrategy = getJsonbContext().getConfigProperties().getBinaryDataStrategy();
            switch(deserializationStrategy) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArrayDeserializer(this);
                default:
                    return new ByteArrayBase64Deserializer(customConfig);
            }
        }
        if (isCharArray(resolvedClass)) {
            return new CharArrayDeserializer(this);
        }
        //Third deserializer is a supported value type to deserialize to JSON_VALUE
        if (isJsonValueEvent(parsedEvent)) {
            final Optional<AbstractValueTypeDeserializer<?>> supportedDeserializerOpt = getSupportedTypeDeserializer(resolvedClass);
            if (!supportedDeserializerOpt.isPresent()) {
                if (JsonParser.Event.VALUE_NULL == parsedEvent) {
                    return NullDeserializer.INSTANCE;
                }
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR, getRuntimeType()));
            }
            return wrapWithAdaptation(adapterBindingOpt, supportedDeserializerOpt.get());
        }
        JsonbDeserializer<?> resultDeserializer;
        if (JsonParser.Event.START_ARRAY != parsedEvent) {
            if (JsonParser.Event.START_OBJECT == parsedEvent) {
                if (!JsonValue.class.isAssignableFrom(resolvedClass)) {
                    if (!Map.class.isAssignableFrom(resolvedClass)) {
                        if (!resolvedClass.isInterface()) {
                            if (adapterBindingOpt.isPresent()) {
                                setRuntimeType(adapterBindingOpt.get().getToType());
                                resolvedClass = ReflectionTypeResolver.getRawType(getRuntimeType());
                            }
                            setClassModel(getClassModel(resolvedClass));
                            resultDeserializer = new ObjectDeserializer<>(this);
                            return wrapWithAdaptation(adapterBindingOpt, resultDeserializer);
                        } else {
                            Class<?> mappingType = getInterfaceMappedType(resolvedClass);
                            if (null == mappingType) {
                                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INFER_TYPE_FOR_UNMARSHALL, resolvedClass.getName()));
                            }
                            setRuntimeType(mappingType);
                            setClassModel(getClassModel(mappingType));
                            return new ObjectDeserializer<>(this);
                        }
                    } else {
                        final JsonbDeserializer<?> entriesArrayDeserializer = new MapDeserializer<>(this);
                        return wrapWithAdaptation(adapterBindingOpt, entriesArrayDeserializer);
                    }
                } else {
                    return wrapWithAdaptation(adapterBindingOpt, new JsonObjectDeserializer(this));
                }
            }
        } else {
            if (!JsonValue.class.isAssignableFrom(resolvedClass)) {
                if (!Map.class.isAssignableFrom(resolvedClass)) {
                    if (!resolvedClass.isArray() && !(getRuntimeType() instanceof GenericArrayType)) {
                        if (!Collection.class.isAssignableFrom(resolvedClass)) {
                            throw new JsonbException("Can't deserialize JSON array into: " + getRuntimeType());
                        } else {
                            resultDeserializer = new CollectionDeserializer<>(this);
                            return wrapWithAdaptation(adapterBindingOpt, resultDeserializer);
                        }
                    } else {
                        resultDeserializer = createArrayItemDeserializer(resolvedClass.getComponentType());
                        return wrapWithAdaptation(adapterBindingOpt, resultDeserializer);
                    }
                } else {
                    final JsonbDeserializer<?> entriesArrayDeserializer = new MapEntriesArrayDeserializer<>(this);
                    return wrapWithAdaptation(adapterBindingOpt, entriesArrayDeserializer);
                }
            } else {
                return wrapWithAdaptation(adapterBindingOpt, new JsonArrayDeserializer(this));
            }
        }
        throw new JsonbException("unresolved type for deserialization: " + getRuntimeType());
    }

    /**
     * Checks if event is a value event.
     *
     * @param inputEvent JSON event to check.
     * @return True if one of value events.
     */
    public static boolean isJsonValueEvent(JsonParser.Event inputEvent) {
        switch(inputEvent) {
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

    private Optional<AbstractValueTypeDeserializer<?>> getSupportedTypeDeserializer(Class<?> resolvedClass) {
        final Optional<? extends SerializerProviderWrapper> serializerProviderOpt = DefaultSerializerRegistry.getInstance().findSerializerProvider(resolvedClass);
        if (serializerProviderOpt.isPresent()) {
            return Optional.of(serializerProviderOpt.get().getDeserializerProvider().provideDeserializer(getCustomization()));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private JsonbDeserializer<?> wrapWithAdaptation(Optional<TypeAdapterBinding> adapterBindingOpt, JsonbDeserializer<?> innerDeserializer) {
        final Optional<JsonbDeserializer<?>> adaptedDeserializerOpt = adapterBindingOpt.map(adapterInfo -> {
            setAdaptedItemCaptor((AdaptedObjectDeserializer) getWrapper(), innerDeserializer);
            return (JsonbDeserializer<?>) getWrapper();
        });
        return adaptedDeserializerOpt.orElse(innerDeserializer);
    }

    private <T, A> void setAdaptedItemCaptor(AdaptedObjectDeserializer<T, A> adaptedDecorator, JsonbDeserializer<T> innerAdaptedDeserializer) {
        adaptedDecorator.setAdaptedTypeDeserializer(innerAdaptedDeserializer);
    }

    private Type inferRuntimeType() {
        Type inferredType = ReflectionTypeResolver.resolveTypeDefault(getWrapper(), null != getGenericType() ? getGenericType() : getRuntimeType());
        //Try to infer best from JSON event.
        if (Object.class == inferredType) {
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
        return inferredType;
    }

    private Class<?> getInterfaceMappedType(Class<?> interfaceClass) {
        if (interfaceClass.isInterface()) {
            Class<?> implClass = null;
            //annotation
            if (getCustomization() instanceof PropertyCustomization) {
                implClass = ((PropertyCustomization) getCustomization()).getImplementationClass();
            }
            //JsonbConfig
            if (null == implClass) {
                implClass = getJsonbContext().getConfigProperties().getUserTypeMapping().get(interfaceClass);
            }
            if (null != implClass) {
                if (!interfaceClass.isAssignableFrom(implClass)) {
                    throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.IMPL_CLASS_INCOMPATIBLE, implClass, interfaceClass));
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

    private boolean isByteArray(Class<?> resolvedClass) {
        return resolvedClass.isArray() && Byte.TYPE == resolvedClass.getComponentType();
    }

    private boolean isCharArray(Class<?> resolvedClass) {
        return resolvedClass.isArray() && Character.TYPE == resolvedClass.getComponentType();
    }
}

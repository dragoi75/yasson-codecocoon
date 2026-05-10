/*******************************************************************************
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 * Sebastien Rius
 ******************************************************************************/
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
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBoundCustomization;
import org.eclipse.yasson.internal.model.customization.PropertyCustomization;
import org.eclipse.yasson.internal.properties.MessageKey;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Builder for currently processed items by unmarshaller.
 *
 * @author Roman Grigoriadi
 * @author Sebastien Rius
 */
public class JsonDeserializerBuilder extends AbstractSerializationBuilder<JsonDeserializerBuilder> {

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
     * @param payload last json event for constructed deserializer.
     * @return Updated object.
     */
    public JsonDeserializerBuilder setJsonValueType(JsonParser.Event payload) {
        this.parsedEvent = payload;
        return this;
    }

    /**
     * Build an fully initialized item.
     *
     * @return built item
     */
    @SuppressWarnings("unchecked")
    public JsonbDeserializer<?> buildDeserializer() {
        runtimeType = inferRuntimeType();
        Class<?> baseType = ReflectionTypeResolver.getRawType(getRuntimeType());

        Optional<AdapterBinding> adapterBindingOpt = Optional.empty();
        if (customization == null
                || customization instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization boundCustomization = (ComponentBoundCustomization) customization;

            //First check if user deserializer is registered for such type
            final ComponentMatcher matcher = jsonbContext.getComponentMatcher();
            Optional<DeserializerBinding<?>> deserializerBindingOpt =
                    matcher.getDeserializerBinding(getRuntimeType(), boundCustomization);
            if (deserializerBindingOpt.isPresent()) {
                return new UserDeserializerDeserializer<>(this, deserializerBindingOpt.get());
            }

            //Second user components is registered.
            Optional<AdapterBinding> adapterHolder = matcher.getAdapterBinding(getRuntimeType(), boundCustomization);
            if (adapterHolder.isPresent()) {
                adapterBindingOpt = adapterHolder;
                runtimeType = adapterBindingOpt.get().getToType();
                wrapper = new AdaptedObjectDeserializer<>(adapterBindingOpt.get(), (AbstractCollectionDeserializer<?>) wrapper);
                baseType = ReflectionTypeResolver.getRawType(getRuntimeType());
            }
        }


        if (Optional.class == baseType) {
            return new OptionalObjectDeserializer(this);
        }

        //In case of Base64 json value would be string and recognition by JsonValueType would not work
        if (isByteArray(baseType)) {
            String approach = jsonbContext.getConfigProperties().getBinaryDataStrategy();
            switch (approach) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArrayDeserializer(this);
                default:
                    return new ByteArrayBase64Deserializer(customization);
            }
        }

        if (isCharArray(baseType)) {
            return new CharArrayDeserializer(this);
        }

        //Third deserializer is a supported value type to deserialize to JSON_VALUE
        if (isJsonValueEvent()) {
            final Optional<AbstractValueTypeDeserializer<?>> supportedDeserializerOpt = getSupportedTypeDeserializer(baseType);
            if (!supportedDeserializerOpt.isPresent()) {
                if (parsedEvent == JsonParser.Event.VALUE_NULL) {
                    return NullDeserializer.INSTANCE;
                }
                throw new JsonbException(MessageBundle.getMessage(MessageKey.DESERIALIZE_VALUE_ERROR, getRuntimeType()));
            }
            return wrapWithAdapter(adapterBindingOpt, supportedDeserializerOpt.get());
        }

        JsonbDeserializer<?> resultHandler;
        if (parsedEvent == JsonParser.Event.START_ARRAY) {
            if (JsonValue.class.isAssignableFrom(baseType)) {
                return wrapWithAdapter(adapterBindingOpt, new JsonArrayDeserializer(this));
            } else if (baseType.isArray() || getRuntimeType() instanceof GenericArrayType) {
                resultHandler = createArrayItemDeserializer(baseType.getComponentType());
                return wrapWithAdapter(adapterBindingOpt, resultHandler);
            } else if (Collection.class.isAssignableFrom(baseType)) {
                resultHandler = new CollectionDeserializer<>(this);
                return wrapWithAdapter(adapterBindingOpt, resultHandler);
            } else {
                throw new JsonbException("Can't deserialize JSON array into: " + getRuntimeType());
            }
        } else if(parsedEvent == JsonParser.Event.START_OBJECT) {
            if (JsonValue.class.isAssignableFrom(baseType)) {
                return wrapWithAdapter(adapterBindingOpt, new JsonObjectDeserializer(this));
            } else if (Map.class.isAssignableFrom(baseType)) {
                final JsonbDeserializer<?> mapHandler = new MapDeserializer(this);
                return wrapWithAdapter(adapterBindingOpt, mapHandler);
            } else if (baseType.isInterface()) {
                Class<?> targetMappedType = getInterfaceMappedType(baseType);
                if (targetMappedType == null) {
                    throw new JsonbException(MessageBundle.getMessage(MessageKey.INFER_TYPE_FOR_UNMARSHALL, baseType.getName()));
                }
                runtimeType = targetMappedType;
                classModel = getClassModel(targetMappedType);
                return new ObjectDeserializer<>(this);
            } else {
                if (adapterBindingOpt.isPresent()) {
                    runtimeType = adapterBindingOpt.get().getToType();
                    baseType = ReflectionTypeResolver.getRawType(getRuntimeType());
                }

                classModel = getClassModel(baseType);

                resultHandler = new ObjectDeserializer<>(this);
                return wrapWithAdapter(adapterBindingOpt, resultHandler);
            }
        }
        throw new JsonbException("unresolved type for deserialization: " + getRuntimeType());
    }

    private boolean isJsonValueEvent() {
        switch (parsedEvent) {
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


    private Optional<AbstractValueTypeDeserializer<?>> getSupportedTypeDeserializer(Class<?> baseType) {
        final Optional<? extends SerializerProviderWrapper> supportedDeserializerWrapperOpt = DefaultSerializerRegistry.getInstance().getValueSerializerProvider(baseType);
        if (supportedDeserializerWrapperOpt.isPresent()) {
            return Optional.of(supportedDeserializerWrapperOpt.get().getDeserializerProvider().provideDeserializer(customization));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private JsonbDeserializer<?> wrapWithAdapter(Optional<AdapterBinding> adapterBindingOpt, JsonbDeserializer<?> elementDeserializer) {
        final Optional<JsonbDeserializer<?>> adaptedDeserializerOpt = adapterBindingOpt.map(adapterInfo -> {
            setAdaptedItemCaptor((AdaptedObjectDeserializer)wrapper, elementDeserializer);
            return (JsonbDeserializer<?>)wrapper;
        });
        return adaptedDeserializerOpt.orElse(elementDeserializer);
    }

    private <T,A> void setAdaptedItemCaptor(AdaptedObjectDeserializer<T,A> decoratorInstance, JsonbDeserializer<T> adaptedDeserializer) {
        decoratorInstance.setAdaptedTypeDeserializer(adaptedDeserializer);
    }

    private Type inferRuntimeType() {
        Type inferredType = ReflectionTypeResolver.resolveActualType(wrapper, genericType != null ? genericType : runtimeType);
        //Try to infer best from JSON event.
        if (inferredType == Object.class) {
            switch (parsedEvent) {
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
        return inferredType;
    }

    private Class<?> getInterfaceMappedType(Class<?> ifaceClass) {
        if (ifaceClass.isInterface()) {
            Class implClass = null;
            //annotation
            if (customization instanceof PropertyCustomization) {
                 implClass = ((PropertyCustomization) customization).getImplementationClass();
            }
            //JsonbConfig
            if (implClass == null) {
                implClass = jsonbContext.getConfigProperties().getUserTypeMapping().get(ifaceClass);
            }
            if (implClass != null) {
                if (!ifaceClass.isAssignableFrom(implClass)) {
                    throw new JsonbException(MessageBundle.getMessage(MessageKey.IMPL_CLASS_INCOMPATIBLE, implClass, ifaceClass));
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
        if (elementType == byte.class) {
            return new ByteArrayDeserializer(this);
        } else if (elementType == short.class) {
            return new ShortArrayDeserializer(this);
        } else if (elementType == int.class) {
            return new IntArrayDeserializer(this);
        } else if (elementType == long.class) {
            return new LongArrayDeserializer(this);
        } else if (elementType == float.class) {
            return new FloatArrayDeserializer(this);
        } else if (elementType == double.class) {
            return new DoubleArrayDeserializer(this);
        } else {
            return new ObjectArrayDeserializer(this);
        }
    }

    private boolean isByteArray(Class<?> baseType) {
        return baseType.isArray() && baseType.getComponentType() == Byte.TYPE;
    }

    private boolean isCharArray(Class<?> baseType) {
        return baseType.isArray() && baseType.getComponentType() == Character.TYPE;
    }
}

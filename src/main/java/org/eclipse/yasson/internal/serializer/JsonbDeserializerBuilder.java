/*******************************************************************************
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
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
public class JsonbDeserializerBuilder extends AbstractSerializerBuilder<JsonbDeserializerBuilder> {

    /**
     * Value type of JSON event.
     */
    private JsonParser.Event parsedEvent;

    /**
     * Creates a new builder.
     *
     * @param bindingContext Context.
     */
    public JsonbDeserializerBuilder(JsonbContext bindingContext) {
        super(bindingContext);
    }

    /**
     * Sets value type.
     *
     * @param inputMessage last json event for constructed deserializer.
     * @return Updated object.
     */
    public JsonbDeserializerBuilder withJsonEvent(JsonParser.Event inputMessage) {
        this.parsedEvent = inputMessage;
        return this;
    }

    /**
     * Build an fully initialized item.
     *
     * @return built item
     */
    @SuppressWarnings("unchecked")
    public JsonbDeserializer<?> build() {
        runtimeType = resolveDeserializationType();
        Class<?> baseType = ReflectionUtils.getRawType(getRuntimeType());

        Optional<AdapterBinding> adapterBindingOpt = Optional.empty();
        if (customization == null
                || customization instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization componentCustomization = (ComponentBoundCustomization) customization;

            //First check if user deserializer is registered for such type
            final ComponentMatcher componentFilter = jsonbContext.getComponentMatcher();
            Optional<DeserializerBinding<?>> customDeserializerOpt =
                    componentFilter.getDeserializerBinding(getRuntimeType(), componentCustomization);
            if (customDeserializerOpt.isPresent()) {
                return new UserDeserializerDeserializer<>(this, customDeserializerOpt.get());
            }

            //Second user components is registered.
            Optional<AdapterBinding> adapterCandidate = componentFilter.getAdapterBinding(getRuntimeType(), componentCustomization);
            if (adapterCandidate.isPresent()) {
                adapterBindingOpt = adapterCandidate;
                runtimeType = adapterBindingOpt.get().getToType();
                wrapper = new AdaptedObjectDeserializer<>(adapterBindingOpt.get(), (AbstractContainerDeserializer<?>) wrapper);
                baseType = ReflectionUtils.getRawType(getRuntimeType());
            }
        }


        if (Optional.class == baseType) {
            return new OptionalValueDeserializer(this);
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
            final Optional<AbstractValueTypeDeserializer<?>> typeDeserializerOpt = getSupportedTypeDeserializer(baseType);
            if (!typeDeserializerOpt.isPresent()) {
                throw new JsonbException(Messages.getMessage(MessageKeys.DESERIALIZE_VALUE_ERROR, getRuntimeType()));
            }
            return wrapAdapted(adapterBindingOpt, typeDeserializerOpt.get());
        }

        JsonbDeserializer<?> objectReader;
        if (parsedEvent == JsonParser.Event.START_ARRAY) {
            if (JsonValue.class.isAssignableFrom(baseType)) {
                return wrapAdapted(adapterBindingOpt, new JsonArrayDeserializer(this));
            } else if (baseType.isArray() || getRuntimeType() instanceof GenericArrayType) {
                objectReader = createArrayDeserializer(baseType.getComponentType());
                return wrapAdapted(adapterBindingOpt, objectReader);
            } else if (Collection.class.isAssignableFrom(baseType)) {
                objectReader = new CollectionDeserializer<>(this);
                return wrapAdapted(adapterBindingOpt, objectReader);
            } else {
                throw new JsonbException("Can't deserialize JSON array into: " + getRuntimeType());
            }
        } else if(parsedEvent == JsonParser.Event.START_OBJECT) {
            if (JsonValue.class.isAssignableFrom(baseType)) {
                return wrapAdapted(adapterBindingOpt, new JsonObjectDeserializer(this));
            } else if (Map.class.isAssignableFrom(baseType)) {
                final JsonbDeserializer<?> mapReader = new MapDeserializer(this);
                return wrapAdapted(adapterBindingOpt, mapReader);
            } else if (baseType.isInterface()) {
                Class<?> targetType = getInterfaceMappedType(baseType);
                if (targetType == null) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.INFER_TYPE_FOR_UNMARSHALL, baseType.getName()));
                }
                runtimeType = targetType;
                classModel = getClassModel(targetType);
                return new ObjectDeserializer<>(this);
            } else {
                if (adapterBindingOpt.isPresent()) {
                    runtimeType = adapterBindingOpt.get().getToType();
                    baseType = ReflectionUtils.getRawType(getRuntimeType());
                }

                classModel = getClassModel(baseType);

                objectReader = new ObjectDeserializer<>(this);
                return wrapAdapted(adapterBindingOpt, objectReader);
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
        final Optional<? extends SerializerProviderWrapper> supportedSerializerProviderOpt = DefaultSerializers.getInstance().findValueSerializerProvider(baseType);
        if (supportedSerializerProviderOpt.isPresent()) {
            return Optional.of(supportedSerializerProviderOpt.get().getDeserializerProvider().provideDeserializer(customization));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private JsonbDeserializer<?> wrapAdapted(Optional<AdapterBinding> adapterBindingOpt, JsonbDeserializer<?> elementDeserializer) {
        final Optional<JsonbDeserializer<?>> decoratedDeserializerOpt = adapterBindingOpt.map(adapterMetadata -> {
            setAdaptedItemCaptor((AdaptedObjectDeserializer)wrapper, elementDeserializer);
            return (JsonbDeserializer<?>)wrapper;
        });
        return decoratedDeserializerOpt.orElse(elementDeserializer);
    }

    private <T,A> void setAdaptedItemCaptor(AdaptedObjectDeserializer<T,A> decoratorInstance, JsonbDeserializer<T> wrappedDeserializer) {
        decoratorInstance.setAdaptedTypeDeserializer(wrappedDeserializer);
    }

    private Type resolveDeserializationType() {
        Type resolvedType = ReflectionUtils.resolveType(wrapper, genericType != null ? genericType : runtimeType);
        //Try to infer best from JSON event.
        if (resolvedType == Object.class) {
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
                default:
                throw new IllegalStateException("Can't infer deserialization type type: " + parsedEvent);

            }
        }
        return resolvedType;
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
                    throw new JsonbException(Messages.getMessage(MessageKeys.IMPL_CLASS_INCOMPATIBLE, implClass, ifaceClass));
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
    private JsonbDeserializer<?> createArrayDeserializer(Class<?> elementType) {
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

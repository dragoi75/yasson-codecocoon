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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import javax.json.JsonValue;
import javax.json.bind.JsonbConfig;
import javax.json.bind.JsonbException;
import javax.json.bind.config.BinaryDataStrategy;
import javax.json.bind.config.PropertyOrderStrategy;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;

import org.eclipse.yasson.internal.ComponentMatcher;
import org.eclipse.yasson.internal.JsonbConfigurationContext;
import org.eclipse.yasson.internal.ReflectionHelper;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.model.ReverseTreeMap;
import org.eclipse.yasson.internal.model.customization.ComponentBoundCustomization;
import org.eclipse.yasson.internal.model.customization.PropertyCustomization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Builder for currently processed items by unmarshaller.
 *
 * @author Roman Grigoriadi
 * @author Sebastien Rius
 */
public class DeserializationBuilder extends AbstractSerializationBuilder<DeserializationBuilder> {

    /**
     * Value type of JSON event.
     */
    private JsonParser.Event parsedEvent;

    /**
     * Map runtime type to use according to ordering strategy set in associated JSONB configuration, HashMap if none was set
     */
    @SuppressWarnings("rawtypes")
    private final Class<? extends Map> mapImplementation;
    
    /**
     * Creates a new builder.
     *
     * @param jsonbConfig Context.
     */
    public DeserializationBuilder(JsonbConfigurationContext jsonbConfig) {
        super(jsonbConfig);
        String operatingSystem = (String) jsonbConfig.getConfig().getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY).orElse(PropertyOrderStrategy.ANY);
        switch (operatingSystem) {
            case PropertyOrderStrategy.LEXICOGRAPHICAL:
                mapImplementation = TreeMap.class;
                break;
            case PropertyOrderStrategy.REVERSE:
                mapImplementation = ReverseTreeMap.class;
                break;
            case PropertyOrderStrategy.ANY:
            default:
                mapImplementation = HashMap.class;
                break;
        }
    }

    /**
     * @return the mapImplType
     */
    @SuppressWarnings("rawtypes")
    public Class<? extends Map> getMapImplType() {
        return mapImplementation;
    }

    /**
     * Sets value type.
     *
     * @param inputRecord last json event for constructed deserializer.
     * @return Updated object.
     */
    public DeserializationBuilder withJsonEvent(JsonParser.Event inputRecord) {
        this.parsedEvent = inputRecord;
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
        Class<?> baseType = ReflectionHelper.getRawType(getRuntimeType());

        Optional<AdapterBinding> adapterBindingOpt = Optional.empty();
        if (customization == null
                || customization instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization componentCustomization = (ComponentBoundCustomization) customization;

            //First check if user deserializer is registered for such type
            final ComponentMatcher componentSelector = jsonbContext.getComponentMatcher();
            Optional<DeserializerBinding<?>> customDeserializerBinding =
                    componentSelector.getDeserializerBinding(getRuntimeType(), componentCustomization);
            if (customDeserializerBinding.isPresent()) {
                return new UserDeserializerDeserializer<>(this, customDeserializerBinding.get());
            }

            //Second user components is registered.
            Optional<AdapterBinding> adapterOptional = componentSelector.getAdapterBinding(getRuntimeType(), componentCustomization);
            if (adapterOptional.isPresent()) {
                adapterBindingOpt = adapterOptional;
                runtimeType = adapterBindingOpt.get().getToType();
                wrapper = new AdaptedObjectDeserializer<>(adapterBindingOpt.get(), (AbstractContainerDeserializer<?>) wrapper);
                baseType = ReflectionHelper.getRawType(getRuntimeType());
            }
        }


        if (Optional.class == baseType) {
            return new OptionalObjectDeserializer(this);
        }

        //In case of Base64 json value would be string and recognition by JsonValueType would not work
        if (isByteArray(baseType)) {
            String resolutionMode = jsonbContext.getConfigProperties().getBinaryDataStrategy();
            switch (resolutionMode) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArrayDeserializer(this);
                default:
                    return new ByteArrayBase64Deserializer(customization);
            }
        }

        //Third deserializer is a supported value type to deserialize to JSON_VALUE
        if (isJsonValueEvent()) {
            final Optional<AbstractValueTypeDeserializer<?>> supportedValueDeserializer = getSupportedTypeDeserializer(baseType);
            if (!supportedValueDeserializer.isPresent()) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR, getRuntimeType()));
            }
            return wrapIfAdapted(adapterBindingOpt, supportedValueDeserializer.get());
        }

        JsonbDeserializer<?> decoder;
        if (parsedEvent == JsonParser.Event.START_ARRAY) {
            if (JsonValue.class.isAssignableFrom(baseType)) {
                return wrapIfAdapted(adapterBindingOpt, new JsonArrayDeserializer(this));
            } else if (baseType.isArray() || getRuntimeType() instanceof GenericArrayType) {
                decoder = createArrayElementDeserializer(baseType.getComponentType());
                return wrapIfAdapted(adapterBindingOpt, decoder);
            } else if (Collection.class.isAssignableFrom(baseType)) {
                decoder = new CollectionDeserializer<>(this);
                return wrapIfAdapted(adapterBindingOpt, decoder);
            } else {
                throw new JsonbException("Can't deserialize JSON array into: " + getRuntimeType());
            }
        } else if(parsedEvent == JsonParser.Event.START_OBJECT) {
            if (JsonValue.class.isAssignableFrom(baseType)) {
                return wrapIfAdapted(adapterBindingOpt, new JsonObjectDeserializer(this));
            } else if (Map.class.isAssignableFrom(baseType)) {
                final JsonbDeserializer<?> mapHandler = new MapDeserializer(this);
                return wrapIfAdapted(adapterBindingOpt, mapHandler);
            } else if (baseType.isInterface()) {
                Class<?> targetType = getInterfaceMappedType(baseType);
                if (targetType == null) {
                    throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.INFER_TYPE_FOR_UNMARSHALL, baseType.getName()));
                }
                runtimeType = targetType;
                classModel = getClassModel(targetType);
                return new ObjectDeserializer<>(this);
            } else {
                if (adapterBindingOpt.isPresent()) {
                    runtimeType = adapterBindingOpt.get().getToType();
                    baseType = ReflectionHelper.getRawType(getRuntimeType());
                }

                classModel = getClassModel(baseType);

                decoder = new ObjectDeserializer<>(this);
                return wrapIfAdapted(adapterBindingOpt, decoder);
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
        final Optional<? extends SerializerProviderWrapper> serializerProviderOptional = DefaultSerializerProvider.getInstance().locateValueSerializerProvider(baseType);
        if (serializerProviderOptional.isPresent()) {
            return Optional.of(serializerProviderOptional.get().getDeserializerProvider().provideDeserializer(customization));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private JsonbDeserializer<?> wrapIfAdapted(Optional<AdapterBinding> adapterBindingOpt, JsonbDeserializer<?> elementDeserializer) {
        final Optional<JsonbDeserializer<?>> adaptedOpt = adapterBindingOpt.map(adapterInfo -> {
            setAdaptedItemCaptor((AdaptedObjectDeserializer)wrapper, elementDeserializer);
            return (JsonbDeserializer<?>)wrapper;
        });
        return adaptedOpt.orElse(elementDeserializer);
    }

    private <T,A> void setAdaptedItemCaptor(AdaptedObjectDeserializer<T,A> decorator, JsonbDeserializer<T> adaptedDeserializer) {
        decorator.setAdaptedTypeDeserializer(adaptedDeserializer);
    }

    private Type inferRuntimeType() {
        Type inferredType = ReflectionHelper.resolveGenericType(wrapper, genericType != null ? genericType : runtimeType);
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
                    return mapImplementation;
                default:
                throw new IllegalStateException("Can't infer deserialization type type: " + parsedEvent);

            }
        }
        return inferredType;
    }

    private Class<?> getInterfaceMappedType(Class<?> iface) {
        if (iface.isInterface()) {
            Class concreteImpl = null;
            //annotation
            if (customization instanceof PropertyCustomization) {
                 concreteImpl = ((PropertyCustomization) customization).getImplementationClass();
            }
            //JsonbConfig
            if (concreteImpl == null) {
                concreteImpl = jsonbContext.getConfigProperties().getUserTypeMapping().get(iface);
            }
            if (concreteImpl != null) {
                if (!iface.isAssignableFrom(concreteImpl)) {
                    throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.IMPL_CLASS_INCOMPATIBLE, concreteImpl, iface));
                }
                return concreteImpl;
            }
        }
        return null;
    }

    /**
     * Instance is not created in case of array items, because, we don't know how long it should be
     * till parser ends parsing.
     */
    private JsonbDeserializer<?> createArrayElementDeserializer(Class<?> elementType) {
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
}

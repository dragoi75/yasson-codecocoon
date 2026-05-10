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

import org.eclipse.yasson.internal.ComponentBindingResolver;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinder;
import org.eclipse.yasson.internal.model.ReversedTreeMap;
import org.eclipse.yasson.internal.model.customization.ComponentBindingCustomization;
import org.eclipse.yasson.internal.model.customization.PropertySerializationConfig;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Builder for currently processed items by unmarshaller.
 *
 * @author Roman Grigoriadi
 * @author Sebastien Rius
 */
public class JsonbDeserializerBuilder extends BaseSerializerBuilder<JsonbDeserializerBuilder> {

    /**
     * Value type of JSON event.
     */
    private JsonParser.Event parsedRecord;

    /**
     * Map runtime type to use according to ordering strategy set in associated JSONB configuration, HashMap if none was set
     */
    @SuppressWarnings("rawtypes")
    private final Class<? extends Map> mapImplementationClass;
    
    /**
     * Creates a new builder.
     *
     * @param runtimeContext Context.
     */
    public JsonbDeserializerBuilder(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
        String operatingSystemName = (String) runtimeContext.getConfig().getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY).orElse(PropertyOrderStrategy.ANY);
        switch (operatingSystemName) {
            case PropertyOrderStrategy.LEXICOGRAPHICAL:
                mapImplementationClass = TreeMap.class;
                break;
            case PropertyOrderStrategy.REVERSE:
                mapImplementationClass = ReversedTreeMap.class;
                break;
            case PropertyOrderStrategy.ANY:
            default:
                mapImplementationClass = HashMap.class;
                break;
        }
    }

    /**
     * @return the mapImplType
     */
    @SuppressWarnings("rawtypes")
    public Class<? extends Map> getMapImplType() {
        return mapImplementationClass;
    }

    /**
     * Sets value type.
     *
     * @param payload last json event for constructed deserializer.
     * @return Updated object.
     */
    public JsonbDeserializerBuilder setJsonValueType(JsonParser.Event payload) {
        this.parsedRecord = payload;
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
        Class<?> actualType = ReflectionTypeResolver.getRawType(getRuntimeType());

        Optional<TypeAdapterBinding> typeAdapterOptional = Optional.empty();
        if (customization == null
                || customization instanceof ComponentBindingCustomization) {
            ComponentBindingCustomization bindingCustomization = (ComponentBindingCustomization) customization;

            //First check if user deserializer is registered for such type
            final ComponentBindingResolver componentResolver = jsonbContext.getComponentMatcher();
            Optional<DeserializerBinder<?>> customDeserializerOptional =
                    componentResolver.getDeserializerBinding(getRuntimeType(), bindingCustomization);
            if (customDeserializerOptional.isPresent()) {
                return new UserDelegatingDeserializer<>(this, customDeserializerOptional.get());
            }

            //Second user components is registered.
            Optional<TypeAdapterBinding> adapterRefOptional = componentResolver.getAdapterBinding(getRuntimeType(), bindingCustomization);
            if (adapterRefOptional.isPresent()) {
                typeAdapterOptional = adapterRefOptional;
                runtimeType = typeAdapterOptional.get().getToType();
                wrapper = new AdapterAwareObjectDeserializer<>(typeAdapterOptional.get(), (BaseContainerDeserializer<?>) wrapper);
                actualType = ReflectionTypeResolver.getRawType(getRuntimeType());
            }
        }


        if (Optional.class == actualType) {
            return new OptionalValueDeserializer(this);
        }

        //In case of Base64 json value would be string and recognition by JsonValueType would not work
        if (isByteArray(actualType)) {
            String approachName = jsonbContext.getConfigProperties().getBinaryDataStrategy();
            switch (approachName) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArrayToObjectDeserializer(this);
                default:
                    return new Base64ByteArrayDeserializer(customization);
            }
        }

        //Third deserializer is a supported value type to deserialize to JSON_VALUE
        if (isJsonValueEvent()) {
            final Optional<BaseValueTypeDeserializer<?>> baseTypeDeserializerOpt = getSupportedTypeDeserializer(actualType);
            if (!baseTypeDeserializerOpt.isPresent()) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.DESERIALIZE_VALUE_ERROR, getRuntimeType()));
            }
            return wrapWithAdapter(typeAdapterOptional, baseTypeDeserializerOpt.get());
        }

        JsonbDeserializer<?> valueReader;
        if (parsedRecord == JsonParser.Event.START_ARRAY) {
            if (JsonValue.class.isAssignableFrom(actualType)) {
                return wrapWithAdapter(typeAdapterOptional, new JsonArrayToObjectDeserializer(this));
            } else if (actualType.isArray() || getRuntimeType() instanceof GenericArrayType) {
                valueReader = createArrayItemDeserializer(actualType.getComponentType());
                return wrapWithAdapter(typeAdapterOptional, valueReader);
            } else if (Collection.class.isAssignableFrom(actualType)) {
                valueReader = new CollectionParser<>(this);
                return wrapWithAdapter(typeAdapterOptional, valueReader);
            } else {
                throw new JsonbException("Can't deserialize JSON array into: " + getRuntimeType());
            }
        } else if(parsedRecord == JsonParser.Event.START_OBJECT) {
            if (JsonValue.class.isAssignableFrom(actualType)) {
                return wrapWithAdapter(typeAdapterOptional, new JsonObjectConverter(this));
            } else if (Map.class.isAssignableFrom(actualType)) {
                final JsonbDeserializer<?> mapInstanceReader = new MapInstanceDeserializer(this);
                return wrapWithAdapter(typeAdapterOptional, mapInstanceReader);
            } else if (actualType.isInterface()) {
                Class<?> targetType = getInterfaceMappedType(actualType);
                if (targetType == null) {
                    throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.INFER_TYPE_FOR_UNMARSHALL, actualType.getName()));
                }
                runtimeType = targetType;
                classModel = getClassModel(targetType);
                return new InstanceDeserializer<>(this);
            } else {
                if (typeAdapterOptional.isPresent()) {
                    runtimeType = typeAdapterOptional.get().getToType();
                    actualType = ReflectionTypeResolver.getRawType(getRuntimeType());
                }

                classModel = getClassModel(actualType);

                valueReader = new InstanceDeserializer<>(this);
                return wrapWithAdapter(typeAdapterOptional, valueReader);
            }
        }
        throw new JsonbException("unresolved type for deserialization: " + getRuntimeType());
    }

    private boolean isJsonValueEvent() {
        switch (parsedRecord) {
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


    private Optional<BaseValueTypeDeserializer<?>> getSupportedTypeDeserializer(Class<?> actualType) {
        final Optional<? extends SerializerProviderAdapter> providerAdapterOptional = DefaultSerializerRegistry.getInstance().lookupValueSerializerProvider(actualType);
        if (providerAdapterOptional.isPresent()) {
            return Optional.of(providerAdapterOptional.get().getDeserializerProvider().provideDeserializer(customization));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private JsonbDeserializer<?> wrapWithAdapter(Optional<TypeAdapterBinding> typeAdapterOptional, JsonbDeserializer<?> element) {
        final Optional<JsonbDeserializer<?>> wrappedDeserializerOptional = typeAdapterOptional.map(adapterInfo -> {
            setAdaptedItemCaptor((AdapterAwareObjectDeserializer)wrapper, element);
            return (JsonbDeserializer<?>)wrapper;
        });
        return wrappedDeserializerOptional.orElse(element);
    }

    private <T,A> void setAdaptedItemCaptor(AdapterAwareObjectDeserializer<T,A> decoratorWrapper, JsonbDeserializer<T> innerDeserializer) {
        decoratorWrapper.setAdaptedTypeDeserializer(innerDeserializer);
    }

    private Type inferRuntimeType() {
        Type inferredType = ReflectionTypeResolver.resolveActualType(wrapper, genericType != null ? genericType : runtimeType);
        //Try to infer best from JSON event.
        if (inferredType == Object.class) {
            switch (parsedRecord) {
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
                    return mapImplementationClass;
                default:
                throw new IllegalStateException("Can't infer deserialization type type: " + parsedRecord);

            }
        }
        return inferredType;
    }

    private Class<?> getInterfaceMappedType(Class<?> ifaceClass) {
        if (ifaceClass.isInterface()) {
            Class implClass = null;
            //annotation
            if (customization instanceof PropertySerializationConfig) {
                 implClass = ((PropertySerializationConfig) customization).getImplementationClass();
            }
            //JsonbConfig
            if (implClass == null) {
                implClass = jsonbContext.getConfigProperties().getUserTypeMapping().get(ifaceClass);
            }
            if (implClass != null) {
                if (!ifaceClass.isAssignableFrom(implClass)) {
                    throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.IMPL_CLASS_INCOMPATIBLE, implClass, ifaceClass));
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
            return new ByteArrayToObjectDeserializer(this);
        } else if (elementType == short.class) {
            return new PrimitiveShortArrayDeserializer(this);
        } else if (elementType == int.class) {
            return new PrimitiveIntArrayDeserializer(this);
        } else if (elementType == long.class) {
            return new LongArrayParser(this);
        } else if (elementType == float.class) {
            return new PrimitiveFloatArrayDeserializer(this);
        } else if (elementType == double.class) {
            return new DoubleArrayParser(this);
        } else {
            return new ObjectArrayParser(this);
        }
    }

    private boolean isByteArray(Class<?> actualType) {
        return actualType.isArray() && actualType.getComponentType() == Byte.TYPE;
    }
}

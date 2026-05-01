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
import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.ReflectionUtils;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.model.ReverseTreeMap;
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
public class JsonDeserializerBuilder extends AbstractSerializerBuilder<JsonDeserializerBuilder> {

    /**
     * Value type of JSON event.
     */
    private JsonParser.Event parsedEvent;

    /**
     * Map runtime type to use according to ordering strategy set in associated JSONB configuration, HashMap if none was set
     */
    @SuppressWarnings("rawtypes")
    private final Class<? extends Map> mapImplementationClass;
    
    /**
     * Creates a new builder.
     *
     * @param jsonbScope Context.
     */
    public JsonDeserializerBuilder(JsonbContext jsonbScope) {
        super(jsonbScope);
        String operatingSystem = (String) jsonbScope.getConfig().getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY).orElse(PropertyOrderStrategy.ANY);
        switch (operatingSystem) {
            case PropertyOrderStrategy.LEXICOGRAPHICAL:
                mapImplementationClass = TreeMap.class;
                break;
            case PropertyOrderStrategy.REVERSE:
                mapImplementationClass = ReverseTreeMap.class;
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
    @SuppressWarnings("unchecked")
    public JsonbDeserializer<?> build() {
        runtimeType = inferRuntimeType();
        Class<?> candidateType = ReflectionUtils.getRawType(getRuntimeType());

        Optional<AdapterBinding> adapterBindingOpt = Optional.empty();
        if (customization == null
                || customization instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization componentCustomization = (ComponentBoundCustomization) customization;

            //First check if user deserializer is registered for such type
            final ComponentMatcher componentPredicate = jsonbContext.getComponentMatcher();
            Optional<DeserializerBinding<?>> customDeserializerOpt =
                    componentPredicate.getDeserializerBinding(getRuntimeType(), componentCustomization);
            if (customDeserializerOpt.isPresent()) {
                return new UserDeserializerDeserializer<>(this, customDeserializerOpt.get());
            }

            //Second user components is registered.
            Optional<AdapterBinding> adapterOptional = componentPredicate.getAdapterBinding(getRuntimeType(), componentCustomization);
            if (adapterOptional.isPresent()) {
                adapterBindingOpt = adapterOptional;
                runtimeType = adapterBindingOpt.get().getToType();
                wrapper = new AdaptedObjectDeserializer<>(adapterBindingOpt.get(), (AbstractContainerDeserializer<?>) wrapper);
                candidateType = ReflectionUtils.getRawType(getRuntimeType());
            }
        }


        if (Optional.class == candidateType) {
            return new OptionalObjectDeserializer(this);
        }

        //In case of Base64 json value would be string and recognition by JsonValueType would not work
        if (isByteArray(candidateType)) {
            String resolutionStrategy = jsonbContext.getConfigProperties().getBinaryDataStrategy();
            switch (resolutionStrategy) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArrayDeserializer(this);
                default:
                    return new ByteArrayBase64Deserializer(customization);
            }
        }

        //Third deserializer is a supported value type to deserialize to JSON_VALUE
        if (isJsonValueEvent()) {
            final Optional<AbstractValueTypeDeserializer<?>> valueTypeDeserializerOpt = getSupportedTypeDeserializer(candidateType);
            if (!valueTypeDeserializerOpt.isPresent()) {
                throw new JsonbException(Messages.getMessage(MessageKeys.DESERIALIZE_VALUE_ERROR, getRuntimeType()));
            }
            return wrapAdapted(adapterBindingOpt, valueTypeDeserializerOpt.get());
        }

        JsonbDeserializer<?> instanceDeserializer;
        if (parsedEvent == JsonParser.Event.START_ARRAY) {
            if (JsonValue.class.isAssignableFrom(candidateType)) {
                return wrapAdapted(adapterBindingOpt, new JsonArrayDeserializer(this));
            } else if (candidateType.isArray() || getRuntimeType() instanceof GenericArrayType) {
                instanceDeserializer = createArrayItemDeserializer(candidateType.getComponentType());
                return wrapAdapted(adapterBindingOpt, instanceDeserializer);
            } else if (Collection.class.isAssignableFrom(candidateType)) {
                instanceDeserializer = new CollectionDeserializer<>(this);
                return wrapAdapted(adapterBindingOpt, instanceDeserializer);
            } else {
                throw new JsonbException("Can't deserialize JSON array into: " + getRuntimeType());
            }
        } else if(parsedEvent == JsonParser.Event.START_OBJECT) {
            if (JsonValue.class.isAssignableFrom(candidateType)) {
                return wrapAdapted(adapterBindingOpt, new JsonObjectDeserializer(this));
            } else if (Map.class.isAssignableFrom(candidateType)) {
                final JsonbDeserializer<?> mapTypeDeserializer = new MapDeserializer(this);
                return wrapAdapted(adapterBindingOpt, mapTypeDeserializer);
            } else if (candidateType.isInterface()) {
                Class<?> resolvedMappedClass = getInterfaceMappedType(candidateType);
                if (resolvedMappedClass == null) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.INFER_TYPE_FOR_UNMARSHALL, candidateType.getName()));
                }
                runtimeType = resolvedMappedClass;
                classModel = getClassModel(resolvedMappedClass);
                return new ObjectDeserializer<>(this);
            } else {
                if (adapterBindingOpt.isPresent()) {
                    runtimeType = adapterBindingOpt.get().getToType();
                    candidateType = ReflectionUtils.getRawType(getRuntimeType());
                }

                classModel = getClassModel(candidateType);

                instanceDeserializer = new ObjectDeserializer<>(this);
                return wrapAdapted(adapterBindingOpt, instanceDeserializer);
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


    private Optional<AbstractValueTypeDeserializer<?>> getSupportedTypeDeserializer(Class<?> candidateType) {
        final Optional<? extends SerializerProviderWrapper> serializerWrapperOpt = DefaultSerializers.getInstance().findValueSerializerProvider(candidateType);
        if (serializerWrapperOpt.isPresent()) {
            return Optional.of(serializerWrapperOpt.get().getDeserializerProvider().provideDeserializer(customization));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private JsonbDeserializer<?> wrapAdapted(Optional<AdapterBinding> adapterBindingOpt, JsonbDeserializer<?> candidateDeserializer) {
        final Optional<JsonbDeserializer<?>> adaptedDeserializerOpt = adapterBindingOpt.map(adapterBinding -> {
            setAdaptedItemCaptor((AdaptedObjectDeserializer)wrapper, candidateDeserializer);
            return (JsonbDeserializer<?>)wrapper;
        });
        return adaptedDeserializerOpt.orElse(candidateDeserializer);
    }

    private <T,A> void setAdaptedItemCaptor(AdaptedObjectDeserializer<T,A> adaptedDecorator, JsonbDeserializer<T> adaptedDeserializer) {
        adaptedDecorator.setAdaptedTypeDeserializer(adaptedDeserializer);
    }

    private Type inferRuntimeType() {
        Type inferredType = ReflectionUtils.resolveType(wrapper, genericType != null ? genericType : runtimeType);
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
                    return mapImplementationClass;
                default:
                throw new IllegalStateException("Can't infer deserialization type type: " + parsedEvent);

            }
        }
        return inferredType;
    }

    private Class<?> getInterfaceMappedType(Class<?> interfaceClass) {
        if (interfaceClass.isInterface()) {
            Class implClass = null;
            //annotation
            if (customization instanceof PropertyCustomization) {
                 implClass = ((PropertyCustomization) customization).getImplementationClass();
            }
            //JsonbConfig
            if (implClass == null) {
                implClass = jsonbContext.getConfigProperties().getUserTypeMapping().get(interfaceClass);
            }
            if (implClass != null) {
                if (!interfaceClass.isAssignableFrom(implClass)) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.IMPL_CLASS_INCOMPATIBLE, implClass, interfaceClass));
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

    private boolean isByteArray(Class<?> candidateType) {
        return candidateType.isArray() && candidateType.getComponentType() == Byte.TYPE;
    }
}

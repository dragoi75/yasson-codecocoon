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

import org.eclipse.yasson.internal.ComponentBindingResolver;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.ReflectiveTypeUtils;
import org.eclipse.yasson.internal.components.AdapterBindingDescriptor;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBindingCustomizer;
import org.eclipse.yasson.internal.model.customization.PropertySerializationCustomization;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;

/**
 * Builder for currently processed items by unmarshaller.
 *
 * @author Roman Grigoriadi
 * @author Sebastien Rius
 */
public class JsonValueDeserializerBuilder extends AbstractSerializationBuilder<JsonValueDeserializerBuilder> {

    /**
     * Value type of JSON event.
     */
    private JsonParser.Event parsedEvent;

    /**
     * Creates a new builder.
     *
     * @param runtimeContext Context.
     */
    public JsonValueDeserializerBuilder(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
    }

    /**
     * Sets value type.
     *
     * @param incoming last json event for constructed deserializer.
     * @return Updated object.
     */
    public JsonValueDeserializerBuilder withJsonEvent(JsonParser.Event incoming) {
        this.parsedEvent = incoming;
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
        Class<?> declaredClass = ReflectiveTypeUtils.getRawType(getRuntimeType());

        Optional<AdapterBindingDescriptor> adapterDescriptorOpt = Optional.empty();
        if (customization == null
                || customization instanceof ComponentBindingCustomizer) {
            ComponentBindingCustomizer componentCustomizer = (ComponentBindingCustomizer) customization;

            //First check if user deserializer is registered for such type
            final ComponentBindingResolver componentResolver = jsonbContext.getComponentMatcher();
            Optional<JsonbDeserializerBinding<?>> customDeserializerOpt =
                    componentResolver.getDeserializerBinding(getRuntimeType(), componentCustomizer);
            if (customDeserializerOpt.isPresent()) {
                return new UserDeserializerFactory<>(this, customDeserializerOpt.get());
            }

            //Second user components is registered.
            Optional<AdapterBindingDescriptor> bindingDescriptorOpt = componentResolver.getAdapterBinding(getRuntimeType(), componentCustomizer);
            if (bindingDescriptorOpt.isPresent()) {
                adapterDescriptorOpt = bindingDescriptorOpt;
                runtimeType = adapterDescriptorOpt.get().getToType();
                wrapper = new AdaptedTypeDeserializer<>(adapterDescriptorOpt.get(), (BaseContainerDeserializer<?>) wrapper);
                declaredClass = ReflectiveTypeUtils.getRawType(getRuntimeType());
            }
        }


        if (Optional.class == declaredClass) {
            return new OptionalValueDeserializer(this);
        }

        //In case of Base64 json value would be string and recognition by JsonValueType would not work
        if (isByteArray(declaredClass)) {
            String approach = jsonbContext.getConfigProperties().getBinaryDataStrategy();
            switch (approach) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArrayDeserializerImpl(this);
                default:
                    return new Base64ByteArrayDeserializer(customization);
            }
        }

        if (isCharArray(declaredClass)) {
            return new CharacterArrayDeserializer(this);
        }

        //Third deserializer is a supported value type to deserialize to JSON_VALUE
        if (isJsonValueEvent()) {
            final Optional<BaseValueTypeDeserializer<?>> typeDeserializerOpt = getSupportedTypeDeserializer(declaredClass);
            if (!typeDeserializerOpt.isPresent()) {
                throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.DESERIALIZE_VALUE_ERROR, getRuntimeType()));
            }
            return wrapWithAdapter(adapterDescriptorOpt, typeDeserializerOpt.get());
        }

        JsonbDeserializer<?> resultHandler;
        if (parsedEvent == JsonParser.Event.START_ARRAY) {
            if (JsonValue.class.isAssignableFrom(declaredClass)) {
                return wrapWithAdapter(adapterDescriptorOpt, new JsonArrayConverter(this));
            } else if (declaredClass.isArray() || getRuntimeType() instanceof GenericArrayType) {
                resultHandler = createArrayItemDeserializer(declaredClass.getComponentType());
                return wrapWithAdapter(adapterDescriptorOpt, resultHandler);
            } else if (Collection.class.isAssignableFrom(declaredClass)) {
                resultHandler = new CollectionValueDeserializer<>(this);
                return wrapWithAdapter(adapterDescriptorOpt, resultHandler);
            } else {
                throw new JsonbException("Can't deserialize JSON array into: " + getRuntimeType());
            }
        } else if(parsedEvent == JsonParser.Event.START_OBJECT) {
            if (JsonValue.class.isAssignableFrom(declaredClass)) {
                return wrapWithAdapter(adapterDescriptorOpt, new JsonToObjectDeserializer(this));
            } else if (Map.class.isAssignableFrom(declaredClass)) {
                final JsonbDeserializer<?> mapInstance = new MapInstanceDeserializer(this);
                return wrapWithAdapter(adapterDescriptorOpt, mapInstance);
            } else if (declaredClass.isInterface()) {
                Class<?> targetType = getInterfaceMappedType(declaredClass);
                if (targetType == null) {
                    throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.INFER_TYPE_FOR_UNMARSHALL, declaredClass.getName()));
                }
                runtimeType = targetType;
                classModel = getClassModel(targetType);
                return new JsonObjectDeserializer<>(this);
            } else {
                if (adapterDescriptorOpt.isPresent()) {
                    runtimeType = adapterDescriptorOpt.get().getToType();
                    declaredClass = ReflectiveTypeUtils.getRawType(getRuntimeType());
                }

                classModel = getClassModel(declaredClass);

                resultHandler = new JsonObjectDeserializer<>(this);
                return wrapWithAdapter(adapterDescriptorOpt, resultHandler);
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


    private Optional<BaseValueTypeDeserializer<?>> getSupportedTypeDeserializer(Class<?> declaredClass) {
        final Optional<? extends SerializationProviderAdapter> providerAdapterOpt = DefaultSerializerRegistry.getInstance().findSerializerProvider(declaredClass);
        if (providerAdapterOpt.isPresent()) {
            return Optional.of(providerAdapterOpt.get().getDeserializerProvider().provideDeserializer(customization));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private JsonbDeserializer<?> wrapWithAdapter(Optional<AdapterBindingDescriptor> adapterDescriptorOpt, JsonbDeserializer<?> element) {
        final Optional<JsonbDeserializer<?>> adaptedInstanceOpt = adapterDescriptorOpt.map(adapterInfo -> {
            setAdaptedItemCaptor((AdaptedTypeDeserializer)wrapper, element);
            return (JsonbDeserializer<?>)wrapper;
        });
        return adaptedInstanceOpt.orElse(element);
    }

    private <T,A> void setAdaptedItemCaptor(AdaptedTypeDeserializer<T,A> decoratorWrapper, JsonbDeserializer<T> innerDeserializer) {
        decoratorWrapper.setAdaptedTypeDeserializer(innerDeserializer);
    }

    private Type inferRuntimeType() {
        Type inferredType = ReflectiveTypeUtils.resolveGenericType(wrapper, genericType != null ? genericType : runtimeType);
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
            if (customization instanceof PropertySerializationCustomization) {
                 implClass = ((PropertySerializationCustomization) customization).getImplementationClass();
            }
            //JsonbConfig
            if (implClass == null) {
                implClass = jsonbContext.getConfigProperties().getUserTypeMapping().get(ifaceClass);
            }
            if (implClass != null) {
                if (!ifaceClass.isAssignableFrom(implClass)) {
                    throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.IMPL_CLASS_INCOMPATIBLE, implClass, ifaceClass));
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
            return new ByteArrayDeserializerImpl(this);
        } else if (elementType == short.class) {
            return new ShortArrayDeserializerImpl(this);
        } else if (elementType == int.class) {
            return new PrimitiveIntArrayDeserializer(this);
        } else if (elementType == long.class) {
            return new PrimitiveLongArrayDeserializer(this);
        } else if (elementType == float.class) {
            return new PrimitiveFloatArrayDeserializer(this);
        } else if (elementType == double.class) {
            return new PrimitiveDoubleArrayDeserializer(this);
        } else {
            return new ObjectArrayDeserializerImpl(this);
        }
    }

    private boolean isByteArray(Class<?> declaredClass) {
        return declaredClass.isArray() && declaredClass.getComponentType() == Byte.TYPE;
    }

    private boolean isCharArray(Class<?> declaredClass) {
        return declaredClass.isArray() && declaredClass.getComponentType() == Character.TYPE;
    }
}

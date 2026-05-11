/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.ComponentBindingResolver;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.components.SerializerBindingEntry;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBindingCustomization;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.bind.config.BinaryDataStrategy;
import javax.json.bind.serializer.JsonbSerializer;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Builder for serializers.
 *
 * @author Roman Grigoriadi
 */
public class TypeSerializerBuilder extends BaseSerializerBuilder<TypeSerializerBuilder> {

    private Class<?> targetType;

    /**
     * Creates a new builder.
     *
     * @param runtimeContext JSON-B context.
     */
    public TypeSerializerBuilder(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
    }

    /**
     * Adds object class.
     *
     * @param targetType object class
     * @return Builder.
     */
    public TypeSerializerBuilder setObjectClass(Class<?> targetType) {
        this.targetType = targetType;
        return this;
    }

    /**
     * Builds a {@link JsonbSerializer}.
     *
     * @return JsonbSerializer.
     */
    public JsonbSerializer<?> buildSerializer() {
        runtimeType = determineRuntimeType();
        if (customization instanceof ComponentBindingCustomization) {
            ComponentBindingCustomization bindingConfig = (ComponentBindingCustomization) this.customization;
            //First check if user deserializer is registered for such type
            final ComponentBindingResolver bindingResolver = jsonbContext.getComponentMatcher();
            Optional<SerializerBindingEntry<?>> customSerializer = bindingResolver.getSerializerBinding(getRuntimeType(), bindingConfig);
            if (customSerializer.isPresent()) {
                return new UserSerializerWrapper<>(classModel, customSerializer.get().getJsonbSerializer());
            }
            //Second user components is registered.
            Optional<TypeAdapterBinding> adapterBinding = bindingResolver.getAdapterBinding(getRuntimeType(), bindingConfig);
            if (adapterBinding.isPresent()) {
                return new AdapterBasedObjectSerializer<>(classModel, adapterBinding.get());
            }
        }
        final Optional<ConfigurableValueTypeSerializer<?>> valueTypeSerializer = getSupportedTypeSerializer(targetType);
        if (valueTypeSerializer.isPresent()) {
            return valueTypeSerializer.get();
        }
        if (!Collection.class.isAssignableFrom(targetType)) {
            if (!Map.class.isAssignableFrom(targetType)) {
                if (!isByteArray(targetType)) {
                    if (!targetType.isArray() && !(getRuntimeType() instanceof GenericArrayType)) {
                        if (!JsonValue.class.isAssignableFrom(targetType)) {
                            if (!Optional.class.isAssignableFrom(targetType)) {
                                jsonbContext.getMappingContext().registerSerializerProvider(targetType, new ObjectSerializerFactory());
                                return new ObjectMarshaller<>(this);
                            } else {
                                return new OptionalValueSerializer<>(this);
                            }
                        } else {
                            if (!JsonObject.class.isAssignableFrom(targetType)) {
                                return new JsonArrayElementSerializer(this);
                            } else {
                                return new JsonObjectSerializerImpl(this);
                            }
                        }
                    } else {
                        return createArrayElement(targetType.getComponentType());
                    }
                } else {
                    String mode = jsonbContext.getConfigProperties().getBinaryDataStrategy();
                    switch(mode) {
                        case BinaryDataStrategy.BYTE:
                            return new ByteArraySerializerImpl(this);
                        default:
                            return new ByteArrayToBase64Serializer(customization);
                    }
                }
            } else {
                return new MapTypeSerializer<>(this);
            }
        } else {
            return new CollectionJsonSerializer<>(this);
        }
    }

    private boolean isByteArray(Class<?> candidateType) {
        return candidateType.isArray() && Byte.TYPE == candidateType.getComponentType();
    }

    /**
     * Instance is not created in case of array items, because, we don't know how long it should be
     * till parser ends parsing.
     */
    private JsonbSerializer<?> createArrayElement(Class<?> elementType) {
        if (byte.class != elementType) {
            if (short.class != elementType) {
                if (int.class != elementType) {
                    if (long.class != elementType) {
                        if (float.class != elementType) {
                            if (double.class != elementType) {
                                return new ObjectArraySerializerImpl(this);
                            } else {
                                return new DoubleArrayEncoder(this);
                            }
                        } else {
                            return new FloatArraySerializerImpl(this);
                        }
                    } else {
                        return new LongArraySerializerImpl(this);
                    }
                } else {
                    return new IntArrayCodec(this);
                }
            } else {
                return new ShortPrimitiveArraySerializer(this);
            }
        } else {
            return new ByteArraySerializerImpl(this);
        }
    }

    private Optional<ConfigurableValueTypeSerializer<?>> getSupportedTypeSerializer(Class<?> candidateType) {
        final Optional<? extends SerializerProviderAdapter> serializerProviderOptional = DefaultSerializerRegistry.getInstance().lookupValueSerializerProvider(candidateType);
        if (serializerProviderOptional.isPresent()) {
            return Optional.of(serializerProviderOptional.get().getSerializerProvider().provideSerializer(customization));
        }
        return Optional.empty();
    }

    private Type determineRuntimeType() {
        if (null != genericType && Object.class != genericType) {
            return genericType;
        }
        return targetType;
    }
}

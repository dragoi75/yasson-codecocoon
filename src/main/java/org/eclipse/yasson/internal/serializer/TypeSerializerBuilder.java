/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.eclipse.yasson.internal.ComponentBindingRegistry;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.components.JsonbSerializerBinding;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBoundCustomization;

/**
 * Builder for serializers.
 */
public class TypeSerializerBuilder extends SerializerBuilderBase<TypeSerializerBuilder> {

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
        setRuntimeType(determineRuntimeType());
        if (getCustomization() instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization componentConfig = (ComponentBoundCustomization) this.getCustomization();
            //First check if user deserializer is registered for such type
            final ComponentBindingRegistry bindingRegistry = getJsonbContext().getComponentMatcher();
            Optional<JsonbSerializerBinding<?>> serializerBindingOpt = bindingRegistry.getSerializerBinding(getRuntimeType(), componentConfig);
            if (serializerBindingOpt.isPresent()) {
                return new UserSerializerSerializer<>(getClassModel(), serializerBindingOpt.get().getJsonbSerializer());
            }
            //Second user components is registered.
            Optional<TypeAdapterBinding> adapterBindingOpt = bindingRegistry.getSerializeAdapterBinding(getRuntimeType(), componentConfig);
            if (adapterBindingOpt.isPresent()) {
                return new AdaptedObjectSerializer<>(getClassModel(), adapterBindingOpt.get());
            }
        }
        final Optional<AbstractValueSerializer<?>> valueSerializerOpt = getSupportedTypeSerializer(targetType);
        if (valueSerializerOpt.isPresent()) {
            return valueSerializerOpt.get();
        }
        if (!Collection.class.isAssignableFrom(targetType)) {
            if (!Map.class.isAssignableFrom(targetType)) {
                if (!isByteArray(targetType)) {
                    if (!targetType.isArray() && !(getRuntimeType() instanceof GenericArrayType)) {
                        if (!JsonValue.class.isAssignableFrom(targetType)) {
                            if (!Optional.class.isAssignableFrom(targetType)) {
                                getJsonbContext().getMappingContext().registerSerializerProvider(targetType, new ObjectSerializerProvider());
                                return new ObjectSerializer<>(this);
                            } else {
                                return new OptionalObjectSerializer<>(this);
                            }
                        } else {
                            if (!JsonObject.class.isAssignableFrom(targetType)) {
                                return new JsonArraySerializer(this);
                            } else {
                                return new JsonObjectSerializer(this);
                            }
                        }
                    } else {
                        return createArraySerializer(targetType.getComponentType());
                    }
                } else {
                    String approach = getJsonbContext().getConfigProperties().getBinaryDataStrategy();
                    switch(approach) {
                        case BinaryDataStrategy.BYTE:
                            return new ByteArraySerializer(this);
                        default:
                            return new ByteArrayBase64Serializer(getCustomization());
                    }
                }
            } else {
                return new MapSerializer<>(this);
            }
        } else {
            return new CollectionSerializer<>(this);
        }
    }

    private boolean isByteArray(Class<?> candidateType) {
        return candidateType.isArray() && Byte.TYPE == candidateType.getComponentType();
    }

    /**
     * Instance is not created in case of array items, because, we don't know how long it should be
     * till parser ends parsing.
     */
    private JsonbSerializer<?> createArraySerializer(Class<?> elementType) {
        if (byte.class != elementType) {
            if (short.class != elementType) {
                if (char.class != elementType) {
                    if (int.class != elementType) {
                        if (long.class != elementType) {
                            if (float.class != elementType) {
                                if (double.class != elementType) {
                                    return new ObjectArraySerializer<>(this);
                                } else {
                                    return new DoubleArraySerializer(this);
                                }
                            } else {
                                return new FloatArraySerializer(this);
                            }
                        } else {
                            return new LongArraySerializer(this);
                        }
                    } else {
                        return new IntArraySerializer(this);
                    }
                } else {
                    return new CharArraySerializer(this);
                }
            } else {
                return new ShortArraySerializer(this);
            }
        } else {
            return new ByteArraySerializer(this);
        }
    }

    private Optional<AbstractValueSerializer<?>> getSupportedTypeSerializer(Class<?> candidateType) {
        final Optional<? extends SerializerProviderWrapper> serializerProviderOpt = DefaultSerializerRegistry.getInstance().findSerializerProvider(candidateType);
        if (serializerProviderOpt.isPresent()) {
            return Optional.of(serializerProviderOpt.get().getSerializerProvider().provideSerializer(getCustomization()));
        }
        return Optional.empty();
    }

    private Type determineRuntimeType() {
        Type runtimeType = getGenericType();
        if (null != runtimeType && Object.class != runtimeType) {
            return runtimeType;
        }
        return targetType;
    }
}

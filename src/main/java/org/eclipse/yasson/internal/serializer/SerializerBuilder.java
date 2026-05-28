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
import org.eclipse.yasson.internal.ComponentMatcher;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBoundCustomization;

/**
 * Builder for serializers.
 */
public class SerializerBuilder extends GenericSerializerBuilder<SerializerBuilder> {

    private Class<?> objectClass;

    private Type resolveRuntimeType() {
        Type genericType = getGenericType();
        if (null != genericType && Object.class != genericType) {
            return genericType;
        }
        return objectClass;
    }

    private Optional<AbstractValueTypeSerializer<?>> getSupportedTypeSerializer(Class<?> rawType) {
        final Optional<? extends SerializerProviderWrapper> supportedTypeSerializerOptional = DefaultSerializers.getInstance().findValueSerializerProvider(rawType);
        if (supportedTypeSerializerOptional.isPresent()) {
            return Optional.of(supportedTypeSerializerOptional.get().getSerializerProvider().provideSerializer(getCustomization()));
        }
        return Optional.empty();
    }

    /**
     * Instance is not created in case of array items, because, we don't know how long it should be
     * till parser ends parsing.
     */
    private JsonbSerializer<?> createArrayItem(Class<?> componentType) {
        if (byte.class != componentType) {
            if (short.class != componentType) {
                if (char.class != componentType) {
                    if (int.class != componentType) {
                        if (long.class != componentType) {
                            if (float.class != componentType) {
                                if (double.class != componentType) {
                                    if (boolean.class != componentType) {
                                        return new ObjectArraySerializer<>(this);
                                    } else {
                                        return new BooleanArraySerializer(this);
                                    }
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

    /**
     * Builds a {@link JsonbSerializer}.
     *
     * @return JsonbSerializer.
     */
    public JsonbSerializer<?> build() {
        setRuntimeType(resolveRuntimeType());
        if (getCustomization() instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization customization = (ComponentBoundCustomization) this.getCustomization();
            //First check if user deserializer is registered for such type
            final ComponentMatcher componentMatcher = getJsonbContext().getComponentMatcher();
            Optional<SerializerBinding<?>> userSerializer = componentMatcher.getSerializerBinding(getRuntimeType(), customization);
            if (userSerializer.isPresent()) {
                return new UserSerializerSerializer<>(getClassModel(), userSerializer.get().getJsonbSerializer());
            }
            //Second user components is registered.
            Optional<AdapterBinding> adapterInfoOptional = componentMatcher.getSerializeAdapterBinding(getRuntimeType(), customization);
            if (adapterInfoOptional.isPresent()) {
                return new AdaptedObjectSerializer<>(getClassModel(), adapterInfoOptional.get());
            }
        }
        final Optional<AbstractValueTypeSerializer<?>> supportedTypeSerializer = getSupportedTypeSerializer(objectClass);
        if (supportedTypeSerializer.isPresent()) {
            return supportedTypeSerializer.get();
        }
        if (!Collection.class.isAssignableFrom(objectClass)) {
            if (!Map.class.isAssignableFrom(objectClass)) {
                if (!isByteArray(objectClass)) {
                    if (!objectClass.isArray() && !(getRuntimeType() instanceof GenericArrayType)) {
                        if (!JsonValue.class.isAssignableFrom(objectClass)) {
                            if (!Optional.class.isAssignableFrom(objectClass)) {
                                getJsonbContext().getMappingContext().addSerializerProvider(objectClass, new ObjectSerializerProvider());
                                return new ObjectSerializer<>(this);
                            } else {
                                return new OptionalObjectSerializer<>(this);
                            }
                        } else {
                            if (!JsonObject.class.isAssignableFrom(objectClass)) {
                                return new JsonArraySerializer(this);
                            } else {
                                return new JsonObjectSerializer(this);
                            }
                        }
                    } else {
                        return createArrayItem(objectClass.getComponentType());
                    }
                } else {
                    String strategy = getJsonbContext().getConfigProperties().getBinaryDataStrategy();
                    switch(strategy) {
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

    /**
     * Adds object class.
     *
     * @param objectClass object class
     * @return Builder.
     */
    public SerializerBuilder withObjectClass(Class<?> objectClass) {
        this.objectClass = objectClass;
        return this;
    }

    private boolean isByteArray(Class<?> rawType) {
        return rawType.isArray() && Byte.TYPE == rawType.getComponentType();
    }

    /**
     * Creates a new builder.
     *
     * @param jsonbContext JSON-B context.
     */
    public SerializerBuilder(JsonbRuntimeContext jsonbContext) {
        super(jsonbContext);
    }

}

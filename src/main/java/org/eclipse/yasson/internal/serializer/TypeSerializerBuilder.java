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

import org.eclipse.yasson.internal.ComponentMatcher;
import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBoundCustomization;
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
public class TypeSerializerBuilder extends AbstractSerializationBuilder<TypeSerializerBuilder> {

    private Class<?> targetType;

    /**
     * Creates a new builder.
     *
     * @param jsonbEnv JSON-B context.
     */
    public TypeSerializerBuilder(JsonbContext jsonbEnv) {
        super(jsonbEnv);
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
        if (customization instanceof ComponentBoundCustomization) {
            ComponentBoundCustomization componentConfig = (ComponentBoundCustomization) this.customization;
            //First check if user deserializer is registered for such type
            final ComponentMatcher typeMatcher = jsonbContext.getComponentMatcher();
            Optional<SerializerBinding<?>> serializerBindingOpt = typeMatcher.getSerializerBinding(getRuntimeType(), componentConfig);
            if (serializerBindingOpt.isPresent()) {
                return new UserSerializerSerializer<>(classModel, serializerBindingOpt.get().getJsonbSerializer());
            }
            //Second user components is registered.
            Optional<AdapterBinding> adapterBindingOpt = typeMatcher.getAdapterBinding(getRuntimeType(), componentConfig);
            if (adapterBindingOpt.isPresent()) {
                return new AdaptedObjectSerializer<>(classModel, adapterBindingOpt.get());
            }
        }
        final Optional<ConfigurableValueTypeSerializer<?>> valueSerializerOpt = getSupportedTypeSerializer(targetType);
        if (valueSerializerOpt.isPresent()) {
            return valueSerializerOpt.get();
        }
        if (!Collection.class.isAssignableFrom(targetType)) {
            if (!Map.class.isAssignableFrom(targetType)) {
                if (!isByteArray(targetType)) {
                    if (!targetType.isArray() && !(getRuntimeType() instanceof GenericArrayType)) {
                        if (!JsonValue.class.isAssignableFrom(targetType)) {
                            if (!Optional.class.isAssignableFrom(targetType)) {
                                jsonbContext.getMappingContext().registerSerializerProvider(targetType, new ObjectSerializerProvider());
                                return new ObjectMarshaller<>(this);
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
                    String mode = jsonbContext.getConfigProperties().getBinaryDataStrategy();
                    switch(mode) {
                        case BinaryDataStrategy.BYTE:
                            return new ByteArraySerializer(this);
                        default:
                            return new ByteArrayBase64Serializer(customization);
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
                                    return new ObjectArraySerializer(this);
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

    private Optional<ConfigurableValueTypeSerializer<?>> getSupportedTypeSerializer(Class<?> candidateType) {
        final Optional<? extends SerializerProviderWrapper> providerWrapperOpt = DefaultSerializers.getInstance().findValueSerializerProvider(candidateType);
        if (providerWrapperOpt.isPresent()) {
            return Optional.of(providerWrapperOpt.get().getSerializerProvider().provideSerializer(customization));
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

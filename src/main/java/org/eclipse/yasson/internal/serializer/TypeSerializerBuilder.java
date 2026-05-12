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
            Optional<JsonbSerializerBinding<?>> serializerBindingOpt = bindingRegistry
                    .getSerializerBinding(getRuntimeType(), componentConfig);
            if (serializerBindingOpt.isPresent()) {
                return new UserSerializerSerializer<>(getClassModel(), serializerBindingOpt.get().getJsonbSerializer());
            }

            //Second user components is registered.
            Optional<TypeAdapterBinding> adapterBindingOpt = bindingRegistry
                    .getSerializeAdapterBinding(getRuntimeType(), componentConfig);
            if (adapterBindingOpt.isPresent()) {
                return new AdaptedObjectSerializer<>(getClassModel(), adapterBindingOpt.get());
            }
        }

        final Optional<AbstractValueSerializer<?>> valueSerializerOpt = getSupportedTypeSerializer(targetType);
        if (valueSerializerOpt.isPresent()) {
            return valueSerializerOpt.get();
        }

        if (Collection.class.isAssignableFrom(targetType)) {
            return new CollectionSerializer<>(this);
        } else if (Map.class.isAssignableFrom(targetType)) {
            return new MapSerializer<>(this);
        } else if (isByteArray(targetType)) {
            String approach = getJsonbContext().getConfigProperties().getBinaryDataStrategy();
            switch (approach) {
            case BinaryDataStrategy.BYTE:
                return new ByteArraySerializer(this);
            default:
                return new ByteArrayBase64Serializer(getCustomization());
            }
        } else if (targetType.isArray() || getRuntimeType() instanceof GenericArrayType) {
            return createArraySerializer(targetType.getComponentType());

        } else if (JsonValue.class.isAssignableFrom(targetType)) {
            if (JsonObject.class.isAssignableFrom(targetType)) {
                return new JsonObjectSerializer(this);
            } else {
                return new JsonArraySerializer(this);
            }
        } else if (Optional.class.isAssignableFrom(targetType)) {
            return new OptionalObjectSerializer<>(this);
        } else {
            getJsonbContext().getMappingContext().registerSerializerProvider(targetType, new ObjectSerializerProvider());
            return new ObjectSerializer<>(this);
        }

    }

    private boolean isByteArray(Class<?> candidateType) {
        return candidateType.isArray() && candidateType.getComponentType() == Byte.TYPE;
    }

    /**
     * Instance is not created in case of array items, because, we don't know how long it should be
     * till parser ends parsing.
     */
    private JsonbSerializer<?> createArraySerializer(Class<?> elementType) {
        if (elementType == byte.class) {
            return new ByteArraySerializer(this);
        } else if (elementType == short.class) {
            return new ShortArraySerializer(this);
        } else if (elementType == char.class) {
            return new CharArraySerializer(this);
        } else if (elementType == int.class) {
            return new IntArraySerializer(this);
        } else if (elementType == long.class) {
            return new LongArraySerializer(this);
        } else if (elementType == float.class) {
            return new FloatArraySerializer(this);
        } else if (elementType == double.class) {
            return new DoubleArraySerializer(this);
        } else {
            return new ObjectArraySerializer<>(this);
        }
    }

    private Optional<AbstractValueSerializer<?>> getSupportedTypeSerializer(Class<?> candidateType) {
        final Optional<? extends SerializerProviderWrapper> serializerProviderOpt = DefaultSerializerRegistry.getInstance()
                .findSerializerProvider(candidateType);
        if (serializerProviderOpt.isPresent()) {
            return Optional
                    .of(serializerProviderOpt.get().getSerializerProvider().provideSerializer(getCustomization()));
        }
        return Optional.empty();
    }

    private Type determineRuntimeType() {
        Type runtimeType = getGenericType();
        if (runtimeType != null && runtimeType != Object.class) {
            return runtimeType;
        }
        return targetType;
    }
}

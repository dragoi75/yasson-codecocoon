/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

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
public class ObjectSerializerBuilder extends AbstractSerializerBuilder<ObjectSerializerBuilder> {

    private Class<?> targetClass;

    /**
     * Creates a new builder.
     *
     * @param serializationContext JSON-B context.
     */
    public ObjectSerializerBuilder(JsonbContext serializationContext) {
        super(serializationContext);
    }

    /**
     * Adds object class.
     *
     * @param targetClass object class
     * @return Builder.
     */
    public ObjectSerializerBuilder setObjectClass(Class<?> targetClass) {
        this.targetClass = targetClass;
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
            ComponentBoundCustomization componentSettings = (ComponentBoundCustomization) this.customization;
            //First check if user deserializer is registered for such type
            final ComponentMatcher elementMatcher = jsonbContext.getComponentMatcher();
            Optional<SerializerBinding<?>> providedSerializer = elementMatcher.getSerializerBinding(getRuntimeType(), componentSettings);
            if (providedSerializer.isPresent()) {
                return new UserSerializerSerializer<>(classModel, providedSerializer.get().getJsonbSerializer());
            }

            //Second user components is registered.
            Optional<AdapterBinding> bindingOptional = elementMatcher.getAdapterBinding(getRuntimeType(), componentSettings);
            if (bindingOptional.isPresent()) {
                return new AdaptedObjectSerializer<>(classModel, bindingOptional.get());
            }
        }

        final Optional<AbstractValueTypeSerializer<?>> valueTypeSerializer = getSupportedTypeSerializer(targetClass);
        if (valueTypeSerializer.isPresent()) {
            return valueTypeSerializer.get();
        }

        if (Collection.class.isAssignableFrom(targetClass)) {
            return new CollectionSerializer<>(this);
        } else if (Map.class.isAssignableFrom(targetClass)) {
            return new MapSerializer<>(this);
        } else if (isByteArray(targetClass)) {
            String approach = jsonbContext.getConfigProperties().getBinaryDataStrategy();
            switch (approach) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArraySerializer(this);
                default:
                    return new ByteArrayBase64Serializer(customization);
            }
        } else if (targetClass.isArray() || getRuntimeType() instanceof GenericArrayType) {
            return createArraySerializer(targetClass.getComponentType());

        } else if (JsonValue.class.isAssignableFrom(targetClass)) {
            if(JsonObject.class.isAssignableFrom(targetClass)) {
                return new JsonObjectSerializer(this);
            } else {
                return new JsonArraySerializer(this);
            }
        } else if (Optional.class.isAssignableFrom(targetClass)) {
            return new OptionalObjectSerializer<>(this);
        } else {
            jsonbContext.getMappingContext().addSerializerProvider(targetClass, new ObjectSerializerProvider());
            return new ObjectSerializer<>(this);
        }

    }

    private boolean isByteArray(Class<?> baseClass) {
        return baseClass.isArray() && baseClass.getComponentType() == Byte.TYPE;
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
        } else if (elementType == int.class) {
            return new IntArraySerializer(this);
        } else if (elementType == long.class) {
            return new LongArraySerializer(this);
        } else if (elementType == float.class) {
            return new FloatArraySerializer(this);
        } else if (elementType == double.class) {
            return new DoubleArraySerializer(this);
        } else {
            return new ObjectArraySerializer(this);
        }
    }

    private Optional<AbstractValueTypeSerializer<?>> getSupportedTypeSerializer(Class<?> baseClass) {
        final Optional<? extends SerializerProviderWrapper> providerWrapperOptional = DefaultSerializers.getInstance().findValueSerializerProvider(baseClass);
        if (providerWrapperOptional.isPresent()) {
            return Optional.of(providerWrapperOptional.get().getSerializerProvider().provideSerializer(customization));
        }
        return Optional.empty();
    }

    private Type determineRuntimeType() {
        if (genericType != null && genericType != Object.class) {
            return genericType;
        }
        return targetClass;
    }
}

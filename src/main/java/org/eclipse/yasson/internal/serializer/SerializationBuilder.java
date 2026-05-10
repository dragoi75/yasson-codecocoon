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

import org.eclipse.yasson.internal.ComponentBindingResolver;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.components.AdapterBindingDescriptor;
import org.eclipse.yasson.internal.components.SerializerBinding;
import org.eclipse.yasson.internal.model.customization.ComponentBindingCustomizer;

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
public class SerializationBuilder extends AbstractSerializationBuilder<SerializationBuilder> {

    private Class<?> targetType;

    /**
     * Creates a new builder.
     *
     * @param runtimeContext JSON-B context.
     */
    public SerializationBuilder(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
    }

    /**
     * Adds object class.
     *
     * @param targetType object class
     * @return Builder.
     */
    public SerializationBuilder setObjectClass(Class<?> targetType) {
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

        if (customization instanceof ComponentBindingCustomizer) {
            ComponentBindingCustomizer bindingCustomizer = (ComponentBindingCustomizer) this.customization;
            //First check if user deserializer is registered for such type
            final ComponentBindingResolver bindingResolver = jsonbContext.getComponentMatcher();
            Optional<SerializerBinding<?>> customSerializer = bindingResolver.getSerializerBinding(getRuntimeType(), bindingCustomizer);
            if (customSerializer.isPresent()) {
                return new UserSerializerSerializer<>(classModel, customSerializer.get().getJsonbSerializer());
            }

            //Second user components is registered.
            Optional<AdapterBindingDescriptor> adapterDescriptorOpt = bindingResolver.getAdapterBinding(getRuntimeType(), bindingCustomizer);
            if (adapterDescriptorOpt.isPresent()) {
                return new AdaptedObjectSerializer<>(classModel, adapterDescriptorOpt.get());
            }
        }

        final Optional<ConfigurableValueTypeSerializer<?>> valueTypeSerializer = getSupportedTypeSerializer(targetType);
        if (valueTypeSerializer.isPresent()) {
            return valueTypeSerializer.get();
        }

        if (Collection.class.isAssignableFrom(targetType)) {
            return new CollectionSerializer<>(this);
        } else if (Map.class.isAssignableFrom(targetType)) {
            return new MapSerializer<>(this);
        } else if (isByteArray(targetType)) {
            String approach = jsonbContext.getConfigProperties().getBinaryDataStrategy();
            switch (approach) {
                case BinaryDataStrategy.BYTE:
                    return new ByteArraySerializer(this);
                default:
                    return new ByteArrayBase64Serializer(customization);
            }
        } else if (targetType.isArray() || getRuntimeType() instanceof GenericArrayType) {
            return createArraySerializer(targetType.getComponentType());

        } else if (JsonValue.class.isAssignableFrom(targetType)) {
            if(JsonObject.class.isAssignableFrom(targetType)) {
                return new JsonObjectSerializer(this);
            } else {
                return new JsonArraySerializer(this);
            }
        } else if (Optional.class.isAssignableFrom(targetType)) {
            return new OptionalValueSerializer<>(this);
        } else {
            jsonbContext.getMappingContext().registerSerializerProvider(targetType, new ObjectSerializerProvider());
            return new ObjectSerializer<>(this);
        }

    }

    private boolean isByteArray(Class<?> candidateClass) {
        return candidateClass.isArray() && candidateClass.getComponentType() == Byte.TYPE;
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
            return new ObjectArraySerializer(this);
        }
    }

    private Optional<ConfigurableValueTypeSerializer<?>> getSupportedTypeSerializer(Class<?> candidateClass) {
        final Optional<? extends SerializationProviderAdapter> providerAdapterOpt = DefaultSerializerRegistry.getInstance().findSerializerProvider(candidateClass);
        if (providerAdapterOpt.isPresent()) {
            return Optional.of(providerAdapterOpt.get().getSerializerProvider().provideSerializer(customization));
        }
        return Optional.empty();
    }

    private Type determineRuntimeType() {
        if (genericType != null && genericType != Object.class) {
            return genericType;
        }
        return targetType;
    }
}

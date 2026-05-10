/*******************************************************************************
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 * <p>
 * Contributors:
 *     Dmitry Kornilov - initial implementation
 ******************************************************************************/
package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.JsonbPropertyDescriptor;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.serializer.ContainerSerializerFactory;
import org.eclipse.yasson.internal.serializer.DefaultSerializerProvider;
import org.eclipse.yasson.internal.serializer.TypeSerializerBuilder;
import org.eclipse.yasson.internal.serializer.ValueTypeSerializerBase;

import javax.json.bind.JsonbException;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerationException;
import javax.json.stream.JsonGenerator;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * JSONB marshaller. Created each time marshalling operation called.
 *
 * @author Dmitry Kornilov
 * @author Roman Grigoriadi
 */
public class ObjectMarshaller extends ProcessingContextManager implements SerializationContext {

    private static final Logger OBJECT_MARSHAL_LOG = Logger.getLogger(ObjectMarshaller.class.getName());

    private final Type resolvedType;

    /**
     * Creates Marshaller for generation to String.
     *
     * @param configContext Current context.
     * @param baseRuntimeType Type of root object.
     */
    public ObjectMarshaller(JsonbConfigurationContext configContext, Type baseRuntimeType) {
        super(configContext);
        this.resolvedType = baseRuntimeType;
    }

    /**
     * Creates Marshaller for generation to String.
     *
     * @param configContext Current context.
     */
    public ObjectMarshaller(JsonbConfigurationContext configContext) {
        super(configContext);
        this.resolvedType = null;
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     *
     * @param inputValue object to marshall
     * @param jsonWriter generator to use
     */
    public void marshal(Object inputValue, JsonGenerator jsonWriter) {
        try {
            serializeRootObject(inputValue, jsonWriter);
        } catch (JsonbException exception) {
            OBJECT_MARSHAL_LOG.severe(exception.getMessage());
            throw exception;
        } finally {
            try {
                jsonWriter.close();
            } catch (JsonGenerationException generationException) {
                OBJECT_MARSHAL_LOG.severe(generationException.getMessage());
            }
        }
    }

    @Override
    public <T> void serialize(String propertyKey, T inputValue, JsonGenerator gen) {
        Objects.requireNonNull(propertyKey);
        Objects.requireNonNull(inputValue);
        gen.writeKey(propertyKey);
        serializeRootObject(inputValue, gen);
    }

    @Override
    public <T> void serialize(T inputValue, JsonGenerator gen) {
        Objects.requireNonNull(inputValue);
        serializeRootObject(inputValue, gen);
    }

    /**
     * Serializes root element.
     *
     * @param <T> Root type
     * @param topValue Root.
     * @param gen JSON generator.
     */
    @SuppressWarnings("unchecked")
    public <T> void serializeRootObject(T topValue, JsonGenerator gen) {
        final JsonbSerializer<T> primarySerializer = (JsonbSerializer<T>) getRootSerializer(topValue.getClass());
        if (jsonbContext.getConfigProperties().isStrictIJson() &&
                primarySerializer instanceof ValueTypeSerializerBase) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.IJSON_ENABLED_SINGLE_VALUE));
        }
        primarySerializer.serialize(topValue, gen, this);
    }

    private JsonbSerializer<?> getRootSerializer(Class<?> targetClazz) {
        final ContainerSerializerFactory providerFactory = getMappingContext().getSerializerProvider(targetClazz);
        if (providerFactory != null) {
            return providerFactory
                    .getSerializer(new JsonbPropertyDescriptor()
                            .setRuntimeType(resolvedType));
        }
        TypeSerializerBuilder typeSerializerBuilder = new TypeSerializerBuilder(jsonbContext)
                .setObjectClass(targetClazz)
                .setType(resolvedType);

        if (!DefaultSerializerProvider.getInstance().isKnownType(targetClazz)) {
            ClassDescriptor descriptor = getMappingContext().getOrCreateClassModel(targetClazz);
            typeSerializerBuilder.setCustomization(descriptor.getCustomization());
        }
        return typeSerializerBuilder.buildSerializer();
    }

}

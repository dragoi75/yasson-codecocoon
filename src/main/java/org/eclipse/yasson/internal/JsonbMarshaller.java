/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2020 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.yasson.internal;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.logging.Logger;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerationException;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.JsonbPropertyMetadata;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.serializer.AbstractValueSerializer;
import org.eclipse.yasson.internal.serializer.ContainerSerializerFactory;
import org.eclipse.yasson.internal.serializer.DefaultSerializerRegistry;
import org.eclipse.yasson.internal.serializer.TypeSerializerBuilder;

/**
 * JSONB marshaller. Created each time marshalling operation called.
 */
public class JsonbMarshaller extends ObjectProcessingContext implements SerializationContext {

    private static final Logger JSONB_MARSHAL_LOG = Logger.getLogger(JsonbMarshaller.class.getName());

    private final Type actualType;

    /**
     * Creates Marshaller for generation to String.
     *
     * @param runtimeContext    Current context.
     * @param rootType Type of root object.
     */
    public JsonbMarshaller(JsonbRuntimeContext runtimeContext, Type rootType) {
        super(runtimeContext);
        this.actualType = rootType;
    }

    /**
     * Creates Marshaller for generation to String.
     *
     * @param runtimeContext Current context.
     */
    public JsonbMarshaller(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
        this.actualType = null;
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     *
     * @param value        object to marshall
     * @param generator generator to use
     * @param terminateAfterWrite         if generator should be closed
     */
    public void writeJson(Object value, JsonGenerator generator, boolean terminateAfterWrite) {
        try {
            serializeRootValue(value, generator);
        } catch (JsonbException jsonbError) {
            JSONB_MARSHAL_LOG.severe(jsonbError.getMessage());
            throw jsonbError;
        } catch (Exception jsonbError) {
            JSONB_MARSHAL_LOG.severe(jsonbError.getMessage());
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INTERNAL_ERROR, jsonbError.getMessage()), jsonbError);
        } finally {
            try {
                if (!terminateAfterWrite) {
                    generator.flush();
                } else {
                    generator.close();
                }
            } catch (JsonGenerationException generationEx) {
                JSONB_MARSHAL_LOG.severe(generationEx.getMessage());
            }
        }
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     * Closes the generator on completion.
     *
     * @param value        object to marshall
     * @param generator generator to use
     */
    public void writeJson(Object value, JsonGenerator generator) {
        writeJson(value, generator, true);
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     * Leaves generator open for further interaction after completion.
     *
     * @param value        object to marshall
     * @param generator generator to use
     */
    public void marshallNoClose(Object value, JsonGenerator generator) {
        writeJson(value, generator, false);
    }

    @Override
    public <T> void serialize(String propertyName, T value, JsonGenerator jsonWriter) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(value);
        jsonWriter.writeKey(propertyName);
        serializeRootValue(value, jsonWriter);
    }

    @Override
    public <T> void serialize(T value, JsonGenerator jsonWriter) {
        Objects.requireNonNull(value);
        serializeRootValue(value, jsonWriter);
    }

    /**
     * Serializes root element.
     *
     * @param <T>       Root type
     * @param rootValue      Root.
     * @param jsonWriter JSON generator.
     */
    @SuppressWarnings("unchecked")
    public <T> void serializeRootValue(T rootValue, JsonGenerator jsonWriter) {
        if (null == rootValue) {
            getJsonbContext().getConfigProperties().getNullSerializer().serialize(null, jsonWriter, this);
            return;
        }
        final JsonbSerializer<T> rootSer = (JsonbSerializer<T>) getRootSerializer(rootValue.getClass());
        if (getJsonbContext().getConfigProperties().isStrictIJson() && rootSer instanceof AbstractValueSerializer) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.IJSON_ENABLED_SINGLE_VALUE));
        }
        rootSer.serialize(rootValue, jsonWriter, this);
    }

    JsonbSerializer<?> getRootSerializer(Class<?> rootClass) {
        final ContainerSerializerFactory serializerFactory = getMappingContext().getSerializerProvider(rootClass);
        if (null != serializerFactory) {
            return serializerFactory.createSerializer(new JsonbPropertyMetadata().setRuntimeType(actualType));
        }
        TypeSerializerBuilder typeBuilder = new TypeSerializerBuilder(getJsonbContext()).setObjectClass(rootClass).setType(actualType);
        if (!DefaultSerializerRegistry.getInstance().isKnownType(rootClass)) {
            ClassDescriptor classDescriptor = getMappingContext().getOrCreateClassModel(rootClass);
            typeBuilder.setCustomization(classDescriptor.getClassCustomization());
        }
        return typeBuilder.buildSerializer();
    }
}

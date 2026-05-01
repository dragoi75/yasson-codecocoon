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

import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;
import org.eclipse.yasson.internal.serializer.AbstractValueTypeSerializer;
import org.eclipse.yasson.internal.serializer.ContainerSerializerProvider;
import org.eclipse.yasson.internal.serializer.DefaultSerializers;
import org.eclipse.yasson.internal.serializer.SerializerBuilder;
import org.eclipse.yasson.internal.model.JsonbPropertyInfo;

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
public class Serializer extends ProcessingContext implements SerializationContext {

    private static final Logger SERIALIZER_LOG = Logger.getLogger(Serializer.class.getName());

    private final Type currentType;

    /**
     * Creates Serializer for generation to String.
     *
     * @param bindingContext Current context.
     * @param rootType Type of root object.
     */
    public Serializer(JsonbContext bindingContext, Type rootType) {
        super(bindingContext);
        this.currentType = rootType;
    }

    /**
     * Creates Serializer for generation to String.
     *
     * @param bindingContext Current context.
     */
    public Serializer(JsonbContext bindingContext) {
        super(bindingContext);
        this.currentType = null;
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     *
     * @param value object to marshal
     * @param jsonWriter generator to use
     */
    public void marshal(Object value, JsonGenerator jsonWriter) {
        try {
            serializeRoot(value, jsonWriter);
        } catch (JsonbException ex) {
            SERIALIZER_LOG.severe(ex.getMessage());
            throw ex;
        } finally {
            try {
                jsonWriter.close();
            } catch (JsonGenerationException jsonGenEx) {
                SERIALIZER_LOG.severe(jsonGenEx.getMessage());
            }
        }
    }

    @Override
    public <T> void serialize(String propertyName, T value, JsonGenerator out) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(value);
        out.writeKey(propertyName);
        serializeRoot(value, out);
    }

    @Override
    public <T> void serialize(T value, JsonGenerator out) {
        Objects.requireNonNull(value);
        serializeRoot(value, out);
    }

    /**
     * Serializes root element.
     *
     * @param <T> Root type
     * @param rootValue Root.
     * @param out JSON generator.
     */
    @SuppressWarnings("unchecked")
    public <T> void serializeRoot(T rootValue, JsonGenerator out) {
        final JsonbSerializer<T> rootHandler = (JsonbSerializer<T>) getRootSerializer(rootValue.getClass());
        if (jsonbContext.getConfigProperties().isStrictIJson() &&
                rootHandler instanceof AbstractValueTypeSerializer) {
            throw new JsonbException(Messages.getMessage(MessageKeys.IJSON_ENABLED_SINGLE_VALUE));
        }
        rootHandler.serialize(rootValue, out, this);
    }

    private JsonbSerializer<?> getRootSerializer(Class<?> rootClass) {
        final ContainerSerializerProvider containerProvider = getMappingContext().getSerializerProvider(rootClass);
        if (containerProvider != null) {
            return containerProvider
                    .provideSerializer(new JsonbPropertyInfo()
                            .withRuntimeType(currentType));
        }
        SerializerBuilder builder = new SerializerBuilder(jsonbContext)
                .withObjectClass(rootClass)
                .withType(currentType);

        if (!DefaultSerializers.getInstance().isKnownType(rootClass)) {
            ClassModel typeModel = getMappingContext().getOrCreateClassModel(rootClass);
            builder.withCustomization(typeModel.getCustomization());
        }
        return builder.build();
    }

}

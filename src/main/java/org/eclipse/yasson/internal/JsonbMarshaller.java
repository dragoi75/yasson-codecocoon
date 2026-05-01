/*******************************************************************************
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved.
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
public class JsonbMarshaller extends ProcessingContext implements SerializationContext {

    private static final Logger LOG_OUTPUT = Logger.getLogger(JsonbMarshaller.class.getName());

    private final Type resolvedType;

    /**
     * Creates JsonbMarshaller for generation to String.
     *
     * @param bindingContext Current context.
     * @param rootType Type of root object.
     */
    public JsonbMarshaller(JsonbContext bindingContext, Type rootType) {
        super(bindingContext);
        this.resolvedType = rootType;
    }

    /**
     * Creates JsonbMarshaller for generation to String.
     *
     * @param bindingContext Current context.
     */
    public JsonbMarshaller(JsonbContext bindingContext) {
        super(bindingContext);
        this.resolvedType = null;
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     *
     * @param value object to marshal
     * @param gen generator to use
     * @param autoRelease if generator should be closed
     */
    public void marshal(Object value, JsonGenerator gen, boolean autoRelease) {
        try {
            serializeRoot(value, gen);
        } catch (JsonbException jsonbEx) {
            LOG_OUTPUT.severe(jsonbEx.getMessage());
            throw jsonbEx;
        } catch (Exception jsonbEx) {
            LOG_OUTPUT.severe(jsonbEx.getMessage());
            throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR, jsonbEx.getMessage()), jsonbEx);
        } finally {
            try {
                if (autoRelease) {
                    gen.close();
                }
            } catch (JsonGenerationException jsonGenerationEx) {
                LOG_OUTPUT.severe(jsonGenerationEx.getMessage());
            }
        }
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     * Closes the generator on completion.
     *
     * @param value object to marshal
     * @param gen generator to use
     */
    public void marshal(Object value, JsonGenerator gen) {
        marshal(value, gen,true);
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     * Leaves generator open for further interaction after completion.
     *
     * @param value object to marshal
     * @param gen generator to use
     */
    public void marshallNoClose(Object value, JsonGenerator gen) {
        marshal(value, gen,false);
    }

    @Override
    public <T> void serialize(String propertyName, T value, JsonGenerator gen) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(value);
        gen.writeKey(propertyName);
        serializeRoot(value, gen);
    }

    @Override
    public <T> void serialize(T value, JsonGenerator gen) {
        Objects.requireNonNull(value);
        serializeRoot(value, gen);
    }

    /**
     * Serializes root element.
     *
     * @param <T> Root type
     * @param rootValue Root.
     * @param gen JSON generator.
     */
    @SuppressWarnings("unchecked")
    public <T> void serializeRoot(T rootValue, JsonGenerator gen) {
        final JsonbSerializer<T> rootWriter = (JsonbSerializer<T>) getRootSerializer(rootValue.getClass());
        if (jsonbContext.getConfigProperties().isStrictIJson() &&
                rootWriter instanceof AbstractValueTypeSerializer) {
            throw new JsonbException(Messages.getMessage(MessageKeys.IJSON_ENABLED_SINGLE_VALUE));
        }
        rootWriter.serialize(rootValue, gen, this);
    }

    private JsonbSerializer<?> getRootSerializer(Class<?> rootClass) {
        final ContainerSerializerProvider containerProvider = getMappingContext().getSerializerProvider(rootClass);
        if (containerProvider != null) {
            return containerProvider
                    .provideSerializer(new JsonbPropertyInfo()
                            .withRuntimeType(resolvedType));
        }
        SerializerBuilder builder = new SerializerBuilder(jsonbContext)
                .withObjectClass(rootClass)
                .withType(resolvedType);

        if (!DefaultSerializers.getInstance().isKnownType(rootClass)) {
            ClassModel model = getMappingContext().getOrCreateClassModel(rootClass);
            builder.withCustomization(model.getCustomization());
        }
        return builder.build();
    }

}

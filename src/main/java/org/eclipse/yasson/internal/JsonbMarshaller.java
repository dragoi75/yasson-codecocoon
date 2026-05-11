/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  <p>
 *  Contributors:
 *      Dmitry Kornilov - initial implementation
 * ****************************************************************************
 */
package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.JsonbPropertyDescriptor;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;
import org.eclipse.yasson.internal.serializer.ConfigurableValueTypeSerializer;
import org.eclipse.yasson.internal.serializer.ContainerSerializerFactory;
import org.eclipse.yasson.internal.serializer.DefaultSerializerRegistry;
import org.eclipse.yasson.internal.serializer.SerializationBuilder;
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
public class JsonbMarshaller extends ProcessingEnvironment implements SerializationContext {

    private static final Logger JSONB_MARSHALLER_LOG = Logger.getLogger(JsonbMarshaller.class.getName());

    private final Type resolvedType;

    /**
     * Creates Marshaller for generation to String.
     *
     * @param jsonbRuntime Current context.
     * @param rootType Type of root object.
     */
    public JsonbMarshaller(JsonbRuntimeContext jsonbRuntime, Type rootType) {
        super(jsonbRuntime);
        this.resolvedType = rootType;
    }

    /**
     * Creates Marshaller for generation to String.
     *
     * @param jsonbRuntime Current context.
     */
    public JsonbMarshaller(JsonbRuntimeContext jsonbRuntime) {
        super(jsonbRuntime);
        this.resolvedType = null;
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     *
     * @param value object to marshall
     * @param generator generator to use
     * @param shouldClose if generator should be closed
     */
    public void marshal(Object value, JsonGenerator generator, boolean shouldClose) {
        try {
            serializeRootValue(value, generator);
        } catch (JsonbException jsonbException) {
            JSONB_MARSHALLER_LOG.severe(jsonbException.getMessage());
            throw jsonbException;
        } catch (Exception jsonbException) {
            JSONB_MARSHALLER_LOG.severe(jsonbException.getMessage());
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.INTERNAL_ERROR, jsonbException.getMessage()), jsonbException);
        } finally {
            try {
                if (shouldClose) {
                    generator.close();
                }
            } catch (JsonGenerationException generationException) {
                JSONB_MARSHALLER_LOG.severe(generationException.getMessage());
            }
        }
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     * Closes the generator on completion.
     *
     * @param value object to marshall
     * @param generator generator to use
     */
    public void marshal(Object value, JsonGenerator generator) {
        marshal(value, generator, true);
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     * Leaves generator open for further interaction after completion.
     *
     * @param value object to marshall
     * @param generator generator to use
     */
    public void marshalWithoutClose(Object value, JsonGenerator generator) {
        marshal(value, generator, false);
    }

    @Override
    public <T> void serialize(String propertyName, T value, JsonGenerator jsonGen) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(value);
        jsonGen.writeKey(propertyName);
        serializeRootValue(value, jsonGen);
    }

    @Override
    public <T> void serialize(T value, JsonGenerator jsonGen) {
        Objects.requireNonNull(value);
        serializeRootValue(value, jsonGen);
    }

    /**
     * Serializes root element.
     *
     * @param <T> Root type
     * @param rootValue Root.
     * @param jsonGen JSON generator.
     */
    @SuppressWarnings("unchecked")
    public <T> void serializeRootValue(T rootValue, JsonGenerator jsonGen) {
        final JsonbSerializer<T> resolvedSerializer = (JsonbSerializer<T>) getRootSerializer(rootValue.getClass());
        if (jsonbContext.getConfigProperties().isStrictIJson() && resolvedSerializer instanceof ConfigurableValueTypeSerializer) {
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.IJSON_ENABLED_SINGLE_VALUE));
        }
        resolvedSerializer.serialize(rootValue, jsonGen, this);
    }

    private JsonbSerializer<?> getRootSerializer(Class<?> rootClass) {
        final ContainerSerializerFactory serializerFactory = getMappingContext().getSerializerProvider(rootClass);
        if (null != serializerFactory) {
            return serializerFactory.createSerializer(new JsonbPropertyDescriptor().setRuntimeType(resolvedType));
        }
        SerializationBuilder serializationBuilder = new SerializationBuilder(jsonbContext).setObjectClass(rootClass).setType(resolvedType);
        if (!DefaultSerializerRegistry.getInstance().isKnownType(rootClass)) {
            ClassDescriptor classDescriptor = getMappingContext().getOrCreateClassModel(rootClass);
            serializationBuilder.setCustomization(classDescriptor.getCustomization());
        }
        return serializationBuilder.buildSerializer();
    }
}

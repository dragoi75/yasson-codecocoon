/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.serializer.ConfigurableValueTypeSerializer;
import org.eclipse.yasson.internal.serializer.ContainerSerializerProvider;
import org.eclipse.yasson.internal.serializer.DefaultSerializerRegistry;
import org.eclipse.yasson.internal.serializer.TypeSerializerBuilder;
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
public class Marshaller extends ProcessingContext implements SerializationContext {

    private static final Logger logger = Logger.getLogger(Marshaller.class.getName());

    private final Type runtimeType;

    /**
     * Serializes root element.
     *
     * @param <T> Root type
     * @param root Root.
     * @param generator JSON generator.
     */
    @SuppressWarnings("unchecked")
    public <T> void serializeRoot(T root, JsonGenerator generator) {
        final JsonbSerializer<T> rootSerializer = (JsonbSerializer<T>) getRootSerializer(root.getClass());
        if (jsonbContext.getConfigProperties().isStrictIJson() && rootSerializer instanceof ConfigurableValueTypeSerializer) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.IJSON_ENABLED_SINGLE_VALUE));
        }
        rootSerializer.serialize(root, generator, this);
    }

    @Override
    public <T> void serialize(T object, JsonGenerator generator) {
        Objects.requireNonNull(object);
        serializeRoot(object, generator);
    }

    private JsonbSerializer<?> getRootSerializer(Class<?> rootClazz) {
        final ContainerSerializerProvider serializerProvider = getMappingContext().getSerializerProvider(rootClazz);
        if (null != serializerProvider) {
            return serializerProvider.provideSerializer(new JsonbPropertyInfo().withRuntimeType(runtimeType));
        }
        TypeSerializerBuilder serializerBuilder = new TypeSerializerBuilder(jsonbContext).setObjectClass(rootClazz).setType(runtimeType);
        if (!DefaultSerializerRegistry.getInstance().isKnownType(rootClazz)) {
            ClassModel classModel = getMappingContext().getOrCreateClassModel(rootClazz);
            serializerBuilder.setCustomization(classModel.getCustomization());
        }
        return serializerBuilder.buildSerializer();
    }

    /**
     * Creates Marshaller for generation to String.
     *
     * @param jsonbContext Current context.
     * @param rootRuntimeType Type of root object.
     */
    public Marshaller(JsonbRuntimeContext jsonbContext, Type rootRuntimeType) {
        super(jsonbContext);
        this.runtimeType = rootRuntimeType;
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     *
     * @param object object to marshall
     * @param jsonGenerator generator to use
     */
    public void marshall(Object object, JsonGenerator jsonGenerator) {
        try {
            serializeRoot(object, jsonGenerator);
        } catch (JsonbException e) {
            logger.severe(e.getMessage());
            throw e;
        } finally {
            try {
                jsonGenerator.close();
            } catch (JsonGenerationException jge) {
                logger.severe(jge.getMessage());
            }
        }
    }

    @Override
    public <T> void serialize(String key, T object, JsonGenerator generator) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(object);
        generator.writeKey(key);
        serializeRoot(object, generator);
    }

    /**
     * Creates Marshaller for generation to String.
     *
     * @param jsonbContext Current context.
     */
    public Marshaller(JsonbRuntimeContext jsonbContext) {
        super(jsonbContext);
        this.runtimeType = null;
    }

}

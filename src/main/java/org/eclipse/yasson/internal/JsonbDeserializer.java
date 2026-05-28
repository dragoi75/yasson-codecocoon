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
package org.eclipse.yasson.internal;

import java.lang.reflect.Type;
import java.util.logging.Logger;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;
import org.eclipse.yasson.internal.serializer.JsonDeserializerBuilder;

/**
 * JSONB unmarshaller.
 * Uses {@link JsonParser} to navigate through json string.
 */
public class JsonbDeserializer extends ProcessingSessionContext implements DeserializationContext {

    private static final Logger JSONB_LOG = Logger.getLogger(JsonbDeserializer.class.getName());

    /**
     * Get root value event, either for new deserialization process, or deserialization sub-process invoked from
     * custom user deserializer.
     */
    private JsonParser.Event getRootEvent(JsonParser jsonReader) {
        JsonbRiEventParser.ParsingLevelContext levelContext = ((JsonbNavigator) jsonReader).getCurrentLevel();
        //Wrapper parser is at start
        if (null == levelContext.getParent()) {
            return jsonReader.next();
        }
        final JsonParser.Event finalEvent = levelContext.getLastEvent();
        return JsonParser.Event.KEY_NAME == finalEvent ? jsonReader.next() : finalEvent;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeElement(Type targetClass, JsonParser jsonReader) {
        try {
            JsonDeserializerBuilder builder = new JsonDeserializerBuilder(getJsonbContext()).setType(targetClass).setJsonValueType(getRootEvent(jsonReader));
            Class<?> rawClass = ReflectionHelper.getRawType(targetClass);
            ClassModel modelDescriptor = getMappingContext().getOrCreateClassModel(rawClass);
            builder.setCustomization(modelDescriptor.getClassCustomization());
            return (T) builder.buildDeserializer().deserialize(jsonReader, this, targetClass);
        } catch (JsonbException jsonbException) {
            JSONB_LOG.severe(jsonbException.getMessage());
            throw jsonbException;
        } catch (Exception jsonbException) {
            JSONB_LOG.severe(jsonbException.getMessage());
            throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR, jsonbException.getMessage()), jsonbException);
        }
    }

    /**
     * Creates instance of unmarshaller.
     *
     * @param runtimeContext context to use
     */
    public JsonbDeserializer(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
    }

    @Override
    public <T> T deserialize(Class<T> targetClass, JsonParser jsonReader) {
        return deserializeElement(targetClass, jsonReader);
    }

    @Override
    public <T> T deserialize(Type targetClass, JsonParser jsonReader) {
        return deserializeElement(targetClass, jsonReader);
    }

}

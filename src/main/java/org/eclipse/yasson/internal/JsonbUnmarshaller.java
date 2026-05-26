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
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.serializer.DefaultSerializerRegistry;
import org.eclipse.yasson.internal.serializer.JsonDeserializerBuilder;

/**
 * JSONB unmarshaller.
 * Uses {@link JsonParser} to navigate through json string.
 */
public class JsonbUnmarshaller extends ObjectProcessingContext implements DeserializationContext {

    private static final Logger JSONB_UNMARSHALLER_LOG = Logger.getLogger(JsonbUnmarshaller.class.getName());

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Type targetKind, JsonParser jsonReader) {
        try {
            JsonDeserializerBuilder deserializerFactory = new JsonDeserializerBuilder(getJsonbContext()).setType(targetKind).withJsonEvent(getRootEvent(jsonReader));
            Class<?> rawClass = ReflectionTypeResolver.getRawType(targetKind);
            if (!DefaultSerializerRegistry.getInstance().isKnownType(rawClass)) {
                ClassDescriptor classDescriptor = getMappingContext().getOrCreateClassModel(rawClass);
                deserializerFactory.setCustomization(classDescriptor.getClassCustomization());
            }
            return (T) deserializerFactory.buildDeserializer().deserialize(jsonReader, this, targetKind);
        } catch (JsonbException ex) {
            JSONB_UNMARSHALLER_LOG.severe(ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            JSONB_UNMARSHALLER_LOG.severe(ex.getMessage());
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INTERNAL_ERROR, ex.getMessage()), ex);
        }
    }

    /**
     * Get root value event, either for new deserialization process, or deserialization sub-process invoked from
     * custom user deserializer.
     */
    private JsonParser.Event getRootEvent(JsonParser jsonReader) {
        JsonbStreamingParser.LevelParseContext levelContext = ((JsonbNavigator) jsonReader).getCurrentLevel();
        //Wrapper parser is at start
        if (null == levelContext.getParent()) {
            return jsonReader.next();
        }
        final JsonParser.Event finalEvent = levelContext.getLastEvent();
        return JsonParser.Event.KEY_NAME == finalEvent ? jsonReader.next() : finalEvent;
    }

    @Override
    public <T> T deserialize(Type targetKind, JsonParser jsonReader) {
        return deserializeValue(targetKind, jsonReader);
    }

    /**
     * Creates instance of unmarshaller.
     *
     * @param runtimeContext context to use
     */
    public JsonbUnmarshaller(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
    }

    @Override
    public <T> T deserialize(Class<T> targetClass, JsonParser jsonReader) {
        return deserializeValue(targetClass, jsonReader);
    }

}

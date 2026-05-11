/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  <p>
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;
import org.eclipse.yasson.internal.serializer.DefaultSerializerRegistry;
import org.eclipse.yasson.internal.serializer.JsonValueDeserializerBuilder;
import javax.json.bind.JsonbException;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.util.logging.Logger;

/**
 * JSONB unmarshaller.
 * Uses {@link JsonParser} to navigate through json string.
 *
 * @author Roman Grigoriadi
 */
public class JsonbUnmarshaller extends ProcessingEnvironment implements DeserializationContext {

    private static final Logger JSONB_UNMARSHALLER_LOG = Logger.getLogger(JsonbUnmarshaller.class.getName());

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

    @Override
    public <T> T deserialize(Type targetSignature, JsonParser jsonReader) {
        return deserializeValue(targetSignature, jsonReader);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Type targetSignature, JsonParser jsonReader) {
        try {
            JsonValueDeserializerBuilder deserializerFactory = new JsonValueDeserializerBuilder(jsonbContext).setType(targetSignature).withJsonEvent(getRootEvent(jsonReader));
            Class<?> rawClass = ReflectiveTypeUtils.getRawType(targetSignature);
            if (!DefaultSerializerRegistry.getInstance().isKnownType(rawClass)) {
                ClassDescriptor classDescriptor = getMappingContext().getOrCreateClassModel(rawClass);
                deserializerFactory.setCustomization(classDescriptor.getCustomization());
            }
            return (T) deserializerFactory.buildDeserializer().deserialize(jsonReader, this, targetSignature);
        } catch (JsonbException jsonbException) {
            JSONB_UNMARSHALLER_LOG.severe(jsonbException.getMessage());
            throw jsonbException;
        } catch (Exception jsonbException) {
            JSONB_UNMARSHALLER_LOG.severe(jsonbException.getMessage());
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.INTERNAL_ERROR, jsonbException.getMessage()), jsonbException);
        }
    }

    /**
     * Get root value event, either for new deserialization process, or deserialization sub-process invoked from
     * custom user deserializer.
     */
    private JsonParser.Event getRootEvent(JsonParser jsonReader) {
        JsonbStreamingParser.LevelParseContext levelContext = ((JsonbCursor) jsonReader).getCurrentLevel();
        //Wrapper parser is at start
        if (null == levelContext.getParent()) {
            return jsonReader.next();
        }
        final JsonParser.Event finalEvent = levelContext.getLastEvent();
        return JsonParser.Event.KEY_NAME == finalEvent ? jsonReader.next() : finalEvent;
    }
}

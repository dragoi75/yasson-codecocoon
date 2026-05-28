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
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKey;
import org.eclipse.yasson.internal.serializer.DefaultSerializerRegistry;
import org.eclipse.yasson.internal.serializer.JsonDeserializerBuilder;
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
public class JsonbDeserializer extends ProcessingContext implements DeserializationContext {

    private static final Logger JSONB_LOG = Logger.getLogger(JsonbDeserializer.class.getName());

    @Override
    public <T> T deserialize(Type valueType, JsonParser jsonStream) {
        return deserializeValue(valueType, jsonStream);
    }

    /**
     * Get root value event, either for new deserialization process, or deserialization sub-process invoked from
     * custom user deserializer.
     */
    private JsonParser.Event getRootEvent(JsonParser jsonStream) {
        JsonbRiStreamParser.ParsingLevelContext levelContext = ((JsonbStructureNavigator) jsonStream).getCurrentLevel();
        //Wrapper parser is at start
        if (null == levelContext.getParent()) {
            return jsonStream.next();
        }
        final JsonParser.Event rootEvent = levelContext.getLastEvent();
        return JsonParser.Event.KEY_NAME == rootEvent ? jsonStream.next() : rootEvent;
    }

    @Override
    public <T> T deserialize(Class<T> targetClass, JsonParser jsonStream) {
        return deserializeValue(targetClass, jsonStream);
    }

    /**
     * Creates instance of unmarshaller.
     *
     * @param runtimeContext context to use
     */
    public JsonbDeserializer(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Type valueType, JsonParser jsonStream) {
        try {
            JsonDeserializerBuilder deserializerFactory = new JsonDeserializerBuilder(jsonbContext).setType(valueType).setJsonValueType(getRootEvent(jsonStream));
            Class<?> rawClass = ReflectionTypeResolver.getRawType(valueType);
            if (!DefaultSerializerRegistry.getInstance().isKnownType(rawClass)) {
                ClassDescriptor classDescriptor = getMappingContext().getOrCreateClassModel(rawClass);
                deserializerFactory.setCustomization(classDescriptor.getCustomization());
            }
            return (T) deserializerFactory.buildDeserializer().deserialize(jsonStream, this, valueType);
        } catch (JsonbException jsonbException) {
            JSONB_LOG.severe(jsonbException.getMessage());
            throw jsonbException;
        } catch (Exception jsonbException) {
            JSONB_LOG.severe(jsonbException.getMessage());
            throw new JsonbException(MessageBundle.getMessage(MessageKey.INTERNAL_ERROR, jsonbException.getMessage()), jsonbException);
        }
    }

}

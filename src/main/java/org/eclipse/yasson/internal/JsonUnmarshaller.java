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

import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;
import org.eclipse.yasson.internal.serializer.DefaultSerializers;
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
public class JsonUnmarshaller extends ObjectProcessingContext implements DeserializationContext {

    private static final Logger JSONB_UNMARSHALLER_LOG = Logger.getLogger(JsonUnmarshaller.class.getName());

    /**
     * Creates instance of unmarshaller.
     *
     * @param runtimeContext context to use
     */
    public JsonUnmarshaller(JsonbRuntimeContext runtimeContext) {
        super(runtimeContext);
    }

    @Override
    public <T> T deserialize(Class<T> targetClass, JsonParser jsonReader) {
        return unmarshalItem(targetClass, jsonReader);
    }

    @Override
    public <T> T deserialize(Type targetType, JsonParser jsonReader) {
        return unmarshalItem(targetType, jsonReader);
    }

    @SuppressWarnings("unchecked")
    private <T> T unmarshalItem(Type targetType, JsonParser jsonReader) {
        try {
            JsonDeserializerBuilder deserializerFactory = new JsonDeserializerBuilder(jsonbContext).setType(targetType).setJsonValueType(getRootEvent(jsonReader));
            Class<?> rawClass = ReflectionTypeUtils.getRawType(targetType);
            if (!DefaultSerializers.getInstance().isKnownType(rawClass)) {
                ClassModel typeModel = getMappingContext().getOrCreateClassModel(rawClass);
                deserializerFactory.setCustomization(typeModel.getCustomization());
            }
            return (T) deserializerFactory.buildDeserializer().deserialize(jsonReader, this, targetType);
        } catch (JsonbException jsonbException) {
            JSONB_UNMARSHALLER_LOG.severe(jsonbException.getMessage());
            throw jsonbException;
        } catch (Exception jsonbException) {
            JSONB_UNMARSHALLER_LOG.severe(jsonbException.getMessage());
            throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR, jsonbException.getMessage()), jsonbException);
        }
    }

    /**
     * Get root value event, either for new deserialization process, or deserialization sub-process invoked from
     * custom user deserializer.
     */
    private JsonParser.Event getRootEvent(JsonParser jsonReader) {
        JsonbRiEventParser.LevelParseContext levelContext = ((JsonbNavigator) jsonReader).getCurrentLevel();
        //Wrapper parser is at start
        if (null == levelContext.getParent()) {
            return jsonReader.next();
        }
        final JsonParser.Event terminalEvent = levelContext.getLastEvent();
        return JsonParser.Event.KEY_NAME == terminalEvent ? jsonReader.next() : terminalEvent;
    }
}

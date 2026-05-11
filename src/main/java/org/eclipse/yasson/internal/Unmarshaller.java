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
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.Messages;
import org.eclipse.yasson.internal.serializer.DefaultSerializers;
import org.eclipse.yasson.internal.serializer.DeserializerBuilder;
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
public class Unmarshaller extends ObjectProcessingContext implements DeserializationContext {

    private static final Logger logger = Logger.getLogger(Unmarshaller.class.getName());

    /**
     * Creates instance of unmarshaller.
     *
     * @param jsonbContext context to use
     */
    public Unmarshaller(JsonbContext jsonbContext) {
        super(jsonbContext);
    }

    @Override
    public <T> T deserialize(Class<T> clazz, JsonParser parser) {
        return deserializeItem(clazz, parser);
    }

    @Override
    public <T> T deserialize(Type type, JsonParser parser) {
        return deserializeItem(type, parser);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeItem(Type type, JsonParser parser) {
        try {
            DeserializerBuilder deserializerBuilder = new DeserializerBuilder(jsonbContext).setType(type).withJsonValueType(getRootEvent(parser));
            Class<?> rawType = ReflectiveTypeResolver.getRawType(type);
            if (!DefaultSerializers.getInstance().isKnownType(rawType)) {
                ClassDescriptor classModel = getMappingContext().getOrCreateClassModel(rawType);
                deserializerBuilder.setCustomization(classModel.getCustomization());
            }
            return (T) deserializerBuilder.build().deserialize(parser, this, type);
        } catch (JsonbException e) {
            logger.severe(e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.severe(e.getMessage());
            throw new JsonbException(Messages.getMessage(MessageKeyConstants.INTERNAL_ERROR, e.getMessage()), e);
        }
    }

    /**
     * Get root value event, either for new deserialization process, or deserialization sub-process invoked from
     * custom user deserializer.
     */
    private JsonParser.Event getRootEvent(JsonParser parser) {
        JsonbRiParser.LevelContext currentLevel = ((JsonbParser) parser).getCurrentLevel();
        //Wrapper parser is at start
        if (null == currentLevel.getParent()) {
            return parser.next();
        }
        final JsonParser.Event lastEvent = currentLevel.getLastEvent();
        return JsonParser.Event.KEY_NAME == lastEvent ? parser.next() : lastEvent;
    }
}

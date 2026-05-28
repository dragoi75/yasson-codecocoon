/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
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
import org.eclipse.yasson.internal.serializer.DefaultSerializerRegistry;
import org.eclipse.yasson.internal.serializer.JsonbDeserializerBuilder;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;

/**
 * JSONB unmarshaller.
 * Uses {@link JsonParser} to navigate through json string.
 *
 * @author Roman Grigoriadi
 */
public class Unmarshaller extends ProcessingContext implements DeserializationContext {

    @SuppressWarnings("unchecked")
    private <T> T deserializeItem(Type type, JsonParser parser) {
        JsonbDeserializerBuilder deserializerBuilder = new JsonbDeserializerBuilder(jsonbContext).setType(type).setJsonValueType(getRootEvent(parser));
        Class<?> rawType = ReflectionTypeResolver.getRawType(type);
        if (!DefaultSerializerRegistry.getInstance().isKnownType(rawType)) {
            ClassModel classModel = getMappingContext().getOrCreateClassModel(rawType);
            deserializerBuilder.setCustomization(classModel.getCustomization());
        }
        return (T) deserializerBuilder.buildDeserializer().deserialize(parser, this, type);
    }

    @Override
    public <T> T deserialize(Class<T> clazz, JsonParser parser) {
        return deserializeItem(clazz, parser);
    }

    /**
     * Get root value event, either for new deserialization process, or deserialization sub-process invoked from
     * custom user deserializer.
     */
    private JsonParser.Event getRootEvent(JsonParser parser) {
        if (0 == parser.getLocation().getStreamOffset()) {
            return parser.next();
        }
        final JsonParser.Event lastEvent = ((JsonbParser) parser).getCurrentLevel().getLastEvent();
        return JsonParser.Event.KEY_NAME == lastEvent ? parser.next() : lastEvent;
    }

    /**
     * Creates instance of unmarshaller.
     *
     * @param jsonbContext context to use
     */
    public Unmarshaller(JsonbRuntimeContext jsonbContext) {
        super(jsonbContext);
    }

    @Override
    public <T> T deserialize(Type type, JsonParser parser) {
        return deserializeItem(type, parser);
    }

}

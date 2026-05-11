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

import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.serializer.DefaultSerializerProvider;
import org.eclipse.yasson.internal.serializer.DeserializationBuilder;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;

/**
 * JSONB unmarshaller.
 * Uses {@link JsonParser} to navigate through json string.
 *
 * @author Roman Grigoriadi
 */
public class JsonUnmarshaller extends ProcessingContextManager implements DeserializationContext {

    /**
     * Creates instance of unmarshaller.
     *
     * @param configurationContext context to use
     */
    public JsonUnmarshaller(JsonbConfigurationContext configurationContext) {
        super(configurationContext);
    }

    @Override
    public <T> T deserialize(Class<T> targetClass, JsonParser jsonReader) {
        return deserializeValue(targetClass, jsonReader);
    }

    @Override
    public <T> T deserialize(Type targetGeneric, JsonParser jsonReader) {
        return deserializeValue(targetGeneric, jsonReader);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Type targetGeneric, JsonParser jsonReader) {
        DeserializationBuilder deserializationBuilder = new DeserializationBuilder(jsonbContext).setType(targetGeneric).withJsonEvent(getRootEvent(jsonReader));
        Class<?> rawClass = ReflectionHelper.getRawType(targetGeneric);
        if (!DefaultSerializerProvider.getInstance().isKnownType(rawClass)) {
            ClassDescriptor classDescriptor = getMappingContext().getOrCreateClassModel(rawClass);
            deserializationBuilder.setCustomization(classDescriptor.getCustomization());
        }
        return (T) deserializationBuilder.buildDeserializer().deserialize(jsonReader, this, targetGeneric);
    }

    /**
     * Get root value event, either for new deserialization process, or deserialization sub-process invoked from
     * custom user deserializer.
     */
    private JsonParser.Event getRootEvent(JsonParser jsonReader) {
        if (0 == jsonReader.getLocation().getStreamOffset()) {
            return jsonReader.next();
        }
        final JsonParser.Event terminalEvent = ((JsonbStreamParser) jsonReader).getCurrentLevel().getLastEvent();
        return JsonParser.Event.KEY_NAME == terminalEvent ? jsonReader.next() : terminalEvent;
    }
}

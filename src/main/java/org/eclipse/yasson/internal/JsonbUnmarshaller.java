/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 * <p>
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/
package org.eclipse.yasson.internal;


import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.serializer.DefaultSerializers;
import org.eclipse.yasson.internal.serializer.DeserializerBuilder;

import javax.json.bind.serializer.DeserializationContext;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;

/**
 * JSONB unmarshaller.
 * Uses {@link JsonParser} to navigate through json string.
 *
 * @author Roman Grigoriadi
 */
public class JsonbUnmarshaller extends ProcessingContext implements DeserializationContext {

    /**
     * Creates instance of unmarshaller.
     *
     * @param bindingContext context to use
     */
    public JsonbUnmarshaller(JsonbContext bindingContext) {
        super(bindingContext);
    }

    @Override
    public <T> T deserialize(Class<T> targetClass, JsonParser jsonStream) {
        return deserializeItem(targetClass, jsonStream);
    }

    @Override
    public <T> T deserialize(Type valueType, JsonParser jsonStream) {
        return deserializeItem(valueType, jsonStream);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeItem(Type valueType, JsonParser jsonStream) {
        DeserializerBuilder unmarshallerBuilder = new DeserializerBuilder(jsonbContext)
                .withType(valueType).withJsonValueType(getRootEvent(jsonStream));
        Class<?> rawClass = ReflectionUtils.getRawType(valueType);
        if (!DefaultSerializers.getInstance().isKnownType(rawClass)) {
            ClassModel typeModel = getMappingContext().getOrCreateClassModel(rawClass);
            unmarshallerBuilder.withCustomization(typeModel.getCustomization());
        }

        return (T) unmarshallerBuilder.build().deserialize(jsonStream, this, valueType);
    }

    /**
     * Get root value event, either for new deserialization process, or deserialization sub-process invoked from
     * custom user deserializer.
     */
    private JsonParser.Event getRootEvent(JsonParser jsonStream) {
        if (jsonStream.getLocation().getStreamOffset() == 0) {
            return jsonStream.next();
        }
        final JsonParser.Event rootEvent = ((JsonbParser) jsonStream).getCurrentLevel().getLastEvent();
        return rootEvent == JsonParser.Event.KEY_NAME ? jsonStream.next() : rootEvent;
    }

}

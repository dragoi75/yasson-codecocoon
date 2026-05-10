/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonbCursor;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;

import javax.json.bind.JsonbException;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.util.OptionalDouble;

/**
 * Deserializer for {@link OptionalDouble} type.
 * 
 * @author David Kral
 */
public class OptionalDoubleDeserializer extends BaseValueTypeDeserializer<OptionalDouble> {

    /**
     * Creates a new instance.
     *
     * @param serializationConfig Model customization.
     */
    public OptionalDoubleDeserializer(SerializationCustomization serializationConfig) {
        super(OptionalDouble.class, serializationConfig);
    }

    @Override
    public OptionalDouble deserialize(JsonParser jsonReader, DeserializationContext deserializationContext, Type runtimeType) {
        final JsonParser.Event currentEvent = ((JsonbCursor) jsonReader).moveToValue();
        if (currentEvent == JsonParser.Event.VALUE_NULL) {
            return OptionalDouble.empty();
        }
        String text = jsonReader.getString();
        return deserializeValue(text, (JsonbUnmarshaller) deserializationContext, runtimeType);
    }

    @Override
    protected OptionalDouble deserializeValue(String jsonString, JsonbUnmarshaller unmarshaller, Type rtType) {
        try {
            return OptionalDouble.of(Double.parseDouble(jsonString));
        } catch (NumberFormatException e) {
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.DESERIALIZE_VALUE_ERROR, OptionalDouble.class));
        }
    }
}

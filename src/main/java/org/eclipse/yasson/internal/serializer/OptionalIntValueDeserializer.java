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
package org.eclipse.yasson.internal.serializer;

import java.lang.reflect.Type;
import java.util.OptionalInt;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer for {@link OptionalInt} type.
 */
public class OptionalIntValueDeserializer extends AbstractValueTypeDeserializer<OptionalInt> {

    /**
     * Creates a new instance.
     *
     * @param configOptions Model customization.
     */
    public OptionalIntValueDeserializer(Customization configOptions) {
        super(OptionalInt.class, configOptions);
    }

    @Override
    public OptionalInt deserialize(JsonParser jsonReader, DeserializationContext deserializationContext, Type runtimeType) {
        final JsonParser.Event currentEvent = ((JsonbParser) jsonReader).moveToValue();
        if (JsonParser.Event.VALUE_NULL == currentEvent) {
            return OptionalInt.empty();
        }
        final String textContent = jsonReader.getString();
        return deserialize(textContent, (Unmarshaller) deserializationContext, runtimeType);
    }

    @Override
    protected OptionalInt deserialize(String jsonText, Unmarshaller unmarshaller, Type rtType) {
        try {
            return OptionalInt.of(Integer.parseInt(jsonText));
        } catch (NumberFormatException e) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR, OptionalInt.class));
        }
    }
}

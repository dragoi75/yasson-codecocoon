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

import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.JsonbRiParser;
import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer for {@link JsonValue} containing null, false, true, string and number.
 */
public class JsonValueDecoder extends AbstractValueTypeDeserializer<JsonValue> {

    @Override
    public JsonValue deserialize(JsonParser jsonReader, DeserializationContext ctx, Type rtType) {
        final JsonParser.Event followingEvent = ((JsonbRiParser) jsonReader).getLastEvent();
        switch (followingEvent) {
        case VALUE_TRUE:
            return JsonValue.TRUE;
        case VALUE_FALSE:
            return JsonValue.FALSE;
        case VALUE_NULL:
            return JsonValue.NULL;
        case VALUE_STRING:
        case VALUE_NUMBER:
            return jsonReader.getValue();
        default:
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Unknown JSON value: " + followingEvent));
        }
    }

    @Override
    protected JsonValue deserialize(String jsonValue, Unmarshaller unmarshaller, Type rtType) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public JsonValueDecoder(Customization customConfig) {
        super(JsonValue.class, customConfig);
    }

}

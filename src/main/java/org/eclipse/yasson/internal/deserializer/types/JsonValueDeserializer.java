/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.deserializer.types;

import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DefaultDeserializationContext;
import org.eclipse.yasson.internal.deserializer.ModelParser;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * Deserializer of the {@link JsonValue} type.
 */
class JsonValueDeserializer implements ModelParser<JsonParser> {

    private final ModelParser<Object> delegate;

    @Override
    public Object deserializeModel(JsonParser value, DefaultDeserializationContext context) {
        JsonParser.Event last = context.getLastValueEvent();
        return delegate.deserializeModel(deserializeValue(last, value), context);
    }

    private JsonValue deserializeValue(JsonParser.Event last, JsonParser parser) {
        switch (last) {
        case VALUE_TRUE:
            return JsonValue.TRUE;
        case VALUE_FALSE:
            return JsonValue.FALSE;
        case VALUE_NULL:
            return JsonValue.NULL;
        case VALUE_STRING:
        case VALUE_NUMBER:
            return parser.getValue();
        case START_OBJECT:
            return parser.getObject();
        case START_ARRAY:
            return parser.getArray();
        default:
            throw new JsonbException(MessageProvider.getMessage(MessageConstants.INTERNAL_ERROR, "Unknown JSON value: " + last));
        }
    }

    JsonValueDeserializer(TypeDeserializerBuilder builder) {
        delegate = builder.getDelegate();
    }

}

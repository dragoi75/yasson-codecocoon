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

import org.eclipse.yasson.internal.DeserializationContextImplementation;
import org.eclipse.yasson.internal.deserializer.ModelUnmarshaller;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer of the {@link JsonValue} type.
 */
class JsonValueDeserializer implements ModelUnmarshaller<JsonParser> {

    private final ModelUnmarshaller<Object> delegate;
    private final JsonValue nullValue;

    private JsonValue deserializeValue(JsonParser.Event last, JsonParser parser) {
        switch (last) {
        case VALUE_TRUE:
            return JsonValue.TRUE;
        case VALUE_FALSE:
            return JsonValue.FALSE;
        case VALUE_NULL:
            return nullValue;
        case VALUE_STRING:
        case VALUE_NUMBER:
            return parser.getValue();
        case START_OBJECT:
            return parser.getObject();
        case START_ARRAY:
            return parser.getArray();
        default:
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Unknown JSON value: " + last));
        }
    }

    @Override
    public Object unmarshal(JsonParser value, DeserializationContextImplementation context) {
        JsonParser.Event last = context.getLastValueEvent();
        return delegate.unmarshal(deserializeValue(last, value), context);
    }

    JsonValueDeserializer(TypeDeserializerBuilder builder, JsonValue nullValue) {
        this.delegate = builder.getDelegate();
        this.nullValue = nullValue;
    }

}

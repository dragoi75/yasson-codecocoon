/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.jsonstructure;

import java.util.Iterator;

import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Iterates over {@link jakarta.json.JsonStructure}.
 */
abstract class JsonStructureWalker implements Iterator<JsonParser.Event> {

    /**
     * Check the type of current  {@link JsonValue} and return a string representing a value.
     *
     * @return String value for current JsonValue
     */
    String getString() {
        JsonValue stringElement = getValue();
        if (stringElement instanceof JsonString) {
            return ((JsonString) stringElement).getString();
        } else {
            return stringElement.toString();
        }
    }

    /**
     * Convert {@link JsonValue} type to {@link JsonParser.Event}.
     *
     * @param stringElement JsonValue
     * @return JsonParser event
     */
    JsonParser.Event getValueEvent(JsonValue stringElement) {
        switch (stringElement.getValueType()) {
        case NUMBER:
            return JsonParser.Event.VALUE_NUMBER;
        case STRING:
        case TRUE:
        case FALSE:
            return JsonParser.Event.VALUE_STRING;
        case OBJECT:
            return JsonParser.Event.START_OBJECT;
        case ARRAY:
            return JsonParser.Event.START_ARRAY;
        case NULL:
            return JsonParser.Event.VALUE_NULL;
        default:
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INTERNAL_ERROR,
                                                         "unknown json value: " + stringElement.getValueType()));
        }

    }

    /**
     * Get current {@link JsonValue}, that the parser is pointing on.
     *
     * @return JsonValue result.
     */
    abstract JsonValue getValue();

    /**
     * Creates an exception for throwing in case of current value type is not compatible with
     * called getter return type.
     *
     * @return JsonbException with error description.
     */
    abstract JsonbException createIncompatibleValueException();

}

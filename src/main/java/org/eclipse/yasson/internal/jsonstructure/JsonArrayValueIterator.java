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

import jakarta.json.JsonArray;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Iterates over {@link JsonArray}.
 */
public class JsonArrayValueIterator extends JsonStructureWalker {

    private final Iterator<JsonValue> elementIterator;

    private JsonValue currentElement;

    @Override
    JsonValue getValue() {
        return currentElement;
    }

    @Override
    JsonbException createIncompatibleValueException() {
        return new JsonbException(MessageBundle.getMessage(MessageKeyConstants.NUMBER_INCOMPATIBLE_VALUE_TYPE_ARRAY,
                                                      getValue().getValueType()));
    }

    /**
     * After {@link JsonParser.Event} END_ARRAY is returned from next() iterator is removed from the stack.
     *
     * @return always true
     */
    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    String getString() {
        if (currentElement instanceof JsonString) {
            return ((JsonString) currentElement).getString();
        }
        return currentElement.toString();
    }

    /**
     * Creates new array iterator.
     *
     * @param arrayNode json array
     */
    public JsonArrayValueIterator(JsonArray arrayNode) {
        this.elementIterator = arrayNode.iterator();
    }

    @Override
    public JsonParser.Event next() {
        if (elementIterator.hasNext()) {
            currentElement = elementIterator.next();
            return getValueEvent(currentElement);
        }
        return JsonParser.Event.END_ARRAY;
    }

}

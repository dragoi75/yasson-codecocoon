/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;

/**
 * Adapter for {@link JsonParser}, that reads a {@link JsonStructure} content tree instead of JSON text.
 *
 * Yasson and jsonb API components are using {@link JsonParser} as its input API.
 * This adapter allows deserialization of {@link JsonStructure} into java content tree using same components
 * as when parsing JSON text.
 */
public class JsonStructureParserAdapter implements JsonParser {

    private Deque<JsonStructureIterator> cursorStack = new ArrayDeque<>();

    private final JsonStructure rootNode;

    /**
     * Creates new {@link JsonStructure} parser.
     *
     * @param inputNode json structure
     */
    public JsonStructureParserAdapter(JsonStructure inputNode) {
        this.rootNode = inputNode;
    }

    @Override
    public boolean hasNext() {
        return cursorStack.peek().hasNext();
    }

    @Override
    public Event next() {
        if (cursorStack.isEmpty()) {
            if (!(rootNode instanceof JsonObject)) {
                if (rootNode instanceof JsonArray) {
                    cursorStack.push(new JsonArrayIterator((JsonArray) rootNode));
                    return Event.START_ARRAY;
                }
            } else {
                cursorStack.push(new JsonObjectIterator((JsonObject) rootNode));
                return Event.START_OBJECT;
            }
        }
        JsonStructureIterator activeCursor = cursorStack.peek();
        Event upcomingEvent = activeCursor.next();
        if (Event.START_OBJECT != upcomingEvent) {
            if (Event.START_ARRAY != upcomingEvent) {
                if (Event.END_OBJECT == upcomingEvent || Event.END_ARRAY == upcomingEvent) {
                    cursorStack.pop();
                }
            } else {
                cursorStack.push(new JsonArrayIterator((JsonArray) cursorStack.peek().getValue()));
            }
        } else {
            cursorStack.push(new JsonObjectIterator((JsonObject) cursorStack.peek().getValue()));
        }
        return upcomingEvent;
    }

    @Override
    public String getString() {
        return cursorStack.peek().getString();
    }

    @Override
    public boolean isIntegralNumber() {
        return getJsonNumberValue().isIntegral();
    }

    @Override
    public int getInt() {
        return getJsonNumberValue().intValueExact();
    }

    @Override
    public long getLong() {
        return getJsonNumberValue().longValueExact();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return getJsonNumberValue().bigDecimalValue();
    }

    @Override
    public JsonObject getObject() {
        //        ((JsonObjectIterator) iterators.peek()).jsonObject
        return cursorStack.peek().getValue().asJsonObject();
    }

    private JsonNumber getJsonNumberValue() {
        JsonStructureIterator structureCursor = cursorStack.peek();
        JsonValue jsonElement = structureCursor.getValue();
        if (JsonValue.ValueType.NUMBER != jsonElement.getValueType()) {
            throw structureCursor.createIncompatibleValueError();
        }
        return (JsonNumber) jsonElement;
    }

    @Override
    public JsonLocation getLocation() {
        throw new JsonbException("Operation not supported");
    }

    @Override
    public void close() {
        //noop
    }
}

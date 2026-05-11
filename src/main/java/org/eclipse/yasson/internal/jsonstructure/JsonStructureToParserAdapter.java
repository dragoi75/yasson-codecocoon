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
public class JsonStructureToParserAdapter implements JsonParser {

    private Deque<JsonStructureIterator> iterators = new ArrayDeque<>();

    private final JsonStructure rootStructure;

    /**
     * Creates new {@link JsonStructure} parser.
     *
     * @param structure json structure
     */
    public JsonStructureToParserAdapter(JsonStructure structure) {
        this.rootStructure = structure;
    }

    @Override
    public boolean hasNext() {
        return iterators.peek().hasNext();
    }

    @Override
    public Event next() {
        if (iterators.isEmpty()) {
            if (!(rootStructure instanceof JsonObject)) {
                if (rootStructure instanceof JsonArray) {
                    iterators.push(new JsonArrayIterator((JsonArray) rootStructure));
                    return Event.START_ARRAY;
                }
            } else {
                iterators.push(new JsonObjectIterator((JsonObject) rootStructure));
                return Event.START_OBJECT;
            }
        }
        JsonStructureIterator current = iterators.peek();
        Event next = current.next();
        if (Event.START_OBJECT != next) {
            if (Event.START_ARRAY != next) {
                if (Event.END_OBJECT == next || Event.END_ARRAY == next) {
                    iterators.pop();
                }
            } else {
                iterators.push(new JsonArrayIterator((JsonArray) iterators.peek().getValue()));
            }
        } else {
            iterators.push(new JsonObjectIterator((JsonObject) iterators.peek().getValue()));
        }
        return next;
    }

    @Override
    public String getString() {
        return iterators.peek().getString();
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

    private JsonNumber getJsonNumberValue() {
        JsonStructureIterator iterator = iterators.peek();
        JsonValue value = iterator.getValue();
        if (JsonValue.ValueType.NUMBER != value.getValueType()) {
            throw iterator.createIncompatibleValueError();
        }
        return (JsonNumber) value;
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

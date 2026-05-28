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

package org.eclipse.yasson.internal;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;

/**
 * Decorator for JSONP parser. Adds some checks for parser cursor manipulation methods.
 */
public class UserDeserializerParser implements JsonbNavigator {

    private final JsonbNavigator jsonbParser;

    /**
     * Remembered parser level, which is applied to user deserializer structure.
     */
    private final JsonbRiEventParser.ParsingLevelContext level;

    @Override
    public int getInt() {
        return jsonbParser.getInt();
    }

    @Override
    public JsonObject getObject() {
        return jsonbParser.getObject();
    }

    @Override
    public Stream<JsonValue> getArrayStream() {
        return jsonbParser.getArrayStream();
    }

    /**
     * Skips a value or a structure.
     * If current event is START_ARRAY or START_OBJECT, whole structure is skipped to end.
     */
    @Override
    public void skipJsonStructure() {
        jsonbParser.skipJsonStructure();
    }

    /**
     * Current level of JsonbRiParser.
     *
     * @return current level
     */
    @Override
    public JsonbRiEventParser.ParsingLevelContext getCurrentLevel() {
        return jsonbParser.getCurrentLevel();
    }

    /**
     * Moves parser cursor to START_OBJECT or START_ARRAY.
     */
    @Override
    public Event moveToStartStructure() {
        return jsonbParser.moveToStartStructure();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return jsonbParser.getBigDecimal();
    }

    @Override
    public boolean hasNext() {
        return !level.isParsed() && jsonbParser.hasNext();
    }

    @Override
    public void skipObject() {
        jsonbParser.skipObject();
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        return jsonbParser.getValueStream();
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }

    /**
     * Moves parser to required event, if current event is equal to required does nothing.
     *
     * @param event required event
     */
    @Override
    public void moveTo(Event event) {
        jsonbParser.moveTo(event);
    }

    @Override
    public JsonValue getValue() {
        return jsonbParser.getValue();
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return jsonbParser.getObjectStream();
    }

    /**
     * Moves parser cursor to any JSON value.
     */
    @Override
    public Event moveToValue() {
        return jsonbParser.moveToValue();
    }

    @Override
    public long getLong() {
        return jsonbParser.getLong();
    }

    /**
     * Constructs an instance with parser and context.
     *
     * @param parser jsonb parser to decorate
     */
    public UserDeserializerParser(JsonbNavigator parser) {
        this.jsonbParser = parser;
        level = jsonbParser.getCurrentLevel();
    }

    @Override
    public void skipArray() {
        jsonbParser.skipArray();
    }

    @Override
    public JsonLocation getLocation() {
        return jsonbParser.getLocation();
    }

    @Override
    public Event next() {
        if (level.isParsed()) {
            throw new IllegalStateException("Parser level data inconsistent.");
        }
        return jsonbParser.next();
    }

    @Override
    public boolean isIntegralNumber() {
        return jsonbParser.isIntegralNumber();
    }

    @Override
    public JsonArray getArray() {
        return jsonbParser.getArray();
    }

    /**
     * JsonParser in JSONB runtime is shared with user components, if user lefts cursor half way in progress
     * it must be advanced artificially to the end of JSON structure representing deserialized object.
     */
    public void advanceParserToEnd() {
        while (!level.isParsed() && jsonbParser.hasNext()) {
            next();
        }
    }

    @Override
    public String getString() {
        return jsonbParser.getString();
    }

}

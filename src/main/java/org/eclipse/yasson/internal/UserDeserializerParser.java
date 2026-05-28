/*******************************************************************************
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved.
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

package org.eclipse.yasson.internal;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.stream.JsonLocation;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Decorator for JSONP parser. Adds some checks for parser cursor manipulation methods.
 *
 * @author Roman Grigoriadi
 */
public class UserDeserializerParser implements JsonbCursor {

    private final JsonbCursor jsonbParser;

    /**
     * Remembered parser level, which is applied to user deserializer structure.
     */
    private final JsonbStreamingParser.LevelParseContext level;


    @Override
    public Stream<JsonValue> getArrayStream() {
        return jsonbParser.getArrayStream();
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        return jsonbParser.getValueStream();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return jsonbParser.getBigDecimal();
    }

    @Override
    public String getString() {
        return jsonbParser.getString();
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return jsonbParser.getObjectStream();
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
    public JsonValue getValue() {
        return jsonbParser.getValue();
    }

    @Override
    public JsonArray getArray() {
        return jsonbParser.getArray();
    }

    @Override
    public JsonObject getObject() {
        return jsonbParser.getObject();
    }

    @Override
    public void skipArray() {
        jsonbParser.skipArray();
    }

    @Override
    public JsonLocation getLocation() {
        return jsonbParser.getLocation();
    }

    /**
     * Constructs an instance with parser and context.
     * @param parser jsonb parser to decorate
     */
    public UserDeserializerParser(JsonbCursor parser) {
        this.jsonbParser = parser;
        level = jsonbParser.getCurrentLevel();
    }

    /**
     * Moves parser cursor to any JSON value.
     */
    @Override
    public Event moveToValue() {
        return jsonbParser.moveToValue();
    }

    @Override
    public void skipObject() {
        jsonbParser.skipObject();
    }

    @Override
    public Event next() {
        if (level.isParsed()) {
            throw new IllegalStateException("Parser level data inconsistent.");
        }
        return jsonbParser.next();
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
    public boolean hasNext() {
        return !level.isParsed() && jsonbParser.hasNext();
    }

    /**
     * Moves parser cursor to START_OBJECT or START_ARRAY.
     */
    @Override
    public Event moveToStartStructure() {
        return jsonbParser.moveToStartStructure();
    }

    @Override
    public long getLong() {
        return jsonbParser.getLong();
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getInt() {
        return jsonbParser.getInt();
    }

    /**
     * Current level of JsonbRiParser.
     *
     * @return current level
     */
    @Override
    public JsonbStreamingParser.LevelParseContext getCurrentLevel() {
        return jsonbParser.getCurrentLevel();
    }

    @Override
    public boolean isIntegralNumber() {
        return jsonbParser.isIntegralNumber();
    }

    /**
     * Skips a value or a structure.
     * If current event is START_ARRAY or START_OBJECT, whole structure is skipped to end.
     */
    @Override
    public void skipJsonStructure() {
        jsonbParser.skipJsonStructure();
    }

}

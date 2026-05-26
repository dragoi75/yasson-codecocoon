/*
 * Copyright (c) 2016, 2021 Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.properties.ErrorMessageKeys;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Decorator for JSONP parser used by JSONB.
 */
public class JsonbRiParser implements JsonParser, JsonbParser {

    /**
     * State holder for current json structure level.
     */
    public static class LevelContext {

        private final LevelContext parent;

        private JsonParser.Event lastEvent;

        private String lastKeyName;

        private boolean parsed;

        /**
         * Getter for parsed property.
         *
         * @return True or false.
         */
        public boolean isParsed() {
            return parsed;
        }

        private void finish() {
            if (parsed) {
                throw new IllegalStateException("Level already parsed");
            }
            parsed = true;
        }

        /**
         * Get parent.
         *
         * @return Parent.
         */
        public LevelContext getParent() {
            return parent;
        }

        /**
         * Creates an instance.
         *
         * @param parent Parent context.
         */
        public LevelContext(LevelContext parent) {
            this.parent = parent;
        }

        private void setLastEvent(Event lastEvent) {
            this.lastEvent = lastEvent;
        }

        /**
         * Gets last event.
         *
         * @return Last event.
         */
        public Event getLastEvent() {
            return lastEvent;
        }

        /**
         * Gets last key name.
         *
         * @return Last key name.
         */
        public String getLastKeyName() {
            return lastKeyName;
        }

        private void setLastKeyName(String lastKeyName) {
            Objects.requireNonNull(lastKeyName);
            this.lastKeyName = lastKeyName;
        }

    }

    private final JsonParser jsonParser;

    private final Deque<LevelContext> level = new ArrayDeque<>();

    private Event moveTo(Event... events) {
        if (!level.isEmpty() && contains(events, level.peek().getLastEvent())) {
            return level.peek().getLastEvent();
        }
        final Event next = next();
        if (contains(events, next)) {
            return next;
        }
        throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.INTERNAL_ERROR, "Parser event [" + Arrays.toString(events) + "] not found." + getLastDataMsg()));
    }

    @Override
    public JsonValue getValue() {
        if (level.isEmpty() || null == getLastEvent()) {
            return jsonParser.getValue();
        }
        switch(getLastEvent()) {
            case START_ARRAY:
                return getArray();
            case START_OBJECT:
                return getObject();
            default:
                return jsonParser.getValue();
        }
    }

    @Override
    public Stream<JsonValue> getArrayStream() {
        return jsonParser.getArrayStream();
    }

    @Override
    public void skipArray() {
        jsonParser.skipArray();
        level.pop();
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return jsonParser.getObjectStream();
    }

    private String getLastDataMsg() {
        StringBuilder builder = new StringBuilder();
        final LevelContext currentLevel = getCurrentLevel();
        builder.append(" Last data: [").append("EVENT: ").append(currentLevel.getLastEvent()).append(" KEY_NAME: ").append(currentLevel.getLastKeyName()).append("]");
        return builder.toString();
    }

    @Override
    public JsonObject getObject() {
        JsonObject object = jsonParser.getObject();
        level.pop();
        return object;
    }

    @Override
    public void skipObject() {
        jsonParser.skipObject();
        level.pop();
    }

    @Override
    public void close() {
        jsonParser.close();
    }

    @Override
    public LevelContext getCurrentLevel() {
        return level.peek();
    }

    private boolean contains(Event[] events, Event candidate) {
        for (Event event : events) {
            if (candidate == event) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getInt() {
        return jsonParser.getInt();
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        return jsonParser.getValueStream();
    }

    @Override
    public void moveTo(Event required) {
        if (!level.isEmpty() && required == level.peek().getLastEvent()) {
            return;
        }
        final Event next = next();
        if (required == next) {
            return;
        }
        throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.INTERNAL_ERROR, "Event " + required + " not found." + getLastDataMsg()));
    }

    public Event getLastEvent() {
        return level.peek().getLastEvent();
    }

    @Override
    public long getLong() {
        return jsonParser.getLong();
    }

    @Override
    public JsonParser.Event next() {
        final Event next = jsonParser.next();
        level.peek().setLastEvent(next);
        switch(next) {
            case START_ARRAY:
            case START_OBJECT:
                final LevelContext newLevel = new LevelContext(level.peek());
                newLevel.setLastEvent(next);
                level.push(newLevel);
                break;
            case END_ARRAY:
            case END_OBJECT:
                level.pop().finish();
                break;
            case KEY_NAME:
                getCurrentLevel().setLastKeyName(jsonParser.getString());
                break;
            default:
                break;
        }
        return next;
    }

    @Override
    public Event moveToValue() {
        return moveTo(Event.VALUE_STRING, Event.VALUE_NUMBER, Event.VALUE_FALSE, Event.VALUE_TRUE, Event.VALUE_NULL);
    }

    /**
     * Creates a parser.
     *
     * @param jsonParser JSON-P parser to decorate.
     */
    public JsonbRiParser(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
        //root level
        this.level.push(new LevelContext(null));
    }

    @Override
    public JsonLocation getLocation() {
        return jsonParser.getLocation();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return jsonParser.getBigDecimal();
    }

    @Override
    public JsonArray getArray() {
        JsonArray result = jsonParser.getArray();
        level.pop();
        return result;
    }

    @Override
    public boolean isIntegralNumber() {
        return jsonParser.isIntegralNumber();
    }

    @Override
    public void skipJsonStructure() {
        final LevelContext currentLevel = level.peek();
        switch(currentLevel.getLastEvent()) {
            case START_ARRAY:
            case START_OBJECT:
                while (!currentLevel.isParsed()) {
                    next();
                }
                return;
            default:
                return;
        }
    }

    @Override
    public boolean hasNext() {
        return jsonParser.hasNext();
    }

    @Override
    public Event moveToStartStructure() {
        return moveTo(Event.START_OBJECT, Event.START_ARRAY);
    }

    @Override
    public String getString() {
        return jsonParser.getString();
    }

}

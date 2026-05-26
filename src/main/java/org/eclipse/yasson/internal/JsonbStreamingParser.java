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
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Decorator for JSONP parser used by JSONB.
 */
public class JsonbStreamingParser implements JsonParser, JsonbNavigator {

    /**
     * State holder for current json structure level.
     */
    public static class LevelParseContext {

        private final LevelParseContext enclosingContext;

        private JsonParser.Event previousEvent;

        private String previousKey;

        private boolean isProcessed;

        /**
         * Get parent.
         *
         * @return Parent.
         */
        public LevelParseContext getParent() {
            return enclosingContext;
        }

        /**
         * Gets last key name.
         *
         * @return Last key name.
         */
        public String getLastKeyName() {
            return previousKey;
        }

        /**
         * Getter for parsed property.
         *
         * @return True or false.
         */
        public boolean isParsed() {
            return isProcessed;
        }

        private void finish() {
            if (isProcessed) {
                throw new IllegalStateException("Level already parsed");
            }
            isProcessed = true;
        }

        /**
         * Gets last event.
         *
         * @return Last event.
         */
        public Event getLastEvent() {
            return previousEvent;
        }

        private void setLastEvent(Event previousEvent) {
            this.previousEvent = previousEvent;
        }

        /**
         * Creates an instance.
         *
         * @param enclosingContext Parent context.
         */
        public LevelParseContext(LevelParseContext enclosingContext) {
            this.enclosingContext = enclosingContext;
        }

        private void setLastKeyName(String previousKey) {
            Objects.requireNonNull(previousKey);
            this.previousKey = previousKey;
        }

    }

    private final JsonParser parser;

    private final Deque<LevelParseContext> contextStack = new ArrayDeque<>();

    private Event advanceTo(Event... eventSequence) {
        if (!contextStack.isEmpty() && containsEvent(eventSequence, contextStack.peek().getLastEvent())) {
            return contextStack.peek().getLastEvent();
        }
        final Event upcomingEvent = next();
        if (containsEvent(eventSequence, upcomingEvent)) {
            return upcomingEvent;
        }
        throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Parser event [" + Arrays.toString(eventSequence) + "] not found." + getLastDataMsg()));
    }

    @Override
    public Stream<JsonValue> getArrayStream() {
        return parser.getArrayStream();
    }

    @Override
    public JsonObject getObject() {
        JsonObject jsonObj = parser.getObject();
        contextStack.pop();
        return jsonObj;
    }

    @Override
    public JsonValue getValue() {
        return parser.getValue();
    }

    @Override
    public void skipArray() {
        parser.skipArray();
        contextStack.pop();
    }

    public Event getLastEvent() {
        return contextStack.peek().getLastEvent();
    }

    @Override
    public boolean hasNext() {
        return parser.hasNext();
    }

    @Override
    public JsonbStreamingParser.LevelParseContext getCurrentLevel() {
        return contextStack.peek();
    }

    @Override
    public void skipObject() {
        parser.skipObject();
        contextStack.pop();
    }

    @Override
    public JsonParser.Event next() {
        final Event upcomingEvent = parser.next();
        contextStack.peek().setLastEvent(upcomingEvent);
        switch(upcomingEvent) {
            case START_ARRAY:
            case START_OBJECT:
                final LevelParseContext createdParseContext = new LevelParseContext(contextStack.peek());
                createdParseContext.setLastEvent(upcomingEvent);
                contextStack.push(createdParseContext);
                break;
            case END_ARRAY:
            case END_OBJECT:
                contextStack.pop().finish();
                break;
            case KEY_NAME:
                getCurrentLevel().setLastKeyName(parser.getString());
                break;
            default:
                break;
        }
        return upcomingEvent;
    }

    @Override
    public JsonArray getArray() {
        JsonArray jsonArray = parser.getArray();
        contextStack.pop();
        return jsonArray;
    }

    @Override
    public void moveTo(Event targetEvent) {
        if (!contextStack.isEmpty() && targetEvent == contextStack.peek().getLastEvent()) {
            return;
        }
        final Event upcomingEvent = next();
        if (targetEvent == upcomingEvent) {
            return;
        }
        throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Event " + targetEvent + " not found." + getLastDataMsg()));
    }

    @Override
    public void close() {
        parser.close();
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        return parser.getValueStream();
    }

    /**
     * Creates a parser.
     *
     * @param parser JSON-P parser to decorate.
     */
    public JsonbStreamingParser(JsonParser parser) {
        this.parser = parser;
        //root level
        this.contextStack.push(new LevelParseContext(null));
    }

    @Override
    public int getInt() {
        return parser.getInt();
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return parser.getObjectStream();
    }

    private String getLastDataMsg() {
        StringBuilder sb = new StringBuilder();
        final LevelParseContext activeContext = getCurrentLevel();
        sb.append(" Last data: [").append("EVENT: ").append(activeContext.getLastEvent()).append(" KEY_NAME: ").append(activeContext.getLastKeyName()).append("]");
        return sb.toString();
    }

    @Override
    public String getString() {
        return parser.getString();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return parser.getBigDecimal();
    }

    @Override
    public Event moveToValue() {
        return advanceTo(Event.VALUE_STRING, Event.VALUE_NUMBER, Event.VALUE_FALSE, Event.VALUE_TRUE, Event.VALUE_NULL);
    }

    @Override
    public Event moveToStartStructure() {
        return advanceTo(Event.START_OBJECT, Event.START_ARRAY);
    }

    @Override
    public boolean isIntegralNumber() {
        return parser.isIntegralNumber();
    }

    @Override
    public JsonLocation getLocation() {
        return parser.getLocation();
    }

    @Override
    public void skipJsonStructure() {
        final LevelParseContext activeContext = contextStack.peek();
        switch(activeContext.getLastEvent()) {
            case START_ARRAY:
            case START_OBJECT:
                while (!activeContext.isParsed()) {
                    next();
                }
                return;
            default:
                return;
        }
    }

    @Override
    public long getLong() {
        return parser.getLong();
    }

    private boolean containsEvent(Event[] eventSequence, Event potentialMatch) {
        for (Event occurrence : eventSequence) {
            if (potentialMatch == occurrence) {
                return true;
            }
        }
        return false;
    }

}

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
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

/**
 * Decorator for JSONP parser used by JSONB.
 */
public class JsonbRiEventParser implements JsonParser, JsonbNavigator {

    /**
     * State holder for current json structure level.
     */
    public static class ParsingLevelContext {

        private final ParsingLevelContext enclosingContext;

        private JsonParser.Event previousEvent;

        private String previousKey;

        private boolean isProcessed;

        /**
         * Creates an instance.
         *
         * @param enclosingContext Parent context.
         */
        public ParsingLevelContext(ParsingLevelContext enclosingContext) {
            this.enclosingContext = enclosingContext;
        }

        /**
         * Gets last event.
         *
         * @return Last event.
         */
        public JsonParser.Event getLastEvent() {
            return previousEvent;
        }

        private void setLastEvent(JsonParser.Event previousEvent) {
            this.previousEvent = previousEvent;
        }

        /**
         * Gets last key name.
         *
         * @return Last key name.
         */
        public String getLastKeyName() {
            return previousKey;
        }

        private void setLastKeyName(String previousKey) {
            Objects.requireNonNull(previousKey);
            this.previousKey = previousKey;
        }

        /**
         * Get parent.
         *
         * @return Parent.
         */
        public ParsingLevelContext getParent() {
            return enclosingContext;
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
    }

    private final JsonParser parserImpl;

    private final Deque<ParsingLevelContext> contextStack = new ArrayDeque<>();

    /**
     * Creates a parser.
     *
     * @param parserImpl JSON-P parser to decorate.
     */
    public JsonbRiEventParser(JsonParser parserImpl) {
        this.parserImpl = parserImpl;
        //root level
        this.contextStack.push(new ParsingLevelContext(null));
    }

    @Override
    public boolean hasNext() {
        return parserImpl.hasNext();
    }

    @Override
    public long getLong() {
        return parserImpl.getLong();
    }

    @Override
    public int getInt() {
        return parserImpl.getInt();
    }

    @Override
    public JsonParser.Event next() {
        final JsonParser.Event upcomingEvent = parserImpl.next();
        contextStack.peek().setLastEvent(upcomingEvent);
        switch(upcomingEvent) {
            case START_ARRAY:
            case START_OBJECT:
                final ParsingLevelContext createdContext = new ParsingLevelContext(contextStack.peek());
                createdContext.setLastEvent(upcomingEvent);
                contextStack.push(createdContext);
                break;
            case END_ARRAY:
            case END_OBJECT:
                contextStack.pop().finish();
                break;
            case KEY_NAME:
                getCurrentLevel().setLastKeyName(parserImpl.getString());
                break;
            default:
                break;
        }
        return upcomingEvent;
    }

    @Override
    public boolean isIntegralNumber() {
        return parserImpl.isIntegralNumber();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return parserImpl.getBigDecimal();
    }

    @Override
    public JsonLocation getLocation() {
        return parserImpl.getLocation();
    }

    @Override
    public void close() {
        parserImpl.close();
    }

    @Override
    public String getString() {
        return parserImpl.getString();
    }

    @Override
    public void moveTo(JsonParser.Event expectedEvent) {
        if (!contextStack.isEmpty() && expectedEvent == contextStack.peek().getLastEvent()) {
            return;
        }
        final Event upcomingEvent = next();
        if (expectedEvent == upcomingEvent) {
            return;
        }
        throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR, "Event " + expectedEvent + " not found." + getLastDataMsg()));
    }

    @Override
    public Event moveToValue() {
        return moveToEvent(Event.VALUE_STRING, Event.VALUE_NUMBER, Event.VALUE_FALSE, Event.VALUE_TRUE, Event.VALUE_NULL);
    }

    @Override
    public Event moveToStartStructure() {
        return moveToEvent(Event.START_OBJECT, Event.START_ARRAY);
    }

    private Event moveToEvent(Event... eventSequence) {
        if (!contextStack.isEmpty() && containsEvent(eventSequence, contextStack.peek().getLastEvent())) {
            return contextStack.peek().getLastEvent();
        }
        final Event upcomingEvent = next();
        if (containsEvent(eventSequence, upcomingEvent)) {
            return upcomingEvent;
        }
        throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR, "Parser event [" + Arrays.toString(eventSequence) + "] not found." + getLastDataMsg()));
    }

    private boolean containsEvent(Event[] eventSequence, Event possibleMatch) {
        for (Event target : eventSequence) {
            if (possibleMatch == target) {
                return true;
            }
        }
        return false;
    }

    private String getLastDataMsg() {
        StringBuilder sb = new StringBuilder();
        final ParsingLevelContext activeLevel = getCurrentLevel();
        sb.append(" Last data: [").append("EVENT: ").append(activeLevel.getLastEvent()).append(" KEY_NAME: ").append(activeLevel.getLastKeyName()).append("]");
        return sb.toString();
    }

    @Override
    public JsonbRiEventParser.ParsingLevelContext getCurrentLevel() {
        return contextStack.peek();
    }

    @Override
    public void skipJsonStructure() {
        final ParsingLevelContext activeLevel = contextStack.peek();
        switch(activeLevel.getLastEvent()) {
            case START_ARRAY:
            case START_OBJECT:
                while (!activeLevel.isParsed()) {
                    next();
                }
                return;
            default:
                return;
        }
    }

    @Override
    public JsonObject getObject() {
        JsonObject jsonObj = parserImpl.getObject();
        contextStack.pop();
        return jsonObj;
    }

    @Override
    public JsonValue getValue() {
        return parserImpl.getValue();
    }

    @Override
    public JsonArray getArray() {
        JsonArray jsonArray = parserImpl.getArray();
        contextStack.pop();
        return jsonArray;
    }

    @Override
    public Stream<JsonValue> getArrayStream() {
        return parserImpl.getArrayStream();
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return parserImpl.getObjectStream();
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        return parserImpl.getValueStream();
    }

    @Override
    public void skipArray() {
        parserImpl.skipArray();
        contextStack.pop();
    }

    @Override
    public void skipObject() {
        parserImpl.skipObject();
        contextStack.pop();
    }

    public JsonParser.Event getLastEvent() {
        return contextStack.peek().getLastEvent();
    }
}

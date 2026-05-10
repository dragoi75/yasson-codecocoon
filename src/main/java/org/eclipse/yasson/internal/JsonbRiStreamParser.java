/*******************************************************************************
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.bind.JsonbException;
import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParser;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Stream;

/**
 * Decorator for JSONP parser used by JSONB.
 *
 * @author Roman Grigoriadi
 */
public class JsonbRiStreamParser implements JsonParser, JsonbStreamParser {

    /**
     * State holder for current json structure level.
     */
    public static class LevelParseState {
        private final LevelParseState enclosingState;
        private JsonParser.Event previousEvent;
        private String previousKey;
        private boolean isProcessed;

        /**
         * Creates an instance.
         *
         * @param enclosingState Parent context.
         */
        public LevelParseState(LevelParseState enclosingState) {
            this.enclosingState = enclosingState;
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
            this.previousKey = previousKey;
        }

        /**
         * Get parent.
         *
         * @return Parent.
         */
        public LevelParseState getParent() {
            return enclosingState;
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

    private final JsonParser tokenParser;

    private final Stack<LevelParseState> stateStack = new Stack<>();

    /**
     * Creates a parser.
     *
     * @param tokenParser JSON-P parser to decorate.
     */
    public JsonbRiStreamParser(JsonParser tokenParser) {
        this.tokenParser = tokenParser;
        //root level
        this.stateStack.push(new LevelParseState(null));
    }

    @Override
    public boolean hasNext() {
        return  tokenParser.hasNext();
    }

    @Override
    public long getLong() {
        return tokenParser.getLong();
    }

    @Override
    public int getInt() {
        return tokenParser.getInt();
    }

    @Override
    public JsonParser.Event next() {
        final JsonParser.Event upcomingEvent = tokenParser.next();
        stateStack.peek().setLastEvent(upcomingEvent);
        switch (upcomingEvent) {
            case START_ARRAY:
            case START_OBJECT:
                final LevelParseState createdState = new LevelParseState(stateStack.peek());
                createdState.setLastEvent(upcomingEvent);
                stateStack.push(createdState);
                break;
            case END_ARRAY:
            case END_OBJECT:
                stateStack.pop().finish();
                break;
            case KEY_NAME:
                getCurrentLevel().setLastKeyName(tokenParser.getString());
                break;
            default:
                break;
        }
        return upcomingEvent;
    }

    @Override
    public boolean isIntegralNumber() {
        return tokenParser.isIntegralNumber();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return tokenParser.getBigDecimal();
    }

    @Override
    public JsonLocation getLocation() {
        return tokenParser.getLocation();
    }

    @Override
    public void close() {
        tokenParser.close();
    }

    @Override
    public String getString() {
        return tokenParser.getString();
    }

    @Override
    public void moveTo(JsonParser.Event targetEvent) {
        if (!stateStack.empty() && stateStack.peek().getLastEvent() == targetEvent) {
            return;
        }

        final Event upcomingEvent = next();
        if (upcomingEvent == targetEvent) {
            return;
        }

        throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Event " + targetEvent + " not found." + getLastDataMsg()));
    }

    @Override
    public Event moveToValue() {
        return moveToEvent(Event.VALUE_STRING, Event.VALUE_NUMBER, Event.VALUE_FALSE, Event.VALUE_TRUE, Event.VALUE_NULL);
    }

    @Override
    public Event moveToStartStructure() {
        return moveToEvent(Event.START_OBJECT, Event.START_ARRAY);
    }

    private Event moveToEvent(Event... candidates) {
        if (!stateStack.empty() && containsEvent(candidates, stateStack.peek().getLastEvent())) {
            return stateStack.peek().getLastEvent();
        }

        final Event upcomingEvent = next();
        if (containsEvent(candidates, upcomingEvent)) {
            return upcomingEvent;
        }

        throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Parser event ["+Arrays.toString(candidates)+"] not found." + getLastDataMsg()));
    }

    private boolean containsEvent(Event[] candidates, Event potentialEvent) {
        for (Event incoming : candidates) {
            if (incoming == potentialEvent) {
                return true;
            }
        }
        return false;
    }

    private String getLastDataMsg() {
        StringBuilder sb = new StringBuilder();
        final LevelParseState activeState = getCurrentLevel();
        sb.append(" Last data: [").append("EVENT: ").append(activeState.getLastEvent()).append(" KEY_NAME: ")
                .append(activeState.getLastKeyName()).append("]");
        return sb.toString();
    }

    @Override
    public JsonbRiStreamParser.LevelParseState getCurrentLevel() {
        return stateStack.peek();
    }

    @Override
    public void skipJsonStructure() {
        final LevelParseState activeState = stateStack.peek();
        switch (activeState.getLastEvent()) {
            case START_ARRAY:
            case START_OBJECT:
                while (!activeState.isParsed()) {
                    next();
                }
                return;
            default:
                return;
        }
    }

    @Override
    public JsonObject getObject() {
        JsonObject jsonObj = tokenParser.getObject();
        stateStack.pop();
        return jsonObj;
    }

    @Override
    public JsonValue getValue() {
        return tokenParser.getValue();
    }

    @Override
    public JsonArray getArray() {
        JsonArray jsonArray = tokenParser.getArray();
        stateStack.pop();
        return jsonArray;
    }

    @Override
    public Stream<JsonValue> getArrayStream() {
        return tokenParser.getArrayStream();
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return tokenParser.getObjectStream();
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        return tokenParser.getValueStream();
    }

    @Override
    public void skipArray() {
        tokenParser.skipArray();
        stateStack.pop();
    }

    @Override
    public void skipObject() {
        tokenParser.skipObject();
        stateStack.pop();
    }

    public JsonParser.Event getLastEvent() {
        return stateStack.peek().getLastEvent();
    }
}

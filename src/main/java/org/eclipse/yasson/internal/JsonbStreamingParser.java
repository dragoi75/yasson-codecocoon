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

import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;

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
public class JsonbStreamingParser implements JsonParser, JsonbCursor {

    /**
     * State holder for current json structure level.
     */
    public static class LevelParseContext {
        private final LevelParseContext enclosingContext;
        private JsonParser.Event previousEvent;
        private String previousKey;
        private boolean isProcessed;

        /**
         * Creates an instance.
         *
         * @param enclosingContext Parent context.
         */
        public LevelParseContext(LevelParseContext enclosingContext) {
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
            this.previousKey = previousKey;
        }

        /**
         * Get parent.
         *
         * @return Parent.
         */
        public LevelParseContext getParent() {
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

    private final JsonParser parserInstance;

    private final Stack<LevelParseContext> contextStack = new Stack<>();

    /**
     * Creates a parser.
     *
     * @param parserInstance JSON-P parser to decorate.
     */
    public JsonbStreamingParser(JsonParser parserInstance) {
        this.parserInstance = parserInstance;
        //root level
        this.contextStack.push(new LevelParseContext(null));
    }

    @Override
    public boolean hasNext() {
        return  parserInstance.hasNext();
    }

    @Override
    public long getLong() {
        return parserInstance.getLong();
    }

    @Override
    public int getInt() {
        return parserInstance.getInt();
    }

    @Override
    public JsonParser.Event next() {
        final JsonParser.Event upcomingEvent = parserInstance.next();
        contextStack.peek().setLastEvent(upcomingEvent);
        switch (upcomingEvent) {
            case START_ARRAY:
            case START_OBJECT:
                final LevelParseContext createdContext = new LevelParseContext(contextStack.peek());
                createdContext.setLastEvent(upcomingEvent);
                contextStack.push(createdContext);
                break;
            case END_ARRAY:
            case END_OBJECT:
                contextStack.pop().finish();
                break;
            case KEY_NAME:
                getCurrentLevel().setLastKeyName(parserInstance.getString());
                break;
            default:
                break;
        }
        return upcomingEvent;
    }

    @Override
    public boolean isIntegralNumber() {
        return parserInstance.isIntegralNumber();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return parserInstance.getBigDecimal();
    }

    @Override
    public JsonLocation getLocation() {
        return parserInstance.getLocation();
    }

    @Override
    public void close() {
        parserInstance.close();
    }

    @Override
    public String getString() {
        return parserInstance.getString();
    }

    @Override
    public void moveTo(JsonParser.Event targetEvent) {
        if (!contextStack.empty() && contextStack.peek().getLastEvent() == targetEvent) {
            return;
        }

        final Event upcomingEvent = next();
        if (upcomingEvent == targetEvent) {
            return;
        }

        throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.INTERNAL_ERROR, "Event " + targetEvent + " not found." + getLastDataMsg()));
    }

    @Override
    public Event moveToValue() {
        return moveToEvent(Event.VALUE_STRING, Event.VALUE_NUMBER, Event.VALUE_FALSE, Event.VALUE_TRUE, Event.VALUE_NULL);
    }

    @Override
    public Event moveToStartStructure() {
        return moveToEvent(Event.START_OBJECT, Event.START_ARRAY);
    }

    private Event moveToEvent(Event... eventCandidates) {
        if (!contextStack.empty() && containsEvent(eventCandidates, contextStack.peek().getLastEvent())) {
            return contextStack.peek().getLastEvent();
        }

        final Event upcomingEvent = next();
        if (containsEvent(eventCandidates, upcomingEvent)) {
            return upcomingEvent;
        }

        throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.INTERNAL_ERROR, "Parser event ["+Arrays.toString(eventCandidates)+"] not found." + getLastDataMsg()));
    }

    private boolean containsEvent(Event[] eventCandidates, Event option) {
        for (Event evt : eventCandidates) {
            if (evt == option) {
                return true;
            }
        }
        return false;
    }

    private String getLastDataMsg() {
        StringBuilder sb = new StringBuilder();
        final LevelParseContext activeContext = getCurrentLevel();
        sb.append(" Last data: [").append("EVENT: ").append(activeContext.getLastEvent()).append(" KEY_NAME: ")
                .append(activeContext.getLastKeyName()).append("]");
        return sb.toString();
    }

    @Override
    public JsonbStreamingParser.LevelParseContext getCurrentLevel() {
        return contextStack.peek();
    }

    @Override
    public void skipJsonStructure() {
        final LevelParseContext activeContext = contextStack.peek();
        switch (activeContext.getLastEvent()) {
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
    public JsonObject getObject() {
        JsonObject jsonNode = parserInstance.getObject();
        contextStack.pop();
        return jsonNode;
    }

    @Override
    public JsonValue getValue() {
        return parserInstance.getValue();
    }

    @Override
    public JsonArray getArray() {
        JsonArray jsonArray = parserInstance.getArray();
        contextStack.pop();
        return jsonArray;
    }

    @Override
    public Stream<JsonValue> getArrayStream() {
        return parserInstance.getArrayStream();
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return parserInstance.getObjectStream();
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        return parserInstance.getValueStream();
    }

    @Override
    public void skipArray() {
        parserInstance.skipArray();
        contextStack.pop();
    }

    @Override
    public void skipObject() {
        parserInstance.skipObject();
        contextStack.pop();
    }

    public JsonParser.Event getLastEvent() {
        return contextStack.peek().getLastEvent();
    }
}

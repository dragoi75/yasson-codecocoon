/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.bind.JsonbException;
import javax.json.stream.JsonLocation;
import javax.json.stream.JsonParser;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Decorator for JSONP parser used by JSONB.
 *
 * @author Roman Grigoriadi
 */
public class JsonbRiEventParser implements JsonParser, JsonbNavigator {

    /**
     * State holder for current json structure level.
     */
    public static class LevelParseContext {

        private final LevelParseContext enclosingContext;

        private JsonParser.Event previousEvent;

        private String previousKey;

        private boolean parsingComplete;

        /**
         * Get parent.
         *
         * @return Parent.
         */
        public LevelParseContext getParent() {
            return enclosingContext;
        }

        private void finish() {
            if (parsingComplete) {
                throw new IllegalStateException("Level already parsed");
            }
            parsingComplete = true;
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
            return parsingComplete;
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

        private void setLastEvent(Event previousEvent) {
            this.previousEvent = previousEvent;
        }

        /**
         * Gets last event.
         *
         * @return Last event.
         */
        public Event getLastEvent() {
            return previousEvent;
        }

    }

    private final JsonParser parser;

    private final Deque<LevelParseContext> contextStack = new ArrayDeque<>();

    @Override
    public JsonLocation getLocation() {
        return parser.getLocation();
    }

    @Override
    public JsonArray getArray() {
        JsonArray jsonArray = parser.getArray();
        contextStack.pop();
        return jsonArray;
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return parser.getObjectStream();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return parser.getBigDecimal();
    }

    private boolean containsEvent(Event[] eventList, Event potentialMatch) {
        for (Event inputItem : eventList) {
            if (potentialMatch == inputItem) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Event moveToStartStructure() {
        return advanceTo(Event.START_OBJECT, Event.START_ARRAY);
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
    public JsonValue getValue() {
        return parser.getValue();
    }

    @Override
    public int getInt() {
        return parser.getInt();
    }

    @Override
    public JsonbRiEventParser.LevelParseContext getCurrentLevel() {
        return contextStack.peek();
    }

    @Override
    public void skipArray() {
        parser.skipArray();
        contextStack.pop();
    }

    @Override
    public long getLong() {
        return parser.getLong();
    }

    @Override
    public Stream<JsonValue> getArrayStream() {
        return parser.getArrayStream();
    }

    @Override
    public void skipObject() {
        parser.skipObject();
        contextStack.pop();
    }

    @Override
    public Event moveToValue() {
        return advanceTo(Event.VALUE_STRING, Event.VALUE_NUMBER, Event.VALUE_FALSE, Event.VALUE_TRUE, Event.VALUE_NULL);
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        return parser.getValueStream();
    }

    @Override
    public JsonObject getObject() {
        JsonObject jsonObj = parser.getObject();
        contextStack.pop();
        return jsonObj;
    }

    @Override
    public boolean hasNext() {
        return parser.hasNext();
    }

    @Override
    public void close() {
        parser.close();
    }

    /**
     * Creates a parser.
     *
     * @param parser JSON-P parser to decorate.
     */
    public JsonbRiEventParser(JsonParser parser) {
        this.parser = parser;
        //root level
        this.contextStack.push(new LevelParseContext(null));
    }

    private String getLastDataMsg() {
        StringBuilder buffer = new StringBuilder();
        final LevelParseContext activeContext = getCurrentLevel();
        buffer.append(" Last data: [").append("EVENT: ").append(activeContext.getLastEvent()).append(" KEY_NAME: ").append(activeContext.getLastKeyName()).append("]");
        return buffer.toString();
    }

    @Override
    public String getString() {
        return parser.getString();
    }

    @Override
    public JsonParser.Event next() {
        final Event followingEvent = parser.next();
        contextStack.peek().setLastEvent(followingEvent);
        switch(followingEvent) {
            case START_ARRAY:
            case START_OBJECT:
                final LevelParseContext childContext = new LevelParseContext(contextStack.peek());
                childContext.setLastEvent(followingEvent);
                contextStack.push(childContext);
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
        return followingEvent;
    }

    @Override
    public boolean isIntegralNumber() {
        return parser.isIntegralNumber();
    }

    private Event advanceTo(Event... eventList) {
        if (!contextStack.isEmpty() && containsEvent(eventList, contextStack.peek().getLastEvent())) {
            return contextStack.peek().getLastEvent();
        }
        final Event followingEvent = next();
        if (containsEvent(eventList, followingEvent)) {
            return followingEvent;
        }
        throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR, "Parser event [" + Arrays.toString(eventList) + "] not found." + getLastDataMsg()));
    }

    public Event getLastEvent() {
        return contextStack.peek().getLastEvent();
    }

    @Override
    public void moveTo(Event targetEvent) {
        if (!contextStack.isEmpty() && targetEvent == contextStack.peek().getLastEvent()) {
            return;
        }
        final Event followingEvent = next();
        if (targetEvent == followingEvent) {
            return;
        }
        throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR, "Event " + targetEvent + " not found." + getLastDataMsg()));
    }

}

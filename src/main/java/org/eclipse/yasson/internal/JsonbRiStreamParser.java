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

import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKey;
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
public class JsonbRiStreamParser implements JsonParser, JsonbStructureNavigator {

    /**
     * State holder for current json structure level.
     */
    public static class ParsingLevelContext {

        private final ParsingLevelContext ancestorContext;

        private JsonParser.Event previousEvent;

        private String previousKey;

        private boolean isProcessed;

        /**
         * Creates an instance.
         *
         * @param ancestorContext Parent context.
         */
        public ParsingLevelContext(ParsingLevelContext ancestorContext) {
            this.ancestorContext = ancestorContext;
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
        public ParsingLevelContext getParent() {
            return ancestorContext;
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

    private final JsonParser underlyingParser;

    private final Stack<ParsingLevelContext> contextStack = new Stack<>();

    /**
     * Creates a parser.
     *
     * @param underlyingParser JSON-P parser to decorate.
     */
    public JsonbRiStreamParser(JsonParser underlyingParser) {
        this.underlyingParser = underlyingParser;
        //root level
        this.contextStack.push(new ParsingLevelContext(null));
    }

    @Override
    public boolean hasNext() {
        return underlyingParser.hasNext();
    }

    @Override
    public long getLong() {
        return underlyingParser.getLong();
    }

    @Override
    public int getInt() {
        return underlyingParser.getInt();
    }

    @Override
    public JsonParser.Event next() {
        final JsonParser.Event upcomingEvent = underlyingParser.next();
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
                getCurrentLevel().setLastKeyName(underlyingParser.getString());
                break;
            default:
                break;
        }
        return upcomingEvent;
    }

    @Override
    public boolean isIntegralNumber() {
        return underlyingParser.isIntegralNumber();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return underlyingParser.getBigDecimal();
    }

    @Override
    public JsonLocation getLocation() {
        return underlyingParser.getLocation();
    }

    @Override
    public void close() {
        underlyingParser.close();
    }

    @Override
    public String getString() {
        return underlyingParser.getString();
    }

    @Override
    public void moveTo(JsonParser.Event targetEvent) {
        if (!contextStack.empty() && targetEvent == contextStack.peek().getLastEvent()) {
            return;
        }
        final Event upcomingEvent = next();
        if (targetEvent == upcomingEvent) {
            return;
        }
        throw new JsonbException(MessageBundle.getMessage(MessageKey.INTERNAL_ERROR, "Event " + targetEvent + " not found." + getLastDataMsg()));
    }

    @Override
    public Event moveToValue() {
        return advanceTo(Event.VALUE_STRING, Event.VALUE_NUMBER, Event.VALUE_FALSE, Event.VALUE_TRUE, Event.VALUE_NULL);
    }

    @Override
    public Event moveToStartStructure() {
        return advanceTo(Event.START_OBJECT, Event.START_ARRAY);
    }

    private Event advanceTo(Event... targetEvents) {
        if (!contextStack.empty() && containsEvent(targetEvents, contextStack.peek().getLastEvent())) {
            return contextStack.peek().getLastEvent();
        }
        final Event upcomingEvent = next();
        if (containsEvent(targetEvents, upcomingEvent)) {
            return upcomingEvent;
        }
        throw new JsonbException(MessageBundle.getMessage(MessageKey.INTERNAL_ERROR, "Parser event [" + Arrays.toString(targetEvents) + "] not found." + getLastDataMsg()));
    }

    private boolean containsEvent(Event[] targetEvents, Event possibleEvent) {
        for (Event inputEvent : targetEvents) {
            if (possibleEvent == inputEvent) {
                return true;
            }
        }
        return false;
    }

    private String getLastDataMsg() {
        StringBuilder sb = new StringBuilder();
        final ParsingLevelContext activeContext = getCurrentLevel();
        sb.append(" Last data: [").append("EVENT: ").append(activeContext.getLastEvent()).append(" KEY_NAME: ").append(activeContext.getLastKeyName()).append("]");
        return sb.toString();
    }

    @Override
    public JsonbRiStreamParser.ParsingLevelContext getCurrentLevel() {
        return contextStack.peek();
    }

    @Override
    public void skipJsonStructure() {
        final ParsingLevelContext activeContext = contextStack.peek();
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
    public JsonObject getObject() {
        JsonObject jsonObj = underlyingParser.getObject();
        contextStack.pop();
        return jsonObj;
    }

    @Override
    public JsonValue getValue() {
        return underlyingParser.getValue();
    }

    @Override
    public JsonArray getArray() {
        JsonArray arrayResult = underlyingParser.getArray();
        contextStack.pop();
        return arrayResult;
    }

    @Override
    public Stream<JsonValue> getArrayStream() {
        return underlyingParser.getArrayStream();
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return underlyingParser.getObjectStream();
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        return underlyingParser.getValueStream();
    }

    @Override
    public void skipArray() {
        underlyingParser.skipArray();
        contextStack.pop();
    }

    @Override
    public void skipObject() {
        underlyingParser.skipObject();
        contextStack.pop();
    }

    public JsonParser.Event getLastEvent() {
        return contextStack.peek().getLastEvent();
    }
}

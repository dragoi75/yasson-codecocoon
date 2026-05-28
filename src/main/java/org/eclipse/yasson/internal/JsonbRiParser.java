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

import org.eclipse.yasson.internal.properties.MessageKeyConstants;
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
public class JsonbRiParser implements JsonParser, JsonbParser {

    /**
     * State holder for current json structure level.
     */
    public static class LevelContext {

        private final LevelContext parent;

        private JsonParser.Event lastEvent;

        private String lastKeyName;

        private boolean parsed;

        private void finish() {
            if (parsed) {
                throw new IllegalStateException("Level already parsed");
            }
            parsed = true;
        }

        /**
         * Gets last key name.
         *
         * @return Last key name.
         */
        public String getLastKeyName() {
            return lastKeyName;
        }

        /**
         * Getter for parsed property.
         *
         * @return True or false.
         */
        public boolean isParsed() {
            return parsed;
        }

        private void setLastKeyName(String lastKeyName) {
            Objects.requireNonNull(lastKeyName);
            this.lastKeyName = lastKeyName;
        }

        /**
         * Get parent.
         *
         * @return Parent.
         */
        public LevelContext getParent() {
            return parent;
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
         * Creates an instance.
         *
         * @param parent Parent context.
         */
        public LevelContext(LevelContext parent) {
            this.parent = parent;
        }

    }

    private final JsonParser jsonParser;

    private final Deque<LevelContext> level = new ArrayDeque<>();

    @Override
    public void skipObject() {
        jsonParser.skipObject();
        level.pop();
    }

    @Override
    public JsonArray getArray() {
        JsonArray result = jsonParser.getArray();
        level.pop();
        return result;
    }

    @Override
    public JsonObject getObject() {
        JsonObject object = jsonParser.getObject();
        level.pop();
        return object;
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        return jsonParser.getValueStream();
    }

    @Override
    public LevelContext getCurrentLevel() {
        return level.peek();
    }

    @Override
    public void skipArray() {
        jsonParser.skipArray();
        level.pop();
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
        throw new JsonbException(Messages.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Event " + required + " not found." + getLastDataMsg()));
    }

    @Override
    public boolean hasNext() {
        return jsonParser.hasNext();
    }

    @Override
    public int getInt() {
        return jsonParser.getInt();
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return jsonParser.getObjectStream();
    }

    @Override
    public JsonLocation getLocation() {
        return jsonParser.getLocation();
    }

    @Override
    public Event moveToStartStructure() {
        return moveTo(Event.START_OBJECT, Event.START_ARRAY);
    }

    public Event getLastEvent() {
        return level.peek().getLastEvent();
    }

    private String getLastDataMsg() {
        StringBuilder builder = new StringBuilder();
        final LevelContext currentLevel = getCurrentLevel();
        builder.append(" Last data: [").append("EVENT: ").append(currentLevel.getLastEvent()).append(" KEY_NAME: ").append(currentLevel.getLastKeyName()).append("]");
        return builder.toString();
    }

    @Override
    public void close() {
        jsonParser.close();
    }

    @Override
    public JsonValue getValue() {
        return jsonParser.getValue();
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
    public boolean isIntegralNumber() {
        return jsonParser.isIntegralNumber();
    }

    private Event moveTo(Event... events) {
        if (!level.isEmpty() && contains(events, level.peek().getLastEvent())) {
            return level.peek().getLastEvent();
        }
        final Event next = next();
        if (contains(events, next)) {
            return next;
        }
        throw new JsonbException(Messages.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Parser event [" + Arrays.toString(events) + "] not found." + getLastDataMsg()));
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
    public Stream<JsonValue> getArrayStream() {
        return jsonParser.getArrayStream();
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
    public String getString() {
        return jsonParser.getString();
    }

    @Override
    public long getLong() {
        return jsonParser.getLong();
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
    public BigDecimal getBigDecimal() {
        return jsonParser.getBigDecimal();
    }

    @Override
    public Event moveToValue() {
        return moveTo(Event.VALUE_STRING, Event.VALUE_NUMBER, Event.VALUE_FALSE, Event.VALUE_TRUE, Event.VALUE_NULL);
    }

}

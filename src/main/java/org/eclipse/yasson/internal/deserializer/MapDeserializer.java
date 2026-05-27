/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.yasson.internal.deserializer;

import java.util.Map;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.DeserializationContextImpl;

/**
 * Map container deserializer.
 */
class MapDeserializer implements ModelDeserializer<JsonParser> {

    private final ModelDeserializer<JsonParser> keyDelegate;

    private final ModelDeserializer<JsonParser> valueDelegate;

    private enum Mode {

        NONE, NORMAL, OBJECT
    }

    private enum State {

        NEXT, VALUE, KEY, DONE
    }

    private Object deserializeValue(JsonParser parser, DeserializationContextImpl context, ModelDeserializer<JsonParser> deserializer) {
        DeserializationContextImpl keyContext = new DeserializationContextImpl(context);
        return deserializer.deserialize(parser, keyContext);
    }

    private void validateKeyName(String keyName, State state) {
        if (State.KEY != state || keyName.equals("key")) {
            if (State.VALUE == state && !keyName.equals("value")) {
                throw new JsonbException("Attribute name has to be 'value' when representing map entry value. Got: " + keyName);
            }
        } else {
            throw new JsonbException("Attribute name has to be 'key' when representing map entry key. Got: " + keyName);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object deserialize(JsonParser parser, DeserializationContextImpl context) {
        Map<Object, Object> map = (Map<Object, Object>) context.getInstance();
        Object key = null;
        Object keyValue = null;
        String keyName = null;
        Mode mode = Mode.NONE;
        State state = State.NEXT;
        while (parser.hasNext()) {
            final JsonParser.Event next = parser.next();
            context.setLastValueEvent(next);
            switch(next) {
                case KEY_NAME:
                    mode = Mode.NONE == mode ? Mode.NORMAL : mode;
                    if (Mode.NORMAL == mode) {
                        keyValue = deserializeValue(parser, context, keyDelegate);
                    }
                    keyName = parser.getString();
                    break;
                case START_OBJECT:
                    mode = Mode.NONE == mode ? Mode.OBJECT : mode;
                case START_ARRAY:
                case VALUE_STRING:
                case VALUE_TRUE:
                case VALUE_FALSE:
                case VALUE_NUMBER:
                case VALUE_NULL:
                    if (Mode.OBJECT != mode) {
                        Object value = deserializeValue(parser, context, valueDelegate);
                        map.put(keyValue, value);
                    } else {
                        if (State.NEXT != state) {
                            if (State.KEY != state) {
                                if (State.VALUE != state) {
                                    throw new JsonbException("Only attributes 'key' and 'value' allowed!");
                                } else {
                                    validateKeyName(keyName, state);
                                    Object value = deserializeValue(parser, context, valueDelegate);
                                    map.put(key, value);
                                    state = State.DONE;
                                }
                            } else {
                                validateKeyName(keyName, state);
                                key = deserializeValue(parser, context, keyDelegate);
                                state = State.VALUE;
                            }
                        } else {
                            state = State.KEY;
                        }
                    }
                    break;
                case END_OBJECT:
                    state = State.NEXT;
                    if (Mode.OBJECT == mode) {
                        break;
                    }
                case END_ARRAY:
                    return map;
                default:
                    throw new JsonbException("Unexpected state: " + next);
            }
        }
        return map;
    }

    MapDeserializer(ModelDeserializer<JsonParser> keyDelegate, ModelDeserializer<JsonParser> valueDelegate) {
        this.keyDelegate = keyDelegate;
        this.valueDelegate = valueDelegate;
    }

}

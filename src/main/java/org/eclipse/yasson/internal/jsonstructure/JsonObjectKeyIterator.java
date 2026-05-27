/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.yasson.internal.jsonstructure;

import java.util.Iterator;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Iterates over {@link JsonObject} managing internal state.
 */
public class JsonObjectKeyIterator extends JsonStructureWalker {

    /**
     * Location pointer.
     */
    public enum ParseState {

        /**
         * Start of the object.
         */
        START,
        /**
         * Property key name.
         */
        KEY,
        /**
         * Property value.
         */
        VALUE,
        /**
         * End of the object.
         */
        END
    }

    private final JsonObject rootObject;

    private final Iterator<String> keyCursor;

    private String activeKey;

    private ParseState parseMode = ParseState.START;

    /**
     * Current key this iterator is pointing at.
     *
     * @return Current key.
     */
    public String getKey() {
        return activeKey;
    }

    @Override
    JsonbException createIncompatibleValueException() {
        return new JsonbException(MessageBundle.getMessage(MessageKeyConstants.NUMBER_INCOMPATIBLE_VALUE_TYPE_OBJECT, getValue().getValueType(), activeKey));
    }

    private void getNextKey() {
        if (!keyCursor.hasNext()) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Object is empty"));
        }
        activeKey = keyCursor.next();
    }

    private void setState(ParseState parseMode) {
        this.parseMode = parseMode;
    }

    JsonObjectKeyIterator(JsonObject rootObject) {
        this.rootObject = rootObject;
        this.keyCursor = rootObject.keySet().iterator();
    }

    @Override
    public boolean hasNext() {
        //From the perspective of JsonParser not finished until END_OBJECT is being read.
        return ParseState.END != parseMode;
    }

    @Override
    String getString() {
        if (ParseState.KEY == parseMode) {
            return activeKey;
        }
        return super.getString();
    }

    /**
     * {@link JsonValue} for current key.
     *
     * @return Current JsonValue.
     */
    public JsonValue getValue() {
        if (ParseState.START == parseMode && null == activeKey) {
            return rootObject;
        }
        return rootObject.get(activeKey);
    }

    @Override
    public JsonParser.Event next() {
        switch(parseMode) {
            case START:
                if (!keyCursor.hasNext()) {
                    setState(ParseState.END);
                    return JsonParser.Event.END_OBJECT;
                } else {
                    getNextKey();
                    setState(ParseState.KEY);
                    return JsonParser.Event.KEY_NAME;
                }
            case KEY:
                setState(ParseState.VALUE);
                JsonValue jsonNode = getValue();
                return getValueEvent(jsonNode);
            case VALUE:
                if (keyCursor.hasNext()) {
                    getNextKey();
                    setState(ParseState.KEY);
                    return JsonParser.Event.KEY_NAME;
                }
                setState(ParseState.END);
                return JsonParser.Event.END_OBJECT;
            default:
                throw new JsonbException("Illegal state");
        }
    }

}

/**
 * ****************************************************************************
 *  Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.jsonstructure;

import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.bind.JsonbException;
import javax.json.stream.JsonParser;
import java.util.Iterator;

/**
 * Iterates over {@link JsonObject} managing internal state.
 */
public class JsonObjectIterator extends JsonStructureIterator {

    /**
     * Location pointer.
     */
    public enum State {

        START, KEY, VALUE, END
    }

    private final JsonObject jsonObject;

    private final Iterator<String> keyIterator;

    private String currentKey;

    private State state = State.START;

    /**
     * Current key this iterator is pointing at.
     * @return Current key.
     */
    public String getKey() {
        return currentKey;
    }

    /**
     * {@link JsonValue} for current key.
     * @return Current JsonValue.
     */
    public JsonValue getValue() {
        return jsonObject.get(currentKey);
    }

    @Override
    String getString() {
        if (State.KEY == state) {
            return currentKey;
        }
        return super.getString();
    }

    @Override
    public JsonParser.Event next() {
        switch(state) {
            case START:
                if (!keyIterator.hasNext()) {
                    setState(State.END);
                    return JsonParser.Event.END_OBJECT;
                } else {
                    nextKey();
                    setState(State.KEY);
                    return JsonParser.Event.KEY_NAME;
                }
            case KEY:
                setState(State.VALUE);
                JsonValue value = getValue();
                return getValueEvent(value);
            case VALUE:
                if (keyIterator.hasNext()) {
                    nextKey();
                    setState(State.KEY);
                    return JsonParser.Event.KEY_NAME;
                }
                setState(State.END);
                return JsonParser.Event.END_OBJECT;
            default:
                throw new JsonbException("Illegal state");
        }
    }

    private void setState(State state) {
        this.state = state;
    }

    private void nextKey() {
        if (!keyIterator.hasNext()) {
            throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR, "Object is empty"));
        }
        currentKey = keyIterator.next();
    }

    @Override
    public boolean hasNext() {
        //From the perspective of JsonParser not finished until END_OBJECT is being read.
        return State.END != state;
    }

    @Override
    JsonbException createIncompatibleValueError() {
        return new JsonbException(Messages.getMessage(MessageKeys.NUMBER_INCOMPATIBLE_VALUE_TYPE_OBJECT, getValue().getValueType(), currentKey));
    }

    JsonObjectIterator(JsonObject jsonObject) {
        this.jsonObject = jsonObject;
        this.keyIterator = jsonObject.keySet().iterator();
    }

}

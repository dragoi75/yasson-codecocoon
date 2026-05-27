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

import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.DeserializationContextImpl;

/**
 * Yasson {@link YassonParser} parser wrapper.
 * <br>
 * Used for user defined deserializers. Does not allow deserializer to read outside the scope it should be used on.
 */
class YassonParser implements JsonParser {

    private final JsonParser delegate;

    private final DeserializationContextImpl context;

    private int level;

    @Override
    public JsonObject getObject() {
        validate();
        level -= 1;
        JsonObject jsonObject = delegate.getObject();
        context.setLastValueEvent(Event.END_OBJECT);
        return jsonObject;
    }

    @Override
    public JsonLocation getLocation() {
        return delegate.getLocation();
    }

    @Override
    public JsonValue getValue() {
        final Event currentLevel = context.getLastValueEvent();
        switch(currentLevel) {
            case START_ARRAY:
                return getArray();
            case START_OBJECT:
                return getObject();
            default:
                return delegate.getValue();
        }
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        validate();
        level -= 1;
        return delegate.getObjectStream();
    }

    @Override
    public void skipObject() {
        validate();
        level -= 1;
        delegate.skipObject();
    }

    @Override
    public long getLong() {
        return delegate.getLong();
    }

    @Override
    public Stream<JsonValue> getArrayStream() {
        validate();
        level -= 1;
        return delegate.getArrayStream();
    }

    private int determineLevelValue(Event firstEvent) {
        switch(firstEvent) {
            case START_ARRAY:
            case START_OBJECT:
                //container start, there will be more events to come
                return 1;
            default:
                //just this single value, do not allow reading more
                return 0;
        }
    }

    private void validate() {
        if (1 > level) {
            throw new NoSuchElementException("There are no more elements available!");
        }
    }

    @Override
    public boolean hasNext() {
        if (1 > level) {
            return false;
        }
        return delegate.hasNext();
    }

    @Override
    public Stream<JsonValue> getValueStream() {
        validate();
        level -= 1;
        return delegate.getValueStream();
    }

    void skipRemaining() {
        while (hasNext()) {
            next();
        }
    }

    @Override
    public Event next() {
        validate();
        Event next = delegate.next();
        context.setLastValueEvent(next);
        switch(next) {
            case START_OBJECT:
            case START_ARRAY:
                level += 1;
                break;
            case END_OBJECT:
            case END_ARRAY:
                level -= 1;
                break;
            default:
        }
        return next;
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getBigDecimal() {
        return delegate.getBigDecimal();
    }

    @Override
    public int getInt() {
        return delegate.getInt();
    }

    @Override
    public String getString() {
        return delegate.getString();
    }

    @Override
    public JsonArray getArray() {
        validate();
        level -= 1;
        JsonArray array = delegate.getArray();
        context.setLastValueEvent(Event.END_ARRAY);
        return array;
    }

    @Override
    public boolean isIntegralNumber() {
        return delegate.isIntegralNumber();
    }

    YassonParser(JsonParser delegate, Event firstEvent, DeserializationContextImpl context) {
        this.delegate = delegate;
        this.context = context;
        this.level = determineLevelValue(firstEvent);
    }

    @Override
    public void skipArray() {
        validate();
        level -= 1;
        delegate.skipArray();
    }

}

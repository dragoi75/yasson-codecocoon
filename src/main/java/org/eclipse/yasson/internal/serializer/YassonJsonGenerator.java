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
package org.eclipse.yasson.internal.serializer;

import java.math.BigDecimal;
import java.math.BigInteger;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonGenerator;

/**
 * Yasson {@link JsonGenerator} generator wrapper.
 * <br>
 * Used for user defined serializers. Does not allow serializer to write outside the scope it should be used on.
 */
class YassonJsonGenerator implements JsonGenerator {

    private final JsonGenerator innerGenerator;

    private int indentDepth;

    @Override
    public JsonGenerator write(String fieldKey, String jsonNode) {
        validateWrite("write(String name, String value)");
        return innerGenerator.write(fieldKey, jsonNode);
    }

    @Override
    public JsonGenerator write(BigInteger jsonNode) {
        validateWrite("write(BigInteger value)");
        indentDepth -= 1;
        return innerGenerator.write(jsonNode);
    }

    @Override
    public JsonGenerator writeEnd() {
        indentDepth -= 1;
        if (0 > indentDepth) {
            throw new JsonbException("writeEnd() cannot be called outside of the scope of user generator.");
        }
        if (0 == indentDepth) {
            //if user has closed array or object and is on the same level he started. There is no more allowed writing.
            indentDepth -= 1;
        }
        return innerGenerator.writeEnd();
    }

    @Override
    public JsonGenerator write(double jsonNode) {
        validateWrite("write(double value)");
        indentDepth -= 1;
        return innerGenerator.write(jsonNode);
    }

    @Override
    public JsonGenerator write(int jsonNode) {
        validateWrite("write(int value)");
        indentDepth -= 1;
        return innerGenerator.write(jsonNode);
    }

    @Override
    public JsonGenerator writeStartArray() {
        validateWrite("writeStartArray()");
        indentDepth += 1;
        return innerGenerator.writeStartArray();
    }

    YassonJsonGenerator(JsonGenerator innerGenerator) {
        this.innerGenerator = innerGenerator;
    }

    @Override
    public JsonGenerator write(String fieldKey, long jsonNode) {
        validateWrite("write(String name, long value)");
        return innerGenerator.write(fieldKey, jsonNode);
    }

    @Override
    public JsonGenerator write(long jsonNode) {
        validateWrite("write(long value)");
        indentDepth -= 1;
        return innerGenerator.write(jsonNode);
    }

    @Override
    public JsonGenerator writeNull() {
        validateWrite("writeNull()");
        indentDepth -= 1;
        return innerGenerator.writeNull();
    }

    @Override
    public JsonGenerator write(boolean jsonNode) {
        validateWrite("write(boolean value)");
        indentDepth -= 1;
        return innerGenerator.write(jsonNode);
    }

    @Override
    public JsonGenerator write(String fieldKey, JsonValue jsonNode) {
        validateWrite("write(String name, JsonValue value)");
        return innerGenerator.write(fieldKey, jsonNode);
    }

    @Override
    public JsonGenerator write(BigDecimal jsonNode) {
        validateWrite("write(BigDecimal value)");
        indentDepth -= 1;
        return innerGenerator.write(jsonNode);
    }

    @Override
    public JsonGenerator writeStartObject(String fieldKey) {
        validateWrite("writeStartObject(String name)");
        indentDepth += 1;
        return innerGenerator.writeStartObject(fieldKey);
    }

    @Override
    public JsonGenerator write(String fieldKey, BigDecimal jsonNode) {
        validateWrite("write(String name, BigDecimal value)");
        return innerGenerator.write(fieldKey, jsonNode);
    }

    @Override
    public void flush() {
        throw new JsonbException("Unsupported operation in user defined deserializer.");
    }

    @Override
    public JsonGenerator writeKey(String fieldKey) {
        validateWrite("writeKey(String name)");
        indentDepth += 1;
        return innerGenerator.writeKey(fieldKey);
    }

    @Override
    public JsonGenerator write(String jsonNode) {
        validateWrite("write(String value)");
        indentDepth -= 1;
        return innerGenerator.write(jsonNode);
    }

    private void validateWrite(String operation) {
        if (0 > indentDepth) {
            throw new JsonbException(operation + " cannot be called outside of the scope of user generator.");
        }
    }

    @Override
    public JsonGenerator write(String fieldKey, int jsonNode) {
        validateWrite("write(String name, int value)");
        return innerGenerator.write(fieldKey, jsonNode);
    }

    @Override
    public JsonGenerator writeStartObject() {
        validateWrite("writeStartObject()");
        indentDepth += 1;
        return innerGenerator.writeStartObject();
    }

    @Override
    public JsonGenerator write(String fieldKey, double jsonNode) {
        validateWrite("write(String name, double value)");
        return innerGenerator.write(fieldKey, jsonNode);
    }

    @Override
    public JsonGenerator writeStartArray(String fieldKey) {
        validateWrite("writeStartArray(String name)");
        indentDepth += 1;
        return innerGenerator.writeStartArray(fieldKey);
    }

    @Override
    public JsonGenerator write(JsonValue jsonNode) {
        validateWrite("write(JsonValue value)");
        indentDepth -= 1;
        return innerGenerator.write(jsonNode);
    }

    @Override
    public JsonGenerator write(String fieldKey, BigInteger jsonNode) {
        validateWrite("write(String name, BigInteger value)");
        return innerGenerator.write(fieldKey, jsonNode);
    }

    @Override
    public JsonGenerator writeNull(String fieldKey) {
        validateWrite("writeNull(String name)");
        return innerGenerator.writeNull(fieldKey);
    }

    @Override
    public JsonGenerator write(String fieldKey, boolean jsonNode) {
        validateWrite("write(String name, boolean value)");
        return innerGenerator.write(fieldKey, jsonNode);
    }

    @Override
    public void close() {
        throw new JsonbException("Unsupported operation in user defined deserializer.");
    }

}

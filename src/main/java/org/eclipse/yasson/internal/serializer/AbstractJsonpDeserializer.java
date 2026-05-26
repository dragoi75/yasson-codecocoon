/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
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

import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbStreamingParser;
import org.eclipse.yasson.internal.JsonbUnmarshaller;

/**
 * Common implementation for JSONP Object and Array.
 *
 * @param <T> json value type
 */
public abstract class AbstractJsonpDeserializer<T extends JsonValue> extends AbstractContainerDeserializer<T> {

    @Override
    protected void deserializeNext(JsonParser parser, JsonbUnmarshaller context) {
        throw new UnsupportedOperationException("Inner json structures are deserialized by JsonParser.");
    }

    @Override
    public void appendResult(Object result) {
        throw new UnsupportedOperationException("Inner json structures are deserialized by JsonParser.");
    }

    @Override
    protected JsonbStreamingParser.LevelParseContext moveToFirst(JsonbNavigator parser) {
        parser.moveToStartStructure();
        return parser.getCurrentLevel();
    }

    /**
     * Create instance of current item with its builder.
     *
     * @param builder {@link JsonDeserializerBuilder} used to build this instance
     */
    protected AbstractJsonpDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
    }

}

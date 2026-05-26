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

import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DeserializationContextImplementation;

/**
 * Json null value checker.
 * <br>
 * Simple delegate which checks whether the obtained parser value event was
 * {@link JsonParser.Event#VALUE_NULL} or not. If the event has been {@link JsonParser.Event#VALUE_NULL}, null value
 * deserializer will be called. In all other cases non-null deserializer is called.
 */
public class NullCheckDeserializer implements ModelUnmarshaller<JsonParser> {

    private final ModelUnmarshaller<JsonParser> nonNullDeserializer;
    private final ModelUnmarshaller<Object> nullDeserializer;

    @Override
    public String toString() {
        return "Null value check";
    }

    /**
     * Create new instance.
     *
     * @param nonNullDeserializer deserializer called when value is not null
     * @param nullDeserializer    deserializer called when value is null
     */
    public NullCheckDeserializer(ModelUnmarshaller<JsonParser> nonNullDeserializer,
                                 ModelUnmarshaller<Object> nullDeserializer) {
        this.nonNullDeserializer = nonNullDeserializer;
        this.nullDeserializer = nullDeserializer;
    }

    @Override
    public Object unmarshal(JsonParser value, DeserializationContextImplementation context) {
        if (context.getLastValueEvent() != JsonParser.Event.VALUE_NULL) {
            return nonNullDeserializer.unmarshal(value, context);
        }
        return nullDeserializer.unmarshal(null, context);
    }

}

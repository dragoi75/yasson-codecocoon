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

import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.JsonbMarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Common type for all supported type serializers.
 *
 * @param <T> value type
 */
public abstract class AbstractValueSerializer<T> implements JsonbSerializer<T> {

    private final Customization customConfig;

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public AbstractValueSerializer(Customization customConfig) {
        this.customConfig = customConfig;
    }

    /**
     * Serializes an object to JSON.
     *
     * @param value       Object to serialize.
     * @param jsonWriter JSON generator to use.
     * @param context       JSON-B mapper context.
     */
    @Override
    public void serialize(T value, JsonGenerator jsonWriter, SerializationContext context) {
        JsonbMarshaller jsonbBinder = (JsonbMarshaller) context;
        serializeValue(value, jsonWriter, jsonbBinder);
    }

    /**
     * Serializes an object to JSON.
     *
     * @param value        Object to serialize.
     * @param jsonWriter  JSON generator to use.
     * @param jsonbBinder Marshaller.
     */
    protected abstract void serializeValue(T value, JsonGenerator jsonWriter, JsonbMarshaller jsonbBinder);

    /**
     * Returns value type customization.
     *
     * @return customization
     */
    public Customization getCustomization() {
        return customConfig;
    }
}

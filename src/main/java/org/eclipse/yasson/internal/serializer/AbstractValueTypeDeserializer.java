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

import java.lang.reflect.Type;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.JsonbDeserializer;
import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Common type for all supported value type serializers.
 *
 * @param <T> value type
 */
public abstract class AbstractValueTypeDeserializer<T> implements jakarta.json.bind.serializer.JsonbDeserializer<T> {

    private final Class<T> clazz;

    private final Customization customization;

    /**
     * Convert string value to object.
     *
     * @param jsonValue    Json value.
     * @param unmarshaller Unmarshaller instance.
     * @param rtType       Runtime type.
     * @return Deserialized object.
     */
    protected T deserialize(String jsonValue, JsonbDeserializer unmarshaller, Type rtType) {
        throw new UnsupportedOperationException("Operation not supported in " + getClass());
    }

    /**
     * Type of a property or creator parameter which is deserialized.
     *
     * @return property type.
     */
    protected Class<T> getPropertyType() {
        return clazz;
    }

    /**
     * Returns customization of object.
     *
     * @return object customization
     */
    public Customization getCustomization() {
        return customization;
    }

    /**
     * Creates a new instance.
     *
     * @param clazz         Class to work with.
     * @param customization Model customization.
     */
    public AbstractValueTypeDeserializer(Class<T> clazz, Customization customization) {
        this.clazz = clazz;
        this.customization = customization;
    }

    /**
     * Extracts single string value for conversion.
     *
     * @param parser Parser to get value from.
     * @param ctx    Unmarshaller.
     * @param rtType return type.
     * @return Deserialized object.
     */
    @Override
    public T deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        JsonbDeserializer unmarshaller = (JsonbDeserializer) ctx;
        final JsonParser.Event event = ((JsonbNavigator) parser).getCurrentLevel().getLastEvent();
        if (JsonParser.Event.VALUE_NULL == event) {
            return null;
        }
        final String value = parser.getString();
        return deserialize(value, unmarshaller, rtType);
    }

}

/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonbCursor;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;

import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;

/**
 * Common type for all supported value type serializers.
 *
 * @author Roman Grigoriadi
 */
public abstract class BaseValueTypeDeserializer<T> implements JsonbDeserializer<T> {

    private final Class<T> valueClass;

    private final SerializationCustomization serializationConfig;

    /**
     * Creates a new instance.
     *
     * @param valueClass Class to work with.
     * @param serializationConfig Model customization.
     */
    public BaseValueTypeDeserializer(Class<T> valueClass, SerializationCustomization serializationConfig) {
        this.valueClass = valueClass;
        this.serializationConfig = serializationConfig;
    }

    /**
     * Extracts single string value for conversion.
     *
     * @param jsonReader Parser to get value from.
     * @param deserializationContext Unmarshaller.
     * @param runtimeType return type.
     * @return Deserialized object.
     */
    @Override
    public T deserialize(JsonParser jsonReader, DeserializationContext deserializationContext, Type runtimeType) {
        JsonbUnmarshaller jsonbAdapter = (JsonbUnmarshaller) deserializationContext;
        final JsonParser.Event jsonToken = ((JsonbCursor) jsonReader).getCurrentLevel().getLastEvent();
        if (jsonToken == JsonParser.Event.VALUE_NULL) {
            return null;
        }

        final String rawText = jsonReader.getString();
        return deserializeValue(rawText, jsonbAdapter, runtimeType);
    }

    /**
     * Convert string value to object.
     *
     * @param jsonText Json value.
     * @param jsonbAdapter Unmarshaller instance.
     * @param runtimeType Runtime type.
     * @return Deserialized object.
     */
    protected T deserializeValue(String jsonText, JsonbUnmarshaller jsonbAdapter, Type runtimeType) {
        throw new UnsupportedOperationException("Operation not supported in " + getClass());
    }

    /**
     * Returns customization of object
     *
     * @return object customization
     */
    public SerializationCustomization getCustomization() {
        return serializationConfig;
    }

    /**
     * Type of a property or creator parameter which is deserialized.
     *
     * @return property type.
     */
    protected Class<T> getPropertyType() {
        return valueClass;
    }
}

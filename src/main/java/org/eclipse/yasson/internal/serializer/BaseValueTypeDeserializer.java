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

import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;

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

    private final Class<T> targetTypeClass;

    private final Customization settings;

    /**
     * Creates a new instance.
     *
     * @param targetTypeClass Class to work with.
     * @param settings Model customization.
     */
    public BaseValueTypeDeserializer(Class<T> targetTypeClass, Customization settings) {
        this.targetTypeClass = targetTypeClass;
        this.settings = settings;
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
        Unmarshaller jaxbHandler = (Unmarshaller) deserializationContext;
        final JsonParser.Event staxRecord = ((JsonbParser) jsonReader).getCurrentLevel().getLastEvent();
        if (staxRecord == JsonParser.Event.VALUE_NULL) {
            return null;
        }

        final String textContent = jsonReader.getString();
        return deserializeInstance(textContent, jaxbHandler, runtimeType);
    }

    /**
     * Convert string value to object.
     *
     * @param jsonString Json value.
     * @param jaxbHandler Unmarshaller instance.
     * @param runtimeType Runtime type.
     * @return Deserialized object.
     */
    protected T deserializeInstance(String jsonString, Unmarshaller jaxbHandler, Type runtimeType) {
        throw new UnsupportedOperationException("Operation not supported in " + getClass());
    }

    /**
     * Returns customization of object
     *
     * @return object customization
     */
    public Customization getCustomization() {
        return settings;
    }

    /**
     * Type of a property or creator parameter which is deserialized.
     *
     * @return property type.
     */
    protected Class<T> getPropertyType() {
        return targetTypeClass;
    }
}

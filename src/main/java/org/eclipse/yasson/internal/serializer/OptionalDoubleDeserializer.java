/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2020 Payara Foundation and/or its affiliates. All rights reserved.
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
import java.util.OptionalDouble;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer for {@link OptionalDouble} type.
 */
public class OptionalDoubleDeserializer extends AbstractValueTypeDeserializer<OptionalDouble> {

    /**
     * Creates a new instance.
     *
     * @param config Model customization.
     */
    public OptionalDoubleDeserializer(Customization config) {
        super(OptionalDouble.class, config);
    }

    @Override
    public OptionalDouble deserialize(JsonParser tokenReader, DeserializationContext deserializationContext, Type runtimeType) {
        final JsonParser.Event upcomingEvent = ((JsonbParser) tokenReader).moveToValue();
        if (JsonParser.Event.VALUE_NULL == upcomingEvent) {
            return OptionalDouble.empty();
        }
        String text = tokenReader.getString();
        return deserialize(text, (Unmarshaller) deserializationContext, runtimeType);
    }

    @Override
    protected OptionalDouble deserialize(String jsonText, Unmarshaller unmarshaller, Type rtType) {
        try {
            return OptionalDouble.of(Double.parseDouble(jsonText));
        } catch (NumberFormatException e) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR, OptionalDouble.class));
        }
    }
}

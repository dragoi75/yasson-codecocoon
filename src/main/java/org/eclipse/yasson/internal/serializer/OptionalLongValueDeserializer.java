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
import java.util.OptionalLong;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer for {@link OptionalLong} type.
 */
public class OptionalLongValueDeserializer extends AbstractValueTypeDeserializer<OptionalLong> {

    /**
     * Creates a new instance.
     *
     * @param customizer Model customization.
     */
    public OptionalLongValueDeserializer(Customization customizer) {
        super(OptionalLong.class, customizer);
    }

    @Override
    public OptionalLong deserialize(JsonParser tokenStream, DeserializationContext deserializationContext, Type runtimeType) {
        final JsonParser.Event upcomingEvent = ((JsonbParser) tokenStream).moveToValue();
        if (JsonParser.Event.VALUE_NULL == upcomingEvent) {
            return OptionalLong.empty();
        }
        return deserialize(tokenStream.getString(), (Unmarshaller) deserializationContext, runtimeType);
    }

    @Override
    protected OptionalLong deserialize(String jsonText, Unmarshaller unmarshaller, Type rtType) {
        try {
            return OptionalLong.of(Long.parseLong(jsonText));
        } catch (NumberFormatException e) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR, OptionalLong.class));
        }
    }
}

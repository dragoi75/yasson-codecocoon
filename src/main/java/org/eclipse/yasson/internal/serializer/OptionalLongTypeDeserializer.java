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

import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Deserializer for {@link OptionalLong} type.
 */
public class OptionalLongTypeDeserializer extends AbstractValueTypeDeserializer<OptionalLong> {

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    public OptionalLongTypeDeserializer(Customization customization) {
        super(OptionalLong.class, customization);
    }

    @Override
    public OptionalLong deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        final JsonParser.Event next = ((JsonbNavigator) parser).moveToValue();
        if (next == JsonParser.Event.VALUE_NULL) {
            return OptionalLong.empty();
        }
        return deserialize(parser.getString(), (JsonbUnmarshaller) ctx, rtType);
    }

    @Override
    protected OptionalLong deserialize(String jsonValue, JsonbUnmarshaller unmarshaller, Type rtType) {
        try {
            return OptionalLong.of(Long.parseLong(jsonValue));
        } catch (NumberFormatException e) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR, OptionalLong.class));
        }
    }
}

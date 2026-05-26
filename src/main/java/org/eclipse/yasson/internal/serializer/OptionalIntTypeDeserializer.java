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
import java.util.OptionalInt;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Deserializer for {@link OptionalInt} type.
 */
public class OptionalIntTypeDeserializer extends AbstractValueTypeDeserializer<OptionalInt> {

    @Override
    protected OptionalInt deserialize(String jsonValue, JsonbUnmarshaller unmarshaller, Type rtType) {
        try {
            return OptionalInt.of(Integer.parseInt(jsonValue));
        } catch (NumberFormatException e) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR, OptionalInt.class));
        }
    }

    @Override
    public OptionalInt deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        final JsonParser.Event next = ((JsonbNavigator) parser).moveToValue();
        if (JsonParser.Event.VALUE_NULL == next) {
            return OptionalInt.empty();
        }
        final String value = parser.getString();
        return deserialize(value, (JsonbUnmarshaller) ctx, rtType);
    }

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    public OptionalIntTypeDeserializer(Customization customization) {
        super(OptionalInt.class, customization);
    }

}

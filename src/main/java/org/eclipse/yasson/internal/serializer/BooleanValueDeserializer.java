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

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer for {@link Boolean} type.
 */
public class BooleanValueDeserializer extends AbstractValueTypeDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser jsonReader, DeserializationContext ctx, Type rtType) {
        JsonParser.Event occurrence = ((JsonbParser) jsonReader).moveToValue();
        switch (occurrence) {
        case VALUE_TRUE:
            return Boolean.TRUE;
        case VALUE_FALSE:
            return Boolean.FALSE;
        case VALUE_STRING:
            return Boolean.parseBoolean(jsonReader.getString());
        default:
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Unknown JSON value: " + occurrence));
        }
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public BooleanValueDeserializer(Customization customConfig) {
        super(Boolean.class, customConfig);
    }

}

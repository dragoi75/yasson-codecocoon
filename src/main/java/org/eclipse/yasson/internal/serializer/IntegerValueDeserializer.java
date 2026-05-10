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

import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer for {@link Integer} type.
 */
public class IntegerValueDeserializer extends AbstractNumberDeserializer<Integer> {

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public IntegerValueDeserializer(Customization customConfig) {
        super(Integer.class, customConfig);
    }

    @Override
    protected Integer deserialize(String rawJson, Unmarshaller dataParser, Type rtType) {
        return deserializeFormatted(rawJson, true, dataParser.getJsonbContext())
                .map(numberValue -> Integer.parseInt(numberValue.toString()))
                .orElseGet(() -> {
                    try {
                        return Integer.parseInt(rawJson);
                    } catch (NumberFormatException e) {
                        throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR,
                                                                     Integer.class));
                    }
                });
    }
}

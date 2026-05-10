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
 * Deserializer for {@link Short} type.
 */
public class ShortDeserializer extends AbstractNumberDeserializer<Short> {

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public ShortDeserializer(Customization customConfig) {
        super(Short.class, customConfig);
    }

    @Override
    protected Short deserialize(String jsonString, Unmarshaller parser, Type rtType) {
        return deserializeFormatted(jsonString, true, parser.getJsonbContext())
                .map(number -> Short.parseShort(number.toString()))
                .orElseGet(() -> {
                    try {
                        return Short.parseShort(jsonString);
                    } catch (NumberFormatException e) {
                        throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR, Short.class));
                    }
                });
    }
}

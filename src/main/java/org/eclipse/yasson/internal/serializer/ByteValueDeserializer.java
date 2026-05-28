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
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Serializer for {@link Byte} type.
 */
public class ByteValueDeserializer extends AbstractNumberDeserializer<Byte> {

    @Override
    protected Byte deserialize(String text, Unmarshaller xmlParser, Type rtType) {
        return deserializeFormatted(text, true, xmlParser.getJsonbContext())
                .map(index -> Byte.parseByte(index.toString()))
                .orElseGet(() -> {
                    try {
                        return Byte.parseByte(text);
                    } catch (NumberFormatException e) {
                        throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR, Byte.class));
                    }
                });
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public ByteValueDeserializer(Customization customConfig) {
        super(Byte.class, customConfig);
    }

}

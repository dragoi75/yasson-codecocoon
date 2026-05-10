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
 * Deserializer for {@link Float} type.
 */
public class FloatValueDeserializer extends AbstractNumberDeserializer<Float> {

    /**
     * Creates a new instance.
     *
     * @param customSettings Model customization.
     */
    public FloatValueDeserializer(Customization customSettings) {
        super(Float.class, customSettings);
    }

    @Override
    protected Float deserialize(String jsonString, Unmarshaller objectParser, Type rtType) {
        return deserializeFormatted(jsonString, false, objectParser.getJsonbContext())
                .map(number -> Float.parseFloat(number.toString()))
                .orElseGet(() -> {
                    try {
                        return Float.parseFloat(jsonString);
                    } catch (NumberFormatException e) {
                        throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR, Float.class));
                    }
                });
    }
}

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
 * Deserializer for {@link Double} type.
 */
public class DoubleValueDeserializer extends AbstractNumberDeserializer<Double> {

    private static final String INFINITY_POSITIVE = "POSITIVE_INFINITY";
    private static final String INFINITY_NEGATIVE = "NEGATIVE_INFINITY";
    private static final String NOT_A_NUMBER = "NaN";

    @Override
    protected Double deserialize(String jsonString, Unmarshaller unmarshalHandler, Type rtType) {
        switch (jsonString) {
        case NOT_A_NUMBER:
            return Double.NaN;
        case INFINITY_POSITIVE:
            return Double.POSITIVE_INFINITY;
        case INFINITY_NEGATIVE:
            return Double.NEGATIVE_INFINITY;
        default:
            return deserializeFormatted(jsonString, false, unmarshalHandler.getJsonbContext())
                    .map(number -> Double.parseDouble(number.toString()))
                    .orElseGet(() -> {
                        try {
                            return Double.parseDouble(jsonString);
                        } catch (NumberFormatException e) {
                            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR,
                                                                         Double.class));
                        }
                    });
        }
    }

    /**
     * Creates a new instance.
     *
     * @param customizer Model customization.
     */
    DoubleValueDeserializer(Customization customizer) {
        super(Double.class, customizer);
    }

}

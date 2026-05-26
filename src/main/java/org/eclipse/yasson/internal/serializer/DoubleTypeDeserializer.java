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

import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer for {@link Double} type.
 */
public class DoubleTypeDeserializer extends AbstractNumberDeserializer<Double> {

    private static final String POSITIVE_INFINITY = "POSITIVE_INFINITY";
    private static final String NEGATIVE_INFINITY = "NEGATIVE_INFINITY";
    private static final String NAN = "NaN";

    @Override
    protected Double deserialize(String jsonValue, JsonbUnmarshaller unmarshaller, Type rtType) {
        switch (jsonValue) {
        case NAN:
            return Double.NaN;
        case POSITIVE_INFINITY:
            return Double.POSITIVE_INFINITY;
        case NEGATIVE_INFINITY:
            return Double.NEGATIVE_INFINITY;
        default:
            return deserializeFormatted(jsonValue, false, unmarshaller.getJsonbContext())
                    .map(num -> Double.parseDouble(num.toString()))
                    .orElseGet(() -> {
                        try {
                            return Double.parseDouble(jsonValue);
                        } catch (NumberFormatException e) {
                            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.DESERIALIZE_VALUE_ERROR,
                                                                         Double.class));
                        }
                    });
        }
    }

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    DoubleTypeDeserializer(Customization customization) {
        super(Double.class, customization);
    }

}

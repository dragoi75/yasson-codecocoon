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
import java.math.BigDecimal;

import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;

import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Deserializer for {@link JsonNumber} type.
 */
public class JsonNumericTypeDeserializer extends AbstractValueTypeDeserializer<JsonNumber> {

    private static final String NUMERIC_LITERAL = "number";

    /**
     * Creates a new instance.
     *
     * @param configuration Model customization.
     */
    public JsonNumericTypeDeserializer(Customization configuration) {
        super(JsonNumber.class, configuration);
    }

    @Override
    protected JsonNumber deserialize(String valueString, Unmarshaller converter, Type rtType) {
        final JsonBuilderFactory jsonBuilder = converter.getJsonbContext().getJsonProvider().createBuilderFactory(null);
        JsonObject objectNode;
        try {
            Integer intValue = Integer.parseInt(valueString);

            objectNode = jsonBuilder.createObjectBuilder()
                    .add(NUMERIC_LITERAL, intValue)
                    .build();
            return objectNode.getJsonNumber(NUMERIC_LITERAL);
        } catch (NumberFormatException exception) {
        }
        try {
            Long longValue = Long.parseLong(valueString);

            objectNode = jsonBuilder.createObjectBuilder()
                    .add(NUMERIC_LITERAL, longValue)
                    .build();
            return objectNode.getJsonNumber(NUMERIC_LITERAL);
        } catch (NumberFormatException exception) {
        }

        objectNode = jsonBuilder.createObjectBuilder()
                .add(NUMERIC_LITERAL, new BigDecimal(valueString))
                .build();
        return objectNode.getJsonNumber(NUMERIC_LITERAL);
    }
}

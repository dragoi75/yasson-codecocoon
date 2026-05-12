/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;

import javax.json.JsonBuilderFactory;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import java.lang.reflect.Type;
import java.math.BigDecimal;

/**
 * Deserializer for {@link JsonNumber} type.
 * 
 * @author David Kral
 */
public class JsonNumericTypeDeserializer extends AbstractValueTypeDeserializer<JsonNumber> {

    private final static String NUMERIC_VALUE = "number";

    /**
     * Creates a new instance.
     *
     * @param customizer Model customization.
     */
    public JsonNumericTypeDeserializer(Customization customizer) {
        super(JsonNumber.class, customizer);
    }

    @Override
    protected JsonNumber deserialize(String jsonText, Unmarshaller converter, Type rtType) {
        final JsonBuilderFactory jsonBuilder = converter.getJsonbContext().getJsonProvider().createBuilderFactory(null);
        JsonObject parsedObject;
        try {
            Integer intValue = Integer.parseInt(jsonText);

            parsedObject = jsonBuilder.createObjectBuilder()
                    .add(NUMERIC_VALUE, intValue)
                    .build();
            return parsedObject.getJsonNumber(NUMERIC_VALUE);
        } catch (NumberFormatException exception) {
        }
        try {
            Long longValue = Long.parseLong(jsonText);

            parsedObject = jsonBuilder.createObjectBuilder()
                    .add(NUMERIC_VALUE, longValue)
                    .build();
            return parsedObject.getJsonNumber(NUMERIC_VALUE);
        } catch (NumberFormatException exception) {
        }

        parsedObject = jsonBuilder.createObjectBuilder()
                .add(NUMERIC_VALUE, new BigDecimal(jsonText))
                .build();
        return parsedObject.getJsonNumber(NUMERIC_VALUE);
    }
}

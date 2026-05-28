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

import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;

import javax.json.bind.JsonbException;
import java.lang.reflect.Type;

/**
 * Deserializer for {@link Integer} type.
 *
 * @author Roman Grigoriadi
 */
public class IntegerTypeDeserializer extends AbstractNumberDeserializer<Integer> {

    @Override
    protected Integer deserializeValue(String jsonValue, JsonbUnmarshaller unmarshaller, Type rtType) {
        return deserializeFormatted(jsonValue, true, unmarshaller.getJsonbContext())
                .map(num -> Integer.parseInt(num.toString()))
                .orElseGet(() -> {
                    try {
                        return Integer.parseInt(jsonValue);
                    } catch (NumberFormatException e) {
                        throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.DESERIALIZE_VALUE_ERROR,
                                Integer.class));
                    }
                });
    }

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    public IntegerTypeDeserializer(SerializationCustomization customization) {
        super(Integer.class, customization);
    }

}

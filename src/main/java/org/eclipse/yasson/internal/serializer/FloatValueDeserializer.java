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
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

import javax.json.bind.JsonbException;
import java.lang.reflect.Type;

/**
 * Deserializer for {@link Float} type.
 *
 * @author David Kral
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
    protected Float deserialize(String jsonString, Unmarshaller valueConverter, Type rtType) {
        return deserializeFormatted(jsonString, false, valueConverter.getJsonbContext())
                .map(numberValue -> Float.parseFloat(numberValue.toString()))
                .orElseGet(() -> {
                    try {
                        return Float.parseFloat(jsonString);
                    } catch (NumberFormatException e) {
                        throw new JsonbException(Messages.getMessage(MessageKeys.DESERIALIZE_VALUE_ERROR, Float.class));
                    }
                });
    }
}

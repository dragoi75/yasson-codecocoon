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

import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

import javax.json.bind.JsonbException;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.util.OptionalDouble;

/**
 * Deserializer for {@link OptionalDouble} type.
 * 
 * @author David Kral
 */
public class OptionalDoubleValueDeserializer extends AbstractValueTypeDeserializer<OptionalDouble> {

    /**
     * Creates a new instance.
     *
     * @param customizer Model customization.
     */
    public OptionalDoubleValueDeserializer(Customization customizer) {
        super(OptionalDouble.class, customizer);
    }

    @Override
    public OptionalDouble deserialize(JsonParser jsonReader, DeserializationContext deserializationContext, Type resolvedType) {
        final JsonParser.Event upcomingEvent = ((JsonbParser) jsonReader).moveToValue();
        if (upcomingEvent == JsonParser.Event.VALUE_NULL) {
            return OptionalDouble.empty();
        }
        String stringContent = jsonReader.getString();
        return deserialize(stringContent, (Unmarshaller) deserializationContext, resolvedType);
    }

    @Override
    protected OptionalDouble deserialize(String jsonText, Unmarshaller unmarshaller, Type rtType) {
        try {
            return OptionalDouble.of(Double.parseDouble(jsonText));
        } catch (NumberFormatException e) {
            throw new JsonbException(Messages.getMessage(MessageKeys.DESERIALIZE_VALUE_ERROR, OptionalDouble.class));
        }
    }
}

/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
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
import java.util.OptionalInt;

/**
 * Deserializer for {@link OptionalInt} type.
 *
 * @author David Kral
 */
public class OptionalIntValueDeserializer extends AbstractValueTypeDeserializer<OptionalInt> {

    @Override
    protected OptionalInt deserialize(String jsonText, Unmarshaller unmarshaller, Type rtType) {
        try {
            return OptionalInt.of(Integer.parseInt(jsonText));
        } catch (NumberFormatException e) {
            throw new JsonbException(Messages.getMessage(MessageKeys.DESERIALIZE_VALUE_ERROR, OptionalInt.class));
        }
    }

    @Override
    public OptionalInt deserialize(JsonParser jsonReader, DeserializationContext deserializationContext, Type runtimeType) {
        final JsonParser.Event upcomingEvent = ((JsonbParser) jsonReader).moveToValue();
        if (JsonParser.Event.VALUE_NULL == upcomingEvent) {
            return OptionalInt.empty();
        }
        final String textContent = jsonReader.getString();
        return deserialize(textContent, (Unmarshaller) deserializationContext, runtimeType);
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public OptionalIntValueDeserializer(Customization customConfig) {
        super(OptionalInt.class, customConfig);
    }

}

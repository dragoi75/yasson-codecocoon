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

import org.eclipse.yasson.internal.JsonbCursor;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;
import javax.json.bind.JsonbException;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.util.OptionalLong;

/**
 * Deserializer for {@link OptionalLong} type.
 *
 * @author David Kral
 */
public class OptionalLongTypeDeserializer extends BaseValueTypeDeserializer<OptionalLong> {

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    public OptionalLongTypeDeserializer(SerializationCustomization customization) {
        super(OptionalLong.class, customization);
    }

    @Override
    public OptionalLong deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        final JsonParser.Event next = ((JsonbCursor) parser).moveToValue();
        if (JsonParser.Event.VALUE_NULL == next) {
            return OptionalLong.empty();
        }
        return deserializeValue(parser.getString(), (JsonbUnmarshaller) ctx, rtType);
    }

    @Override
    protected OptionalLong deserializeValue(String jsonValue, JsonbUnmarshaller unmarshaller, Type rtType) {
        try {
            return OptionalLong.of(Long.parseLong(jsonValue));
        } catch (NumberFormatException e) {
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.DESERIALIZE_VALUE_ERROR, OptionalLong.class));
        }
    }
}

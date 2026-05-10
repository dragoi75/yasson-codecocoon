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

import org.eclipse.yasson.internal.JsonbStreamingParser;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;

import javax.json.JsonValue;
import javax.json.bind.JsonbException;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;

/**
 * Deserializer for {@link JsonValue} containing null, false, true, string and number.
 * 
 * @author Roman Grigoriadi
 */
public class JsonValueDeserializer extends BaseValueTypeDeserializer<JsonValue> {

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    public JsonValueDeserializer(SerializationCustomization customization) {
        super(JsonValue.class, customization);
    }

    @Override
    public JsonValue deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        final JsonParser.Event next = ((JsonbStreamingParser)parser).getLastEvent();
        switch (next) {
            case VALUE_TRUE:
                return JsonValue.TRUE;
            case VALUE_FALSE:
                return JsonValue.FALSE;
            case VALUE_NULL:
                return JsonValue.NULL;
            case VALUE_STRING:
            case VALUE_NUMBER:
                return parser.getValue();
            default:
                throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.INTERNAL_ERROR, "Unknown JSON value: "+next));
        }
    }

    @Override
    protected JsonValue deserializeValue(String jsonValue, JsonbUnmarshaller unmarshaller, Type rtType) {
        throw new UnsupportedOperationException();
    }
}

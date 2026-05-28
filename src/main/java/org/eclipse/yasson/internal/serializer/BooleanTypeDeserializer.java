/*******************************************************************************
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved.
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
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.Messages;

import javax.json.bind.JsonbException;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.util.NoSuchElementException;

/**
 * Deserializer for {@link Boolean} type.
 *
 * @author David Kral
 */
public class BooleanTypeDeserializer extends AbstractValueTypeDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        JsonParser.Event event = ((JsonbParser) parser).moveToValue();
        switch (event) {
            case VALUE_TRUE:
                return Boolean.TRUE;
            case VALUE_FALSE:
                return Boolean.FALSE;
            case VALUE_STRING:
                return Boolean.parseBoolean(parser.getString());
            default:
                throw new JsonbException(Messages.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Unknown JSON value: " + event));
        }
    }

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    public BooleanTypeDeserializer(SerializationCustomization customization) {
        super(Boolean.class, customization);
    }

}

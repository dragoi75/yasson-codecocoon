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
 * Serializer for {@link Byte} type.
 *
 * @author David Kral
 */
public class ByteDeserializer extends AbstractNumberDeserializer<Byte> {

    /**
     * Creates a new instance.
     *
     * @param config Model customization.
     */
    public ByteDeserializer(Customization config) {
        super(Byte.class, config);
    }

    @Override
    protected Byte deserialize(String text, Unmarshaller parser, Type rtType) {
        return deserializeFormatted(text, true, parser.getJsonbContext())
                .map(count -> Byte.parseByte(count.toString()))
                .orElseGet(() -> {
                    try {
                        return Byte.parseByte(text);
                    } catch (NumberFormatException e) {
                        throw new JsonbException(Messages.getMessage(MessageKeys.DESERIALIZE_VALUE_ERROR, Byte.class));
                    }
                });
    }
}

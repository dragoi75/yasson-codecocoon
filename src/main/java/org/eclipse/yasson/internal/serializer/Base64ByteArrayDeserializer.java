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
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageConstants;

import javax.json.bind.JsonbException;
import javax.json.bind.config.BinaryDataStrategy;
import java.lang.reflect.Type;
import java.util.Base64;

/**
 * Deserialize Base64 json string value into byte array.
 *
 * @author Roman Grigoriadi
 */
public class Base64ByteArrayDeserializer extends BaseValueTypeDeserializer<byte[]> {

    @Override
    protected byte[] deserializeInstance(String jsonString, Unmarshaller converter, Type rtType) {
        return getDecoder(converter.getJsonbContext().getConfigProperties().getBinaryDataStrategy()).decode(jsonString);
    }

    private Base64.Decoder getDecoder(String decodeMode) {
        switch (decodeMode) {
            case BinaryDataStrategy.BASE_64:
                return Base64.getDecoder();
            case BinaryDataStrategy.BASE_64_URL:
                return Base64.getUrlDecoder();
            default:
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.INTERNAL_ERROR, "Invalid strategy: " + decodeMode));
        }
    }

    /**
     * Creates a new instance.
     *
     * @param config Model customization.
     */
    public Base64ByteArrayDeserializer(Customization config) {
        super(byte[].class, config);
    }

}

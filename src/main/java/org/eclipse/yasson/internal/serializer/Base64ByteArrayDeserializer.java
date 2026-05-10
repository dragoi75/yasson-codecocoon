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
import javax.json.bind.config.BinaryDataStrategy;
import java.lang.reflect.Type;
import java.util.Base64;

/**
 * Deserialize Base64 json string value into byte array.
 *
 * @author Roman Grigoriadi
 */
public class Base64ByteArrayDeserializer extends BaseValueTypeDeserializer<byte[]> {

    /**
     * Creates a new instance.
     *
     * @param serializationOptions Model customization.
     */
    public Base64ByteArrayDeserializer(SerializationCustomization serializationOptions) {
        super(byte[].class, serializationOptions);
    }

    @Override
    protected byte[] deserializeValue(String jsonString, JsonbUnmarshaller jsonbParser, Type rtType) {
        return getDecoder(jsonbParser.getJsonbContext().getConfigProperties().getBinaryDataStrategy()).decode(jsonString);
    }

    private Base64.Decoder getDecoder(String decodingMode) {
        switch (decodingMode) {
            case BinaryDataStrategy.BASE_64:
                return Base64.getDecoder();
            case BinaryDataStrategy.BASE_64_URL:
                return Base64.getUrlDecoder();
            default:
                throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.INTERNAL_ERROR, "Invalid strategy: " + decodingMode));
        }
    }
}

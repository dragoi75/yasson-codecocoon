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

import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

import javax.json.bind.JsonbException;
import javax.json.bind.config.BinaryDataStrategy;
import javax.json.stream.JsonGenerator;
import java.util.Base64;

/**
 * Serializes byte array with Base64.
 *
 * @author Roman Grigoriadi
 */
public class ByteArrayToBase64Serializer extends ConfigurableValueTypeSerializer<byte[]> {

    /**
     * Creates a new instance.
     *
     * @param customConfig Customization model.
     */
    public ByteArrayToBase64Serializer(Customization customConfig) {
        super(customConfig);
    }

    @Override
    protected void serializeValue(byte[] dataBytes, JsonGenerator jsonWriter, Marshaller serializer) {
        jsonWriter.write(getEncoder(serializer.getJsonbContext().getConfigProperties().getBinaryDataStrategy()).encodeToString(dataBytes));
    }

    private Base64.Encoder getEncoder(String encodingType) {
        switch (encodingType) {
            case BinaryDataStrategy.BASE_64:
                return Base64.getEncoder();
            case BinaryDataStrategy.BASE_64_URL:
                return Base64.getUrlEncoder();
            default:
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.INTERNAL_ERROR,
                        "Invalid strategy: " + encodingType));
        }
    }
}

/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.serializer;

import java.util.Base64;

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.JsonbMarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Serializes byte array with Base64.
 */
public class ByteArrayBase64Serializer extends AbstractValueSerializer<byte[]> {

    private Base64.Encoder getEncoder(String strategy) {
        switch (strategy) {
        case BinaryDataStrategy.BASE_64:
            return Base64.getEncoder();
        case BinaryDataStrategy.BASE_64_URL:
            return Base64.getUrlEncoder();
        default:
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INTERNAL_ERROR,
                                                         "Invalid strategy: " + strategy));
        }
    }

    @Override
    protected void serializeValue(byte[] obj, JsonGenerator generator, JsonbMarshaller marshaller) {
        generator.write(getEncoder(marshaller.getJsonbContext().getConfigProperties().getBinaryDataStrategy())
                                .encodeToString(obj));
    }

    /**
     * Creates a new instance.
     *
     * @param customization Customization model.
     */
    public ByteArrayBase64Serializer(Customization customization) {
        super(customization);
    }

}

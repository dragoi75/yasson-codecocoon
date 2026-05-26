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

import java.io.UnsupportedEncodingException;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.JsonbMarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Serializer for {@link String} type.
 */
public class StringTypeSerializer extends AbstractValueSerializer<String> {

    @Override
    protected void serializeValue(String obj, JsonGenerator generator, JsonbMarshaller marshaller) {
        generator.write(toJson(obj, marshaller.getJsonbContext()));
    }

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    public StringTypeSerializer(Customization customization) {
        super(customization);
    }

    private String toJson(String object, JsonbRuntimeContext jsonbContext) {
        if ((boolean) jsonbContext.getConfig().getProperty(JsonbConfig.STRICT_IJSON).orElse(false)) {
            try {
                String newString = new String(object.getBytes("UTF-8"), "UTF-8");
                if (!newString.equals(object)) {
                    throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.UNPAIRED_SURROGATE));
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return object;
    }

}

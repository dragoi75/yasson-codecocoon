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

import org.eclipse.yasson.internal.JsonbContextManager;
import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Serializer for {@link String} type.
 */
public class StringTypeJsonSerializer extends AbstractValueTypeSerializer<String> {

    /**
     * Creates a new instance.
     *
     * @param settings Model customization.
     */
    public StringTypeJsonSerializer(Customization settings) {
        super(settings);
    }

    private String toJsonString(String text, JsonbContextManager contextManager) {
        if ((boolean) contextManager.getConfig().getProperty(JsonbConfig.STRICT_IJSON).orElse(false)) {
            try {
                String copiedString = new String(text.getBytes("UTF-8"), "UTF-8");
                if (!copiedString.equals(text)) {
                    throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.UNPAIRED_SURROGATE));
                }
            } catch (UnsupportedEncodingException ex) {
                ex.printStackTrace();
            }
        }
        return text;
    }

    @Override
    protected void serialize(String textValue, JsonGenerator jsonWriter, Marshaller serializer) {
        jsonWriter.write(toJsonString(textValue, serializer.getJsonbContext()));
    }
}

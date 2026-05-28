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
import java.lang.reflect.Type;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;

import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer for {@link String} type.
 */
public class StringToTypeDeserializer extends AbstractValueTypeDeserializer<String> {

    @Override
    protected String deserialize(String jsonText, Unmarshaller dataConverter, Type rtType) {
        if ((boolean) dataConverter.getJsonbContext().getConfig().getProperty(JsonbConfig.STRICT_IJSON).orElse(false)) {
            try {
                String tempString = new String(jsonText.getBytes("UTF-8"), "UTF-8");
                if (!tempString.equals(jsonText)) {
                    throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.UNPAIRED_SURROGATE));
                }
            } catch (UnsupportedEncodingException encodingException) {
                encodingException.printStackTrace();
            }
        }
        return jsonText;
    }

    /**
     * Creates a new instance.
     *
     * @param configOptions Model customization.
     */
    public StringToTypeDeserializer(Customization configOptions) {
        super(String.class, configOptions);
    }

}

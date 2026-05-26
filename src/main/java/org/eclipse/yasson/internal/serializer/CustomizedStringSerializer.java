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

import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

import javax.json.bind.JsonbConfig;
import javax.json.bind.JsonbException;
import javax.json.stream.JsonGenerator;
import java.io.UnsupportedEncodingException;

/**
 * Serializer for {@link String} type.
 * 
 * @author Roman Grigoriadi
 */
public class CustomizedStringSerializer extends AbstractValueTypeSerializer<String> {

    @Override
    protected void serialize(String sourceString, JsonGenerator jsonWriter, Marshaller binder) {
        jsonWriter.write(serializeToJson(sourceString, binder.getJsonbContext()));
    }

    private String serializeToJson(String inputString, JsonbContext serializationContext) {
        if ((boolean) serializationContext.getConfig().getProperty(JsonbConfig.STRICT_IJSON).orElse(false)) {
            try {
                String tempString = new String(inputString.getBytes("UTF-8"), "UTF-8");
                if (!tempString.equals(inputString)) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.UNPAIRED_SURROGATE));
                }
            } catch (UnsupportedEncodingException encodingException) {
                encodingException.printStackTrace();
            }
        }
        return inputString;
    }

    /**
     * Creates a new instance.
     *
     * @param serializerSettings Model customization.
     */
    public CustomizedStringSerializer(Customization serializerSettings) {
        super(serializerSettings);
    }

}

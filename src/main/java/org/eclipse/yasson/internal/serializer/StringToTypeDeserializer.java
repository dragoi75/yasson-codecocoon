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

import javax.json.bind.JsonbConfig;
import javax.json.bind.JsonbException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;

/**
 * Deserializer for {@link String} type.
 * 
 * @author Roman Grigoriadi
 */
public class StringToTypeDeserializer extends AbstractValueTypeDeserializer<String> {

    @Override
    protected String deserialize(String jsonText, Unmarshaller deserializer, Type rtType) {
        if ((boolean) deserializer.getJsonbContext().getConfig().getProperty(JsonbConfig.STRICT_IJSON).orElse(false)) {
            try {
                String constructedString = new String(jsonText.getBytes("UTF-8"), "UTF-8");
                if (!constructedString.equals(jsonText)) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.UNPAIRED_SURROGATE));
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
     * @param config Model customization.
     */
    public StringToTypeDeserializer(Customization config) {
        super(String.class, config);
    }

}

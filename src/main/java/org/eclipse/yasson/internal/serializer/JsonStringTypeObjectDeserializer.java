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

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonString;
import java.lang.reflect.Type;

/**
 * Deserializer for {@link JsonString} type.
 * 
 * @author David Kral
 */
public class JsonStringTypeObjectDeserializer extends AbstractValueTypeDeserializer<JsonString> {

    /**
     * Creates a new instance.
     *
     * @param configOptions Model customization.
     */
    public JsonStringTypeObjectDeserializer(Customization configOptions) {
        super(JsonString.class, configOptions);
    }

    @Override
    protected JsonString deserialize(String jsonText, Unmarshaller deserializer, Type rtType) {
        final JsonBuilderFactory jsonBuilder = deserializer.getJsonbContext().getJsonProvider().createBuilderFactory(null);
        final JsonObject parsedJson = jsonBuilder.createObjectBuilder()
                .add("json", jsonText)
                .build();
        return parsedJson.getJsonString("json");
    }
}

/*******************************************************************************
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.JsonbCursor;
import org.eclipse.yasson.internal.JsonbUnmarshaller;

import javax.json.JsonObject;

/**
 * Item for JsonObject.
 *
 * @author Roman Grigoriadi
 */
public class JsonToObjectDeserializer extends AbstractJsonpDeserializer<JsonObject> {

    private JsonObject parsedJson;

    @Override
    protected void deserializeContainer(JsonbCursor jsonCursor, JsonbUnmarshaller context) {
        this.parsedJson = jsonCursor.getObject();
    }

    /**
     * Create instance of current item with its builder.
     *
     * @param deserializerFactory {@link JsonValueDeserializerBuilder} used to build this instance
     */
    protected JsonToObjectDeserializer(JsonValueDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }


    @Override
    public JsonObject getInstance(JsonbUnmarshaller unmarshaller) {
        return parsedJson;
    }
}

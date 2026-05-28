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

import org.eclipse.yasson.internal.JsonbDeserializer;
import org.eclipse.yasson.internal.JsonbStructureNavigator;

import javax.json.JsonArray;

/**
 * Item for JsonArray.
 *
 * @author Roman Grigoriadi
 */
public class JsonArrayDeserializer extends AbstractJsonpDeserializer<JsonArray> {

    private JsonArray jsonArray;

    @Override
    public JsonArray getInstance(JsonbDeserializer unmarshaller) {
        return jsonArray;
    }

    /**
     * Create instance.
     *
     * @param builder Builder to initialize from.
     */
    protected JsonArrayDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
    }

    @Override
    protected void deserializeCollection(JsonbStructureNavigator parser, JsonbDeserializer context) {
        this.jsonArray = parser.getArray();
    }

}

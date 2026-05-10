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

import jakarta.json.JsonArray;

import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbDeserializer;

/**
 * Item for JsonArray.
 */
public class JsonArrayDeserializer extends AbstractJsonpDeserializer<JsonArray> {

    private JsonArray jsonArray;

    /**
     * Create instance.
     *
     * @param builder Builder to initialize from.
     */
    protected JsonArrayDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
    }

    @Override
    protected void deserialize(JsonbNavigator parser, JsonbDeserializer context) {
        this.jsonArray = parser.getArray();
    }

    @Override
    public JsonArray getInstance(JsonbDeserializer unmarshaller) {
        return jsonArray;
    }
}

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

import jakarta.json.JsonObject;

import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbUnmarshaller;

/**
 * Item for JsonObject.
 */
public class JsonObjectDeserializer extends AbstractJsonpDeserializer<JsonObject> {

    private JsonObject jsonObject;

    @Override
    public JsonObject getInstance(JsonbUnmarshaller unmarshaller) {
        return jsonObject;
    }

    @Override
    protected void deserializeInternal(JsonbNavigator parser, JsonbUnmarshaller context) {
        this.jsonObject = parser.getObject();
    }

    /**
     * Create instance of current item with its builder.
     *
     * @param builder {@link JsonDeserializerBuilder} used to build this instance
     */
    protected JsonObjectDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
    }

}

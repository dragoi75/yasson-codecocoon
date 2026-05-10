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

import javax.json.JsonArray;
import javax.json.JsonValue;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;

/**
 * Serializer for {@link JsonArray}.
 *
 * @author Roman Grigoriadi
 */
public class JsonArrayElementSerializer extends AbstractJsonpSerializer<JsonArray> {

    protected JsonArrayElementSerializer(TypeSerializerBuilder typeSerializerFactory) {
        super(typeSerializerFactory);
    }

    @Override
    protected void serializeInternal(JsonArray jsonArray, JsonGenerator jsonWriter, SerializationContext ctx) {
        for (JsonValue jsonElement : jsonArray) {
            jsonWriter.write(jsonElement);
        }
    }

    @Override
    protected void writeStart(JsonGenerator jsonWriter) {
        jsonWriter.writeStartArray();
    }

    @Override
    protected void writeStart(String fieldName, JsonGenerator jsonWriter) {
        jsonWriter.writeStartArray(fieldName);
    }
}

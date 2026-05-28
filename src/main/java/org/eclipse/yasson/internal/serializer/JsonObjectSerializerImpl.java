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

import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import java.util.Map;

/**
 * Serializer for {@link JsonObject} type.
 *
 * @author Roman Grigoriadi
 */
public class JsonObjectSerializerImpl extends AbstractJsonpSerializer<JsonObject> {

    @Override
    protected void writeStart(String propertyName, JsonGenerator outputWriter) {
        outputWriter.writeStartObject(propertyName);
    }

    @Override
    protected void writeStart(JsonGenerator outputWriter) {
        outputWriter.writeStartObject();
    }

    @Override
    protected void serializeInternal(JsonObject jsonObject, JsonGenerator outputWriter, SerializationContext ctx) {
        for (Map.Entry<String, JsonValue> keyValuePair : jsonObject.entrySet()) {
            outputWriter.write(keyValuePair.getKey(), keyValuePair.getValue());
        }
    }

    protected JsonObjectSerializerImpl(TypeSerializerBuilder typeSerializerFactory) {
        super(typeSerializerFactory);
    }

}

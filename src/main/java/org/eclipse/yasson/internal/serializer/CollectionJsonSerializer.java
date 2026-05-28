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

import org.eclipse.yasson.internal.JsonbRuntimeContext;

import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import java.util.Collection;

/**
 * Serializer for collections.
 *
 * @author Roman Grigoriadi
 */
public class CollectionJsonSerializer<T extends Collection> extends AbstractContainerSerializer<T> implements EmbeddedItem {

    protected final JsonbRuntimeContext jsonbContext;

    @Override
    protected void writeStart(JsonGenerator jsonWriter) {
        jsonWriter.writeStartArray();
    }

    @Override
    protected void writeStart(String fieldName, JsonGenerator jsonWriter) {
        jsonWriter.writeStartArray(fieldName);
    }

    @Override
    protected void serializeInternal(T items, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        for (Object element : items) {
            serializeItem(element, jsonWriter, serializationContext);
        }
    }

    protected CollectionJsonSerializer(TypeSerializerBuilder typeSerializerFactory) {
        super(typeSerializerFactory);
        this.jsonbContext = typeSerializerFactory.getJsonbContext();
    }

}

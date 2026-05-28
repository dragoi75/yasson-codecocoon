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

import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import java.util.Collection;

/**
 * Serializer for collections.
 *
 * @author Roman Grigoriadi
 */
public class CollectionSerializer<T extends Collection> extends ContainerSerializerBase<T> implements EmbeddedItem {

    protected final JsonbContext jsonbContext;

    @Override
    protected void writeBegin(JsonGenerator generator) {
        generator.writeStartArray();
    }

    @Override
    protected void writeBegin(String key, JsonGenerator generator) {
        generator.writeStartArray(key);
    }

    protected CollectionSerializer(TypeSerializerBuilder builder) {
        super(builder);
        this.jsonbContext = builder.getJsonbContext();
    }

    @Override
    protected void serializeContents(T collection, JsonGenerator generator, SerializationContext ctx) {
        for (Object item : collection) {
            serializeElement(item, generator, ctx);
        }
    }

}

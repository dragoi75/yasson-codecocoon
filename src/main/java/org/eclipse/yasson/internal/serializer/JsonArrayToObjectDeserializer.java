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

import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.Unmarshaller;

import javax.json.JsonArray;

/**
 * Item for JsonArray.
 *
 * @author Roman Grigoriadi
 */
public class JsonArrayToObjectDeserializer extends AbstractJsonpDeserializer<JsonArray> {

    private JsonArray elements;

    /**
     * Create instance.
     *
     * @param deserializerFactory Builder to initialize from.
     */
    protected JsonArrayToObjectDeserializer(JsonbDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }

    @Override
    protected void deserializeContents(JsonbParser tokenReader, Unmarshaller context) {
        this.elements = tokenReader.getArray();
    }

    @Override
    public JsonArray getInstance(Unmarshaller unmarshaller) {
        return elements;
    }
}

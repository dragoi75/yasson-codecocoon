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

import org.eclipse.yasson.internal.JsonUnmarshaller;
import org.eclipse.yasson.internal.JsonbStreamParser;
import org.eclipse.yasson.internal.JsonbRiStreamParser;

import javax.json.JsonValue;
import javax.json.stream.JsonParser;

/**
 * Common implementation for JSONP Object and Array.
 *
 * @author Roman Grigoriadi
 */
public abstract class AbstractJsonpDeserializer<T extends JsonValue> extends AbstractContainerDeserializer<T> {

    /**
     * Create instance of current item with its builder.
     *
     * @param builder {@link DeserializationBuilder} used to build this instance
     */
    protected AbstractJsonpDeserializer(DeserializationBuilder builder) {
        super(builder);
    }

    @Override
    protected JsonbRiStreamParser.LevelParseState moveToFirst(JsonbStreamParser parser) {
        parser.moveToStartStructure();
        return parser.getCurrentLevel();
    }

    @Override
    protected void deserializeNext(JsonParser parser, JsonUnmarshaller context) {
        throw new UnsupportedOperationException("Inner json structures are deserialized by JsonParser.");
    }

    @Override
    public void appendResult(Object result) {
        throw new UnsupportedOperationException("Inner json structures are deserialized by JsonParser.");
    }
}

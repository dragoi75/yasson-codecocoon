/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.deserializer;

import java.util.Collection;

import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DefaultDeserializationContext;

/**
 * Collection container deserializer.
 */
class CollectionDeserializer implements ModelParser<JsonParser> {

    private final ModelParser<JsonParser> delegate;

    CollectionDeserializer(ModelParser<JsonParser> delegate) {
        this.delegate = delegate;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object deserializeModel(JsonParser parser, DefaultDeserializationContext context) {
        Collection<Object> collection = (Collection<Object>) context.getInstance();
        while (parser.hasNext()) {
            final JsonParser.Event next = parser.next();
            context.setLastValueEvent(next);
            switch (next) {
            case VALUE_NULL:
            case START_OBJECT:
            case START_ARRAY:
            case VALUE_STRING:
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NUMBER:
                DefaultDeserializationContext newContext = new DefaultDeserializationContext(context);
                collection.add(delegate.deserializeModel(parser, newContext));
                break;
            case END_ARRAY:
                return collection;
            default:
                throw new JsonbException("Unexpected state: " + next);
            }
        }
        return collection;
    }

}

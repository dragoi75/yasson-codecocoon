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

import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DefaultDeserializationContext;

/**
 * Deserializer which creates new deserialization context and invokes delegate with it.
 */
class ContextSwitcher implements ModelParser<JsonParser> {

    private final ModelParser<Object> delegate;
    private final ModelParser<JsonParser> modelDeserializer;

    @Override
    public Object deserializeModel(JsonParser value, DefaultDeserializationContext context) {
        DefaultDeserializationContext ctx = new DefaultDeserializationContext(context);
        Object returnedValue = delegate.deserializeModel(modelDeserializer.deserializeModel(value, ctx), context);
        context.setLastValueEvent(ctx.getLastValueEvent());
        return returnedValue;
    }

    ContextSwitcher(ModelParser<Object> delegate,
                    ModelParser<JsonParser> modelDeserializer) {
        this.delegate = delegate;
        this.modelDeserializer = modelDeserializer;
    }

}

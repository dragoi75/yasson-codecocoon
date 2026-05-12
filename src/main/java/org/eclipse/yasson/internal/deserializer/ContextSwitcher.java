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

import org.eclipse.yasson.internal.DeserializationContextImplementation;

/**
 * Deserializer which creates new deserialization context and invokes delegate with it.
 */
class ContextSwitcher implements ModelUnmarshaller<JsonParser> {

    private final ModelUnmarshaller<Object> delegate;
    private final ModelUnmarshaller<JsonParser> modelDeserializer;

    ContextSwitcher(ModelUnmarshaller<Object> delegate,
                    ModelUnmarshaller<JsonParser> modelDeserializer) {
        this.delegate = delegate;
        this.modelDeserializer = modelDeserializer;
    }

    @Override
    public Object unmarshal(JsonParser value, DeserializationContextImplementation context) {
        DeserializationContextImplementation ctx = new DeserializationContextImplementation(context);
        Object returnedValue = delegate.unmarshal(modelDeserializer.unmarshal(value, ctx), context);
        context.setLastValueEvent(ctx.getLastValueEvent());
        return returnedValue;
    }
}

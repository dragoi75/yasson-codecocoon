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
package org.eclipse.yasson.internal.deserializer.types;

import java.util.OptionalInt;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.DefaultDeserializationContext;
import org.eclipse.yasson.internal.deserializer.ModelParser;

/**
 * Deserializer of the {@link OptionalInt} type.
 */
class OptionalIntDeserializer implements ModelParser<JsonParser> {

    private final ModelParser<JsonParser> extractor;

    private final ModelParser<Object> delegate;

    OptionalIntDeserializer(ModelParser<JsonParser> extractor, ModelParser<Object> delegate) {
        this.extractor = extractor;
        this.delegate = delegate;
    }

    @Override
    public Object deserializeModel(JsonParser value, DefaultDeserializationContext context) {
        if (JsonParser.Event.VALUE_NULL == context.getLastValueEvent()) {
            return delegate.deserializeModel(OptionalInt.empty(), context);
        }
        OptionalInt optional = OptionalInt.of((Integer) extractor.deserializeModel(value, context));
        return delegate.deserializeModel(optional, context);
    }
}

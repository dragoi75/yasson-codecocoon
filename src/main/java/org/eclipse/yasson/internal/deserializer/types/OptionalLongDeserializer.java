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

import java.util.OptionalLong;

import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DefaultDeserializationContext;
import org.eclipse.yasson.internal.deserializer.ModelParser;

/**
 * Deserializer of the {@link OptionalLong} type.
 */
class OptionalLongDeserializer implements ModelParser<JsonParser> {

    private final ModelParser<JsonParser> extractor;
    private final ModelParser<Object> nullValueDelegate;

    OptionalLongDeserializer(ModelParser<JsonParser> extractor, ModelParser<Object> nullValueDelegate) {
        this.extractor = extractor;
        this.nullValueDelegate = nullValueDelegate;
    }

    @Override
    public Object deserializeModel(JsonParser value, DefaultDeserializationContext context) {
        if (context.getLastValueEvent() == JsonParser.Event.VALUE_NULL) {
            return nullValueDelegate.deserializeModel(OptionalLong.empty(), context);
        }
        OptionalLong optional = OptionalLong.of((Long) extractor.deserializeModel(value, context));
        return nullValueDelegate.deserializeModel(optional, context);
    }
}

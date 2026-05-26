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

import org.eclipse.yasson.internal.DeserializationContextImplementation;
import org.eclipse.yasson.internal.deserializer.ModelUnmarshaller;

/**
 * Deserializer of the {@link OptionalInt} type.
 */
class OptionalIntDeserializer implements ModelUnmarshaller<JsonParser> {

    private final ModelUnmarshaller<JsonParser> extractor;
    private final ModelUnmarshaller<Object> delegate;

    @Override
    public Object unmarshal(JsonParser value, DeserializationContextImplementation context) {
        if (context.getLastValueEvent() == JsonParser.Event.VALUE_NULL) {
            return delegate.unmarshal(OptionalInt.empty(), context);
        }
        OptionalInt optional = OptionalInt.of((Integer) extractor.unmarshal(value, context));
        return delegate.unmarshal(optional, context);
    }

    OptionalIntDeserializer(ModelUnmarshaller<JsonParser> extractor, ModelUnmarshaller<Object> delegate) {
        this.extractor = extractor;
        this.delegate = delegate;
    }

}

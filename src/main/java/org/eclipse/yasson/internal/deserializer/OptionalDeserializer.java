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

import java.util.Optional;

import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DefaultDeserializationContext;

/**
 * Deserializer of the {@link Optional} types.
 */
class OptionalDeserializer implements ModelParser<JsonParser> {

    private final ModelParser<JsonParser> typeDeserializer;
    private final ModelParser<Object> delegate;

    @Override
    public Object deserializeModel(JsonParser value, DefaultDeserializationContext context) {
        Optional<Object> val = Optional.ofNullable(typeDeserializer.deserializeModel(value, context));
        return delegate.deserializeModel(val, context);
    }

    OptionalDeserializer(ModelParser<JsonParser> typeDeserializer,
                         ModelParser<Object> delegate) {
        this.typeDeserializer = typeDeserializer;
        this.delegate = delegate;
    }

}

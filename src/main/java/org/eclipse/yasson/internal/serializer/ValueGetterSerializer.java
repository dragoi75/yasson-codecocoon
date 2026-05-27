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

package org.eclipse.yasson.internal.serializer;

import java.lang.invoke.MethodHandle;

import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.DefaultSerializationContext;

/**
 * Extractor of the serialized value from the instance.
 */
class ValueGetterSerializer implements ModelMarshaller {

    private final MethodHandle valueGetter;
    private final ModelMarshaller delegate;

    @Override
    public void marshal(Object value, JsonGenerator generator, DefaultSerializationContext context) {
        Object object;
        try {
            object = valueGetter.invoke(value);
        } catch (Throwable e) {
            throw new JsonbException("Error getting value on: " + value.getClass().getName(), e);
        }
        delegate.marshal(object, generator, context);
    }

    ValueGetterSerializer(MethodHandle valueGetter, ModelMarshaller delegate) {
        this.valueGetter = valueGetter;
        this.delegate = delegate;
    }

}

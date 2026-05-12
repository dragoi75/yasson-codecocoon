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

import org.eclipse.yasson.internal.SerializationContextImpl;

/**
 * Extractor of the serialized value from the instance.
 */
class ValueGetterDelegatingSerializer implements ModelMarshaller {

    private final MethodHandle valueAccessor;
    private final ModelMarshaller modelMarshaller;

    ValueGetterDelegatingSerializer(MethodHandle valueAccessor, ModelMarshaller modelMarshaller) {
        this.valueAccessor = valueAccessor;
        this.modelMarshaller = modelMarshaller;
    }

    @Override
    public void marshal(Object instance, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
        Object target;
        try {
            target = valueAccessor.invoke(instance);
        } catch (Throwable throwable) {
            throw new JsonbException("Error getting value on: " + instance, throwable);
        }
        modelMarshaller.marshal(target, jsonWriter, serializationState);
    }
}

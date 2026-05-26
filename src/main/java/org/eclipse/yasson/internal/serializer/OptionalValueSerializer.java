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

import java.util.Optional;

import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.SerializationContextImpl;

/**
 * Optional container serializer.
 */
class OptionalValueSerializer implements ModelMarshaller {

    private final ModelMarshaller modelMarshaller;

    @SuppressWarnings("unchecked")
    @Override
    public void marshal(Object inputObject, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
        Optional<Object> maybeValue = (Optional<Object>) inputObject;
        modelMarshaller.marshal(maybeValue.orElse(null), jsonWriter, serializationState);
    }

    OptionalValueSerializer(ModelMarshaller modelMarshaller) {
        this.modelMarshaller = modelMarshaller;
    }

}

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

import org.eclipse.yasson.internal.DefaultSerializationContext;

/**
 * Optional container serializer.
 */
class OptionalValueSerializer implements ModelMarshaller {

    private final ModelMarshaller backingMarshaller;

    OptionalValueSerializer(ModelMarshaller backingMarshaller) {
        this.backingMarshaller = backingMarshaller;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void marshal(Object inputObject, JsonGenerator jsonWriter, DefaultSerializationContext serializationState) {
        Optional<Object> maybeObject = (Optional<Object>) inputObject;
        backingMarshaller.marshal(maybeObject.orElse(null), jsonWriter, serializationState);
    }

}

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
package org.eclipse.yasson.internal.serializer.types;

import java.util.OptionalDouble;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.yasson.internal.DefaultSerializationContext;
import org.eclipse.yasson.internal.serializer.ModelMarshaller;

/**
 * Serializer of the {@link OptionalDouble} type.
 */
class OptionalDoubleValueSerializer implements ModelMarshaller {

    private final ModelMarshaller modelMarshaller;

    OptionalDoubleValueSerializer(ModelMarshaller modelMarshaller) {
        this.modelMarshaller = modelMarshaller;
    }

    @Override
    public void marshal(Object inputObject, JsonGenerator jsonWriter, DefaultSerializationContext serializationState) {
        OptionalDouble maybeDouble = (OptionalDouble) inputObject;
        if (!maybeDouble.isPresent()) {
            modelMarshaller.marshal(null, jsonWriter, serializationState);
        } else {
            modelMarshaller.marshal(maybeDouble.getAsDouble(), jsonWriter, serializationState);
        }
    }
}

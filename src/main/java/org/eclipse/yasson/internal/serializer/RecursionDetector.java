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

import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.SerializationContextImpl;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * Recursion checker serializer deals with possible instance recursion in instances.
 */
class RecursionDetector implements ModelMarshaller {

    private final ModelMarshaller underlyingMarshaller;

    @Override
    public void marshal(Object inputObject, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
        if (!serializationState.addProcessedObject(inputObject)) {
            throw new JsonbException(MessageProvider.getMessage(MessageConstants.RECURSIVE_REFERENCE, inputObject.getClass()));
        }
        underlyingMarshaller.marshal(inputObject, jsonWriter, serializationState);
        serializationState.removeProcessedObject(inputObject);
    }

    RecursionDetector(ModelMarshaller underlyingMarshaller) {
        this.underlyingMarshaller = underlyingMarshaller;
    }

}

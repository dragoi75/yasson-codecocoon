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

import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.SerializationContextImpl;

/**
 * User defined serializer executor.
 */
class UserProvidedSerializer<T> implements ModelMarshaller {

    private final JsonbSerializer<T> customSerializer;

    UserProvidedSerializer(JsonbSerializer<T> customSerializer) {
        this.customSerializer = customSerializer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void marshal(Object objectToMarshal, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
        YassonGenerator jsonbWriter = new YassonGenerator(jsonWriter);
        customSerializer.serialize((T) objectToMarshal, jsonbWriter, serializationState);
    }

}

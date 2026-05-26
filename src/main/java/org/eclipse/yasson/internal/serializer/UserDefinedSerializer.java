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

import org.eclipse.yasson.internal.DefaultSerializationContext;

/**
 * User defined serializer executor.
 */
class UserDefinedSerializer<T> implements ModelMarshaller {

    private final JsonbSerializer<T> userDefinedSerializer;

    @SuppressWarnings("unchecked")
    @Override
    public void marshal(Object value, JsonGenerator generator, DefaultSerializationContext context) {
        YassonGenerator yassonGenerator = new YassonGenerator(generator);
        userDefinedSerializer.serialize((T) value, yassonGenerator, context);
    }

    UserDefinedSerializer(JsonbSerializer<T> userDefinedSerializer) {
        this.userDefinedSerializer = userDefinedSerializer;
    }

}

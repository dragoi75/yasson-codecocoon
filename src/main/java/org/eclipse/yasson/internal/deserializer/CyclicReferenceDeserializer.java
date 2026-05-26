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

import java.lang.reflect.Type;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.DeserializationContextImplementation;

/**
 * Deserialization solution for cyclic references.
 */
class CyclicReferenceDeserializer implements ModelUnmarshaller<JsonParser> {

    private final Type type;

    private ModelUnmarshaller<JsonParser> delegate;

    CyclicReferenceDeserializer(Type type) {
        this.type = type;
    }

    @Override
    public Object unmarshal(JsonParser value, DeserializationContextImplementation context) {
        if (null == delegate) {
            delegate = context.getJsonbContext().getChainModelCreator().deserializerChain(type);
        }
        return delegate.unmarshal(value, context);
    }
}

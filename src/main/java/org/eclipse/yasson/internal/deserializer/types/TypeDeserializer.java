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

package org.eclipse.yasson.internal.deserializer.types;

import java.lang.reflect.Type;

import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DeserializationContextManager;
import org.eclipse.yasson.internal.deserializer.ModelUnmarshaller;

/**
 * Base for all type deserializers.
 */
public abstract class TypeDeserializer implements ModelUnmarshaller<String> {

    private final ModelUnmarshaller<Object> delegate;
    private final Class<?> clazz;

    public final Object deserialize(JsonParser value, DeserializationContextManager context) {
        return delegate.unmarshal(deserializeNumberValue(value, context, clazz), context);
    }

    Object deserializeNumberValue(JsonParser value, DeserializationContextManager context, Type rType) {
        return deserializeStringValue(value.getString(), context, rType);
    }

    Class<?> getType() {
        return clazz;
    }

    public final Object deserialize(boolean value, DeserializationContextManager context) {
        return delegate.unmarshal(deserializeBooleanValue(value, context, clazz), context);
    }

    Object deserializeBooleanValue(boolean value, DeserializationContextManager context, Type rType) {
        return deserializeStringValue(String.valueOf(value), context, rType);
    }

    @Override
    public final Object unmarshal(String value, DeserializationContextManager context) {
        return delegate.unmarshal(deserializeStringValue(value, context, clazz), context);
    }

    TypeDeserializer(TypeDeserializerBuilder builder) {
        this.delegate = builder.getDelegate();
        this.clazz = builder.getClazz();
    }

    abstract Object deserializeStringValue(String value, DeserializationContextManager context, Type rType);

}

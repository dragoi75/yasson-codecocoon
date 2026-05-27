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

import org.eclipse.yasson.internal.DefaultDeserializationContext;
import org.eclipse.yasson.internal.deserializer.ModelParser;

/**
 * Base for all type deserializers.
 */
public abstract class TypeDeserializer implements ModelParser<String> {

    private final ModelParser<Object> delegate;
    private final Class<?> clazz;

    Object deserializeBooleanValue(boolean value, DefaultDeserializationContext context, Type rType) {
        return deserializeStringValue(String.valueOf(value), context, rType);
    }

    public final Object deserialize(boolean value, DefaultDeserializationContext context) {
        return delegate.deserializeModel(deserializeBooleanValue(value, context, clazz), context);
    }

    Object deserializeNumberValue(JsonParser value, DefaultDeserializationContext context, Type rType) {
        return deserializeStringValue(value.getString(), context, rType);
    }

    Class<?> getType() {
        return clazz;
    }

    abstract Object deserializeStringValue(String value, DefaultDeserializationContext context, Type rType);

    TypeDeserializer(TypeDeserializerBuilder builder) {
        this.delegate = builder.getDelegate();
        this.clazz = builder.getClazz();
    }

    @Override
    public final Object deserializeModel(String value, DefaultDeserializationContext context) {
        return delegate.deserializeModel(deserializeStringValue(value, context, clazz), context);
    }

    public final Object deserialize(JsonParser value, DefaultDeserializationContext context) {
        return delegate.deserializeModel(deserializeNumberValue(value, context, clazz), context);
    }

}

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
import java.math.BigDecimal;
import java.util.List;

import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DeserializationContextImplementation;
import org.eclipse.yasson.internal.deserializer.ModelUnmarshaller;

/**
 * Deserializer of the {@link Object} type.
 */
class ObjectTypeDeserializer implements ModelUnmarshaller<JsonParser> {

    private static final Type LIST = List.class;

    private final ModelUnmarshaller<Object> delegate;
    private final Class<?> mapClass;

    @Override
    public Object unmarshal(JsonParser value, DeserializationContextImplementation context) {
        Object toSet;
        switch (context.getLastValueEvent()) {
        case VALUE_TRUE:
            toSet = Boolean.TRUE;
            break;
        case VALUE_FALSE:
            toSet = Boolean.FALSE;
            break;
        case VALUE_NUMBER:
            toSet = new BigDecimal(value.getString());
            break;
        case KEY_NAME:
        case VALUE_STRING:
            toSet = value.getString();
            break;
        case START_OBJECT:
            DeserializationContextImplementation newContext = new DeserializationContextImplementation(context);
            toSet = newContext.deserialize(mapClass, value);
            break;
        case START_ARRAY:
            DeserializationContextImplementation newContext1 = new DeserializationContextImplementation(context);
            toSet = newContext1.deserialize(LIST, value);
            break;
        default:
            throw new JsonbException("Unexpected event: " + context.getLastValueEvent());
        }
        return delegate.unmarshal(toSet, context);
    }

    ObjectTypeDeserializer(TypeDeserializerBuilder builder) {
        this.delegate = builder.getDelegate();
        this.mapClass = builder.getConfigProperties().getDefaultMapImplType();
    }

}

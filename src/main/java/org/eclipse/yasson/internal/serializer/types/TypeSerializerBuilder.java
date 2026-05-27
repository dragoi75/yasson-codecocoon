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

import java.lang.reflect.Type;
import java.util.List;

import org.eclipse.yasson.internal.JsonBindingContext;
import org.eclipse.yasson.internal.model.customization.SerializationCustomizer;

/**
 * Type serializer data holder object used during serializer creation.
 */
class TypeSerializerBuilder {

    private final List<Type> chain;
    private final Class<?> clazz;
    private final SerializationCustomizer customization;
    private final JsonBindingContext jsonbContext;
    private final boolean key;

    public SerializationCustomizer getCustomization() {
        return customization;
    }

    public boolean isKey() {
        return key;
    }

    public JsonBindingContext getJsonbContext() {
        return jsonbContext;
    }

    public List<Type> getChain() {
        return chain;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    TypeSerializerBuilder(List<Type> chain,
                          Class<?> clazz,
                          SerializationCustomizer customization,
                          JsonBindingContext jsonbContext,
                          boolean key) {
        this.chain = chain;
        this.clazz = clazz;
        this.customization = customization;
        this.jsonbContext = jsonbContext;
        this.key = key;
    }

}

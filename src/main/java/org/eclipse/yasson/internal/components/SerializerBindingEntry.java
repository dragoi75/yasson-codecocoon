/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.components;

import java.lang.reflect.Type;

import jakarta.json.bind.serializer.JsonbSerializer;

/**
 * Binding for user Serializer component.
 *
 * @param <T> type of jsonb serializer
 */
public class SerializerBindingEntry<T> extends BaseComponentBinding {

    private final JsonbSerializer<T> jsonbAdapter;

    /**
     * Creates a new instance.
     *
     * @param boundType     Generic type argument of serializer. Not null.
     * @param jsonbAdapter Serializer. Can be null.
     */
    public SerializerBindingEntry(Type boundType, JsonbSerializer<T> jsonbAdapter) {
        super(boundType);
        this.jsonbAdapter = jsonbAdapter;
    }

    /**
     * Returns a serializer if any.
     *
     * @return Serializer.
     */
    public JsonbSerializer<T> getJsonbSerializer() {
        return jsonbAdapter;
    }

    /**
     * Class of user component.
     *
     * @return Component class.
     */
    @Override
    public Class<?> getComponentClass() {
        return jsonbAdapter.getClass();
    }
}

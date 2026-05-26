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
import java.util.Objects;

import jakarta.json.bind.adapter.JsonbAdapter;

/**
 * Wrapper for JsonbAdapter generic information and an components itself.
 */
public class AdapterBindingInfo extends AbstractComponentBinding {

    private final Type targetType;

    private final JsonbAdapter<?, ?> jsonConverter;

    /**
     * Get actual components to adapt object value.
     *
     * @return components
     */
    public JsonbAdapter<?, ?> getAdapter() {
        return jsonConverter;
    }

    @Override
    public Class<?> getComponentClass() {
        return jsonConverter.getClass();
    }

    /**
     * Adapter info with type to "adapt from", type to "adapt to" and an components itself.
     *
     * @param sourceType from not null
     * @param targetType   to not null
     * @param jsonConverter  components not null
     */
    public AdapterBindingInfo(Type sourceType, Type targetType, JsonbAdapter<?, ?> jsonConverter) {
        super(sourceType);
        Objects.requireNonNull(targetType);
        Objects.requireNonNull(jsonConverter);
        this.targetType = targetType;
        this.jsonConverter = jsonConverter;
    }

    /**
     * Represents a type to which to adapt into.
     *
     * During marshalling object property is adapted to this type and result is marshalled.
     * During unmarshalling object is unmarshalled into this type first, than converted to field type and set.
     *
     * @return Type from which to adapt
     */
    public Type getToType() {
        return targetType;
    }

}

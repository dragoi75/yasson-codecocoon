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

/**
 * Wrapper holding singleton instances of user defined components - Adapters, (De)Serializers.
 */
public class ComponentBindingInfo {

    private final Type boundType;

    private final SerializerBindingEntry writerEntry;

    private final JsonbDeserializerBinding readerBinding;

    private final AdapterBindingEntry converterEntry;

    /**
     * Type to which components are bound.
     *
     * @return Bound type.
     */
    public Type getBindingType() {
        return boundType;
    }

    /**
     * Serializer if any.
     *
     * @return serializer
     */
    public SerializerBindingEntry getSerializer() {
        return writerEntry;
    }

    /**
     * Deserializer if any.
     *
     * @return deserializer
     */
    public JsonbDeserializerBinding getDeserializer() {
        return readerBinding;
    }

    /**
     * Adapter info if any.
     *
     * @return adapterInfo
     */
    public AdapterBindingEntry getAdapterInfo() {
        return converterEntry;
    }

    /**
     * Creates an instance and populates it with bindings for a given type.
     *
     * @param boundType  Type components are bound to.
     * @param writerEntry   Serializer.
     * @param readerBinding Deserializer.
     * @param converter      Adapter.
     */
    public ComponentBindingInfo(Type boundType,
                                SerializerBindingEntry writerEntry,
                                JsonbDeserializerBinding readerBinding,
                                AdapterBindingEntry converter) {
        this.boundType = boundType;
        this.writerEntry = writerEntry;
        this.readerBinding = readerBinding;
        this.converterEntry = converter;
    }

    /**
     * Construct empty bindings for a given type.
     *
     * @param boundType type components are bound to
     */
    public ComponentBindingInfo(Type boundType) {
        this(boundType, null, null, null);
    }

}

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

package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.components.JsonbSerializerBinding;

/**
 * Common properties of {@link ClassSerializationConfig} and {@link PropertyCustomization}.
 */
abstract class CustomizationBase implements Customization, ComponentBoundCustomization {

    private final TypeAdapterBinding adapterBinding;

    private final JsonbSerializerBinding serializerBinding;

    private final JsonbDeserializerBinding deserializerBinding;

    private final boolean nillable;

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param builder not null
     */
    CustomizationBase(SerializationCustomizationBuilder builder) {
        this.nillable = builder.isNillable();
        this.adapterBinding = builder.getAdapterInfo();
        this.serializerBinding = builder.getSerializerBinding();
        this.deserializerBinding = builder.getDeserializerBinding();
    }

    /**
     * Copy constructor.
     *
     * @param other other customization instance
     */
    CustomizationBase(CustomizationBase other) {
        this.nillable = other.isNillable();
        this.adapterBinding = other.getSerializeAdapterBinding();
        this.serializerBinding = other.getSerializerBinding();
        this.deserializerBinding = other.getDeserializerBinding();
    }

    /**
     * Returns true if <i>nillable</i> customization is present.
     *
     * @return True if <i>nillable</i> customization is present.
     */
    public boolean isNillable() {
        return nillable;
    }

    public TypeAdapterBinding getSerializeAdapterBinding() {
        return adapterBinding;
    }

    @Override
    public TypeAdapterBinding getDeserializeAdapterBinding() {
        return adapterBinding;
    }

    /**
     * Serializer wrapper with resolved generic info.
     *
     * @return serializer wrapper
     */
    public JsonbSerializerBinding getSerializerBinding() {
        return serializerBinding;
    }

    /**
     * Deserializer wrapper with resolved generic info.
     *
     * @return deserializer wrapper
     */
    public JsonbDeserializerBinding getDeserializerBinding() {
        return deserializerBinding;
    }

}

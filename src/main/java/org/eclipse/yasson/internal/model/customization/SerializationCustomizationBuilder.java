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

import org.eclipse.yasson.internal.components.JsonbSerializerBinding;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;

/**
 * Abstract base builder for ensuring immutable state of {@link Customization} objects.
 */
public abstract class SerializationCustomizationBuilder {

    private boolean nullable;

    private TypeAdapterBinding adapterBinding;

    private JsonbSerializerBinding jsonbSerializer;

    private JsonbDeserializerBinding jsonbDeserializer;

    private String[] propertySequence;

    /**
     * Sets serializer info.
     *
     * @param jsonbSerializer Serializer info to set.
     */
    public void setSerializerBinding(JsonbSerializerBinding jsonbSerializer) {
        this.jsonbSerializer = jsonbSerializer;
    }

    /**
     * Gets ordered list of property names.
     *
     * @return Sorted names of properties.
     */
    public String[] getPropertyOrder() {
        return propertySequence;
    }

    /**
     * Sets an components.
     *
     * @param adapterBinding Adapter.
     */
    public void setAdapterInfo(TypeAdapterBinding adapterBinding) {
        this.adapterBinding = adapterBinding;
    }

    /**
     * Gets a deserializer.
     *
     * @return Deserializer.
     */
    public JsonbDeserializerBinding getDeserializerBinding() {
        return jsonbDeserializer;
    }

    /**
     * Sets a sorted list of property names.
     *
     * @param propertySequence Array containing property names
     */
    public void setPropertyOrder(String[] propertySequence) {
        this.propertySequence = propertySequence;
    }

    /**
     * Returns true if <i>nillable</i> customization is present.
     *
     * @return True if <i>nillable</i> customization is present.
     */
    public boolean isNillable() {
        return nullable;
    }

    /**
     * Sets a presence of <i>nillable</i> customization.
     *
     * @param nullable Presence of <i>nillable</i> customization.
     */
    public void setNillable(boolean nullable) {
        this.nullable = nullable;
    }

    /**
     * Gets meta info for user serializers.
     *
     * @return Serializer info
     */
    public JsonbSerializerBinding getSerializerBinding() {
        return jsonbSerializer;
    }

    /**
     * Gets an components.
     *
     * @return Adapter.
     */
    public TypeAdapterBinding getAdapterInfo() {
        return adapterBinding;
    }

    /**
     * Sets a deserializer info.
     *
     * @param jsonbDeserializer Deserializer.
     */
    public void setDeserializerBinding(JsonbDeserializerBinding jsonbDeserializer) {
        this.jsonbDeserializer = jsonbDeserializer;
    }

}

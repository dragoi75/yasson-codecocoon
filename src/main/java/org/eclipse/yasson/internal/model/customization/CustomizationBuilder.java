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

import org.eclipse.yasson.internal.components.AdapterBindingEntry;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.components.SerializerBindingEntry;

/**
 * Abstract base builder for ensuring immutable state of {@link Customization} objects.
 */
public abstract class CustomizationBuilder {

    private boolean nillable;

    private AdapterBindingEntry adapterInfo;

    private SerializerBindingEntry serializerBinding;

    private JsonbDeserializerBinding deserializerBinding;

    private String[] propertyOrder;

    /**
     * Returns true if <i>nillable</i> customization is present.
     *
     * @return True if <i>nillable</i> customization is present.
     */
    public boolean isNillable() {
        return nillable;
    }

    /**
     * Sets a presence of <i>nillable</i> customization.
     *
     * @param nillable Presence of <i>nillable</i> customization.
     */
    public void setNillable(boolean nillable) {
        this.nillable = nillable;
    }

    /**
     * Gets an components.
     *
     * @return Adapter.
     */
    public AdapterBindingEntry getAdapterInfo() {
        return adapterInfo;
    }

    /**
     * Sets an components.
     *
     * @param adapterInfo Adapter.
     */
    public void setAdapterInfo(AdapterBindingEntry adapterInfo) {
        this.adapterInfo = adapterInfo;
    }

    /**
     * Gets meta info for user serializers.
     *
     * @return Serializer info
     */
    public SerializerBindingEntry getSerializerBinding() {
        return serializerBinding;
    }

    /**
     * Sets serializer info.
     *
     * @param serializerBinding Serializer info to set.
     */
    public void setSerializerBinding(SerializerBindingEntry serializerBinding) {
        this.serializerBinding = serializerBinding;
    }

    /**
     * Gets a deserializer.
     *
     * @return Deserializer.
     */
    public JsonbDeserializerBinding getDeserializerBinding() {
        return deserializerBinding;
    }

    /**
     * Sets a deserializer info.
     *
     * @param deserializerBinding Deserializer.
     */
    public void setDeserializerBinding(JsonbDeserializerBinding deserializerBinding) {
        this.deserializerBinding = deserializerBinding;
    }

    /**
     * Gets ordered list of property names.
     *
     * @return Sorted names of properties.
     */
    public String[] getPropertyOrder() {
        return propertyOrder;
    }

    /**
     * Sets a sorted list of property names.
     *
     * @param propertyOrder Array containing property names
     */
    public void setPropertyOrder(String[] propertyOrder) {
        this.propertyOrder = propertyOrder;
    }
}

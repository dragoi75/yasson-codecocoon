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
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * The property customization builder that would be used to build an instance of {@link PropertyCustomization} to ensure its
 * immutability.
 */
public class PropertyCustomizationBuilder extends CustomizationBuilder {

    private String jsonReadName;
    private String jsonWriteName;

    private JsonbNumberFormatter serializeNumberFormatter;
    private JsonbNumberFormatter deserializeNumberFormatter;

    private JsonbDateFormatter serializeDateFormatter;
    private JsonbDateFormatter deserializeDateFormatter;

    private boolean readTransient;
    private boolean writeTransient;

    private AdapterBindingEntry serializeAdapter;
    private AdapterBindingEntry deserializeAdapter;

    private Class implementationClass;

    @Override
    public void setAdapterInfo(AdapterBindingEntry adapterInfo) {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets a property name which is written to JSON document on serialization.
     *
     * @param jsonWriteName Property name.
     */
    public void setJsonWriteName(String jsonWriteName) {
        this.jsonWriteName = jsonWriteName;
    }

    /**
     * Implementation class if property is interface type.
     *
     * @return class implementing property interface
     */
    public Class getImplementationClass() {
        return implementationClass;
    }

    /**
     * Sets a presence of <i>read transient</i> customization.
     *
     * @param readTransient Presence of <i>read transient</i> customization.
     */
    public void setReadTransient(boolean readTransient) {
        this.readTransient = readTransient;
    }

    /**
     * Sets date formatter for formatting dates during serialization process.
     *
     * @param serializeDateFormatter Date formatter for formatting dates during serialization process.
     */
    public void setSerializeDateFormatter(JsonbDateFormatter serializeDateFormatter) {
        this.serializeDateFormatter = serializeDateFormatter;
    }

    public void setDeserializeAdapter(AdapterBindingEntry adapter) {
        this.deserializeAdapter = adapter;
    }

    /**
     * Sets number formatter for formatting numbers during deserialization process.
     *
     * @param deserializeNumberFormatter Number formatter for formatting numbers during deserialization process.
     */
    public void setDeserializeNumberFormatter(JsonbNumberFormatter deserializeNumberFormatter) {
        this.deserializeNumberFormatter = deserializeNumberFormatter;
    }

    /**
     * Implementation class if property is interface type.
     *
     * @param implementationClass implementing property interface
     */
    public void setImplementationClass(Class implementationClass) {
        this.implementationClass = implementationClass;
    }

    public void setSerializeAdapter(AdapterBindingEntry adapter) {
        this.serializeAdapter = adapter;
    }

    /**
     * Sets a JSON property name used to read a property value from on deserialization.
     *
     * @param jsonReadName JSON property name
     */
    public void setJsonReadName(String jsonReadName) {
        this.jsonReadName = jsonReadName;
    }

    /**
     * Sets date formatter for formatting dates during deserialization process.
     *
     * @param deserializeDateFormatter Date formatter for formatting dates during deserialization process.
     */
    public void setDeserializeDateFormatter(JsonbDateFormatter deserializeDateFormatter) {
        this.deserializeDateFormatter = deserializeDateFormatter;
    }

    /**
     * Sets a presence of <i>write transient</i> customization.
     *
     * @param writeTransient Presence of <i>write transient</i> customization.
     */
    public void setWriteTransient(boolean writeTransient) {
        this.writeTransient = writeTransient;
    }

    public AdapterBindingEntry getDeserializeAdapter() {
        return deserializeAdapter;
    }

    /**
     * Returns true if <i>read transient</i> customization is present.
     *
     * @return True if <i>read transient</i> customization is present.
     */
    public boolean isReadTransient() {
        return readTransient;
    }

    /**
     * Gets date formatter for formatting dates during serialization process.
     *
     * @return date formatter for formatting dates during serialization process.
     */
    public JsonbDateFormatter getSerializeDateFormatter() {
        return serializeDateFormatter;
    }

    /**
     * Gets a property name which is written to JSON document on serialization.
     *
     * @return Property name.
     */
    public String getJsonWriteName() {
        return jsonWriteName;
    }

    @Override
    public AdapterBindingEntry getAdapterInfo() {
        return null;
    }

    public AdapterBindingEntry getSerializeAdapter() {
        return serializeAdapter;
    }

    /**
     * Gets number formatter for formatting numbers during serialization process.
     *
     * @return Number formatter for formatting numbers during serialization process.
     */
    public JsonbNumberFormatter getSerializeNumberFormatter() {
        return serializeNumberFormatter;
    }

    /**
     * Gets date formatter for formatting dates during deserialization process.
     *
     * @return Date formatter for formatting dates during deserialization process.
     */
    public JsonbDateFormatter getDeserializeDateFormatter() {
        return deserializeDateFormatter;
    }

    /**
     * Returns true if <i>write transient</i> customization is present.
     *
     * @return True if <i>write transient</i> customization is present.
     */
    public boolean isWriteTransient() {
        return writeTransient;
    }

    /**
     * Sets a JSON property name used to read a property value from on deserialization.
     *
     * @return JSON property name
     */
    public String getJsonReadName() {
        return jsonReadName;
    }

    /**
     * Creates a customization for class properties.
     *
     * @return A new instance of {@link PropertyCustomization}
     */
    public PropertyCustomization buildPropertyCustomization() {
        return new PropertyCustomization(this);
    }

    /**
     * Sets number formatter for formatting numbers during serialization process.
     *
     * @param serializeNumberFormatter Number formatter for formatting numbers during serialization process.
     */
    public void setSerializeNumberFormatter(JsonbNumberFormatter serializeNumberFormatter) {
        this.serializeNumberFormatter = serializeNumberFormatter;
    }

    /**
     * Gets number formatter for formatting numbers during deserialization process.
     *
     * @return Number formatter for formatting numbers during deserialization process.
     */
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        return deserializeNumberFormatter;
    }

}

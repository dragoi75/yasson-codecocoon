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

import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.serializer.JsonbDateTimeFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumericFormatter;

/**
 * The property customization builder that would be used to build an instance of {@link PropertyCustomization} to ensure its
 * immutability.
 */
public class PropertyCustomizationBuilder extends SerializationCustomizationBuilder {

    private String jsonReadName;
    private String jsonWriteName;

    private JsonbNumericFormatter serializeNumberFormatter;
    private JsonbNumericFormatter deserializeNumberFormatter;

    private JsonbDateTimeFormatter serializeDateFormatter;
    private JsonbDateTimeFormatter deserializeDateFormatter;

    private boolean readTransient;
    private boolean writeTransient;

    private TypeAdapterBinding serializeAdapter;
    private TypeAdapterBinding deserializeAdapter;

    private Class implementationClass;

    public TypeAdapterBinding getDeserializeAdapter() {
        return deserializeAdapter;
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
     * Gets date formatter for formatting dates during serialization process.
     *
     * @return date formatter for formatting dates during serialization process.
     */
    public JsonbDateTimeFormatter getSerializeDateFormatter() {
        return serializeDateFormatter;
    }

    /**
     * Sets a JSON property name used to read a property value from on deserialization.
     *
     * @param jsonReadName JSON property name
     */
    public void setJsonReadName(String jsonReadName) {
        this.jsonReadName = jsonReadName;
    }

    public TypeAdapterBinding getSerializeAdapter() {
        return serializeAdapter;
    }

    public void setSerializeAdapter(TypeAdapterBinding adapter) {
        this.serializeAdapter = adapter;
    }

    /**
     * Sets number formatter for formatting numbers during serialization process.
     *
     * @param serializeNumberFormatter Number formatter for formatting numbers during serialization process.
     */
    public void setSerializeNumberFormatter(JsonbNumericFormatter serializeNumberFormatter) {
        this.serializeNumberFormatter = serializeNumberFormatter;
    }

    /**
     * Sets a presence of <i>write transient</i> customization.
     *
     * @param writeTransient Presence of <i>write transient</i> customization.
     */
    public void setWriteTransient(boolean writeTransient) {
        this.writeTransient = writeTransient;
    }

    /**
     * Implementation class if property is interface type.
     *
     * @param implementationClass implementing property interface
     */
    public void setImplementationClass(Class implementationClass) {
        this.implementationClass = implementationClass;
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
     * Gets a property name which is written to JSON document on serialization.
     *
     * @return Property name.
     */
    public String getJsonWriteName() {
        return jsonWriteName;
    }

    public void setDeserializeAdapter(TypeAdapterBinding adapter) {
        this.deserializeAdapter = adapter;
    }

    /**
     * Returns true if <i>write transient</i> customization is present.
     *
     * @return True if <i>write transient</i> customization is present.
     */
    public boolean isWriteTransient() {
        return writeTransient;
    }

    @Override
    public TypeAdapterBinding getAdapterInfo() {
        return null;
    }

    /**
     * Gets number formatter for formatting numbers during serialization process.
     *
     * @return Number formatter for formatting numbers during serialization process.
     */
    public JsonbNumericFormatter getSerializeNumberFormatter() {
        return serializeNumberFormatter;
    }

    /**
     * Sets number formatter for formatting numbers during deserialization process.
     *
     * @param deserializeNumberFormatter Number formatter for formatting numbers during deserialization process.
     */
    public void setDeserializeNumberFormatter(JsonbNumericFormatter deserializeNumberFormatter) {
        this.deserializeNumberFormatter = deserializeNumberFormatter;
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
     * Gets number formatter for formatting numbers during deserialization process.
     *
     * @return Number formatter for formatting numbers during deserialization process.
     */
    public JsonbNumericFormatter getDeserializeNumberFormatter() {
        return deserializeNumberFormatter;
    }

    /**
     * Gets date formatter for formatting dates during deserialization process.
     *
     * @return Date formatter for formatting dates during deserialization process.
     */
    public JsonbDateTimeFormatter getDeserializeDateFormatter() {
        return deserializeDateFormatter;
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
     * Sets a property name which is written to JSON document on serialization.
     *
     * @param jsonWriteName Property name.
     */
    public void setJsonWriteName(String jsonWriteName) {
        this.jsonWriteName = jsonWriteName;
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
     * Sets date formatter for formatting dates during serialization process.
     *
     * @param serializeDateFormatter Date formatter for formatting dates during serialization process.
     */
    public void setSerializeDateFormatter(JsonbDateTimeFormatter serializeDateFormatter) {
        this.serializeDateFormatter = serializeDateFormatter;
    }

    @Override
    public void setAdapterInfo(TypeAdapterBinding adapterInfo) {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets date formatter for formatting dates during deserialization process.
     *
     * @param deserializeDateFormatter Date formatter for formatting dates during deserialization process.
     */
    public void setDeserializeDateFormatter(JsonbDateTimeFormatter deserializeDateFormatter) {
        this.deserializeDateFormatter = deserializeDateFormatter;
    }

}

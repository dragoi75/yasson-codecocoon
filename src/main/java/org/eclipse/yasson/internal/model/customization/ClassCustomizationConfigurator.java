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

import jakarta.json.bind.config.PropertyVisibilityStrategy;

import org.eclipse.yasson.internal.model.JsonbCreatorInvoker;
import org.eclipse.yasson.internal.serializer.JsonbDateTimeFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumericFormatter;

/**
 * The customization builder that would be used to build an instance of {@link ClassSerializationConfig} to ensure its immutability.
 */
public class ClassCustomizationConfigurator extends SerializationCustomizationBuilder {

    private JsonbCreatorInvoker jsonbInvoker;

    /**
     * The class level number formatter that would be used by default for all number properties that don't have a dedicated
     * number formatter
     * annotation.
     */
    private JsonbNumericFormatter numericFormatter;

    /**
     * The class level date formatter that would be used by default for all date properties that don't have a dedicated date
     * formatter annotation.
     */
    private JsonbDateTimeFormatter dateTimeFormatter;

    /**
     * The class or package level property visibility strategy.
     */
    private PropertyVisibilityStrategy visibilityStrategy;

    /**
     * Property visibility strategy for given class.
     *
     * @return property visibility strategy
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy() {
        return visibilityStrategy;
    }

    /**
     * Gets custom constructor or method for user instantiation.
     *
     * @return Custom creator.
     */
    public JsonbCreatorInvoker getCreator() {
        return jsonbInvoker;
    }

    /**
     * Sets custom property visibility strategy.
     *
     * @param visibilityStrategy strategy
     */
    public void setPropertyVisibilityStrategy(PropertyVisibilityStrategy visibilityStrategy) {
        this.visibilityStrategy = visibilityStrategy;
    }

    /**
     * Sets date format for formatting dates.
     *
     * @param dateTimeFormatter Date format.
     */
    public void setDateFormatter(JsonbDateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
    }

    /**
     * Returns the default number formatter instance that would be used for all number properties that don't have a dedicated
     * number formatter.
     *
     * @return the default number formatter instance that would be used for all number properties that don't have a dedicated
     * number formatter
     */
    public JsonbNumericFormatter getNumberFormatter() {
        return numericFormatter;
    }

    /**
     * Gets a date format for formatting dates.
     *
     * @return Date format.
     */
    public JsonbDateTimeFormatter getDateFormatter() {
        return dateTimeFormatter;
    }

    /**
     * Sets custom constructor or method for user instantiation.
     *
     * @param jsonbInvoker Creator to set.
     */
    public void setCreator(JsonbCreatorInvoker jsonbInvoker) {
        this.jsonbInvoker = jsonbInvoker;
    }

    /**
     * Creates a customization for class properties.
     *
     * @return A new instance of {@link PropertyCustomization}
     */
    public ClassSerializationConfig buildClassSerializationConfig() {
        return new ClassSerializationConfig(this);
    }

    /**
     * Sets the default number formatter instance that would be used for all number properties that don't have a dedicated
     * number formatter.
     *
     * @param numericFormatter the default number formatter instance that would be used for all number properties that don't
     *                        have a dedicated number
     *                        formatter.
     */
    public void setNumberFormatter(JsonbNumericFormatter numericFormatter) {
        this.numericFormatter = numericFormatter;
    }

}

/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
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
 * Customization, which could be applied on a class or package level.
 */
public class ClassSerializationConfig extends CustomizationBase {

    private final JsonbCreatorInvoker instanceFactory;

    private String[] fieldSequence;

    private final JsonbNumericFormatter numericFormatter;

    private final JsonbDateTimeFormatter dateTimeFormat;

    private final PropertyVisibilityStrategy visibilityStrategy;

    @Override
    public JsonbDateTimeFormatter getSerializeDateFormatter() {
        return dateTimeFormat;
    }

    @Override
    public JsonbDateTimeFormatter getDeserializeDateFormatter() {
        return dateTimeFormat;
    }

    /**
     * Copy constructor.
     *
     * @param sourceConfig other customization instance
     */
    public ClassSerializationConfig(ClassSerializationConfig sourceConfig) {
        super(sourceConfig);
        this.instanceFactory = sourceConfig.getCreator();
        this.fieldSequence = sourceConfig.getPropertyOrder();
        this.numericFormatter = sourceConfig.getSerializeNumberFormatter();
        this.dateTimeFormat = sourceConfig.getSerializeDateFormatter();
        this.visibilityStrategy = sourceConfig.getPropertyVisibilityStrategy();
    }

    @Override
    public JsonbNumericFormatter getDeserializeNumberFormatter() {
        return numericFormatter;
    }

    /**
     * Sets sorted properties.
     *
     * @param fieldSequence sorted names of properties
     */
    public void setPropertyOrder(String[] fieldSequence) {
        this.fieldSequence = fieldSequence;
    }

    /**
     * Names of properties to sort with.
     *
     * @return sorted names of properties
     */
    public String[] getPropertyOrder() {
        return fieldSequence;
    }

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param configurator not null
     */
    ClassSerializationConfig(ClassCustomizationConfigurator configurator) {
        super(configurator);
        this.instanceFactory = configurator.getCreator();
        this.fieldSequence = configurator.getPropertyOrder();
        this.numericFormatter = configurator.getNumberFormatter();
        this.dateTimeFormat = configurator.getDateFormatter();
        this.visibilityStrategy = configurator.getPropertyVisibilityStrategy();
    }

    /**
     * Returns instance of {@link JsonbCreatorInvoker}.
     *
     * @return instance of creator
     */
    public JsonbCreatorInvoker getCreator() {
        return instanceFactory;
    }

    /**
     * Property visibility strategy for this class model.
     *
     * @return visibility strategy
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy() {
        return visibilityStrategy;
    }

    @Override
    public JsonbNumericFormatter getSerializeNumberFormatter() {
        return numericFormatter;
    }

}

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

import org.eclipse.yasson.internal.model.JsonbInstantiator;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * Customization, which could be applied on a class or package level.
 */
public class ClassSerializationConfig extends CustomizationBase {

    private final JsonbInstantiator instantiator;

    private String[] propertySequence;

    private final JsonbNumberFormatter numericFormatter;

    private final JsonbDateFormatter dateFormatter;

    private final PropertyVisibilityStrategy visibilityStrategy;

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param classCustomizer not null
     */
    ClassSerializationConfig(ClassCustomizationBuilder classCustomizer) {
        super(classCustomizer);
        this.instantiator = classCustomizer.getCreator();
        this.propertySequence = classCustomizer.getPropertyOrder();
        this.numericFormatter = classCustomizer.getNumberFormatter();
        this.dateFormatter = classCustomizer.getDateFormatter();
        this.visibilityStrategy = classCustomizer.getPropertyVisibilityStrategy();
    }

    /**
     * Copy constructor.
     *
     * @param sourceConfig other customization instance
     */
    public ClassSerializationConfig(ClassSerializationConfig sourceConfig) {
        super(sourceConfig);
        this.instantiator = sourceConfig.getCreator();
        this.propertySequence = sourceConfig.getPropertyOrder();
        this.numericFormatter = sourceConfig.getSerializeNumberFormatter();
        this.dateFormatter = sourceConfig.getSerializeDateFormatter();
        this.visibilityStrategy = sourceConfig.getPropertyVisibilityStrategy();
    }

    /**
     * Returns instance of {@link JsonbInstantiator}.
     *
     * @return instance of creator
     */
    public JsonbInstantiator getCreator() {
        return instantiator;
    }

    /**
     * Names of properties to sort with.
     *
     * @return sorted names of properties
     */
    public String[] getPropertyOrder() {
        return propertySequence;
    }

    /**
     * Sets sorted properties.
     *
     * @param propertySequence sorted names of properties
     */
    public void setPropertyOrder(String[] propertySequence) {
        this.propertySequence = propertySequence;
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
    public JsonbNumberFormatter getSerializeNumberFormatter() {
        return numericFormatter;
    }

    @Override
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        return numericFormatter;
    }

    @Override
    public JsonbDateFormatter getSerializeDateFormatter() {
        return dateFormatter;
    }

    @Override
    public JsonbDateFormatter getDeserializeDateFormatter() {
        return dateFormatter;
    }

}

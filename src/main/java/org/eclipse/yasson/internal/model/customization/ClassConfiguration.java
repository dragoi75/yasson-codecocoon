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
public class ClassConfiguration extends CustomizationBase {

    private final JsonbInstantiator instantiator;

    private String[] propertySequence;

    private final JsonbNumberFormatter numericFormatter;

    private final JsonbDateFormatter dateTimeFormat;

    private final PropertyVisibilityStrategy visibilityStrategy;

    /**
     * Property visibility strategy for this class model.
     *
     * @return visibility strategy
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy() {
        return visibilityStrategy;
    }

    @Override
    public JsonbDateFormatter getDeserializeDateFormatter() {
        return dateTimeFormat;
    }

    @Override
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        return numericFormatter;
    }

    /**
     * Names of properties to sort with.
     *
     * @return sorted names of properties
     */
    public String[] getPropertyOrder() {
        return propertySequence;
    }

    @Override
    public JsonbNumberFormatter getSerializeNumberFormatter() {
        return numericFormatter;
    }

    /**
     * Returns instance of {@link JsonbInstantiator}.
     *
     * @return instance of creator
     */
    public JsonbInstantiator getCreator() {
        return instantiator;
    }

    @Override
    public JsonbDateFormatter getSerializeDateFormatter() {
        return dateTimeFormat;
    }

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param customizationFactory not null
     */
    ClassConfiguration(ClassCustomizationBuilder customizationFactory) {
        super(customizationFactory);
        this.instantiator = customizationFactory.getCreator();
        this.propertySequence = customizationFactory.getPropertyOrder();
        this.numericFormatter = customizationFactory.getNumberFormatter();
        this.dateTimeFormat = customizationFactory.getDateFormatter();
        this.visibilityStrategy = customizationFactory.getPropertyVisibilityStrategy();
    }

    /**
     * Copy constructor.
     *
     * @param sourceConfiguration other customization instance
     */
    public ClassConfiguration(ClassConfiguration sourceConfiguration) {
        super(sourceConfiguration);
        this.instantiator = sourceConfiguration.getCreator();
        this.propertySequence = sourceConfiguration.getPropertyOrder();
        this.numericFormatter = sourceConfiguration.getSerializeNumberFormatter();
        this.dateTimeFormat = sourceConfiguration.getSerializeDateFormatter();
        this.visibilityStrategy = sourceConfiguration.getPropertyVisibilityStrategy();
    }

    /**
     * Sets sorted properties.
     *
     * @param propertySequence sorted names of properties
     */
    public void setPropertyOrder(String[] propertySequence) {
        this.propertySequence = propertySequence;
    }

}

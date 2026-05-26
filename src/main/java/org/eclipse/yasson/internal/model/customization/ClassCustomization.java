/*******************************************************************************
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;
import org.eclipse.yasson.internal.model.JsonbCreator;

/**
 * Customization, which could be applied on a class or package level.
 *
 * @author Roman Grigoriadi
 */
public class ClassCustomization extends CustomizationBase {

    private final JsonbCreator creator;

    private String[] propertyOrder;

    private final JsonbNumberFormatter numberFormatter;

    private final JsonbDateFormatter dateTimeFormatter;

    @Override
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        return numberFormatter;
    }

    /**
     * Sets sorted properties.
     *
     * @param propertyOrder sorted names of properties
     */
    public void setPropertyOrder(String[] propertyOrder) {
        this.propertyOrder = propertyOrder;
    }

    @Override
    public JsonbDateFormatter getSerializeDateFormatter() {
        return dateTimeFormatter;
    }

    @Override
    public JsonbDateFormatter getDeserializeDateFormatter() {
        return dateTimeFormatter;
    }

    /**
     * Returns instance of {@link JsonbCreator}.
     *
     * @return instance of creator
     */
    public JsonbCreator getCreator() {
        return creator;
    }

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param builder not null
     */
    ClassCustomization(ClassCustomizationBuilder builder) {
        super(builder);
        this.creator = builder.getCreator();
        this.propertyOrder = builder.getPropertyOrder();
        this.numberFormatter = builder.getNumberFormatter();
        this.dateTimeFormatter = builder.getDateFormatter();
    }

    @Override
    public JsonbNumberFormatter getSerializeNumberFormatter() {
        return numberFormatter;
    }

    /**
     * Names of properties to sort with.
     *
     * @return sorted names of properties
     */
    public String[] getPropertyOrder() {
        return propertyOrder;
    }

    /**
     * Copy constructor.
     *
     * @param other other customization instance
     */
    public ClassCustomization(ClassCustomization other) {
        super(other);
        this.creator = other.getCreator();
        this.propertyOrder = other.getPropertyOrder();
        this.numberFormatter = other.getSerializeNumberFormatter();
        this.dateTimeFormatter = other.getSerializeDateFormatter();
    }

}

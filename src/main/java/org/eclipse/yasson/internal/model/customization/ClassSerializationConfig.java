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

import javax.json.bind.config.PropertyVisibilityStrategy;

/**
 * Customization, which could be applied on a class or package level.
 *
 * @author Roman Grigoriadi
 */
public class ClassSerializationConfig extends CustomizationBase {

    private final JsonbCreator instanceFactory;

    private String[] fieldSequence;

    private final JsonbNumberFormatter decimalFormatter;

    private final JsonbDateFormatter timestampFormatter;

    private final PropertyVisibilityStrategy fieldVisibilityPolicy;

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param classCustomizer not null
     */
    ClassSerializationConfig(ClassCustomizationBuilder classCustomizer) {
        super(classCustomizer);
        this.instanceFactory = classCustomizer.getCreator();
        this.fieldSequence = classCustomizer.getPropertyOrder();
        this.decimalFormatter = classCustomizer.getNumberFormatter();
        this.timestampFormatter = classCustomizer.getDateFormatter();
        this.fieldVisibilityPolicy = classCustomizer.getPropertyVisibilityStrategy();
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
        this.decimalFormatter = sourceConfig.getSerializeNumberFormatter();
        this.timestampFormatter = sourceConfig.getSerializeDateFormatter();
        this.fieldVisibilityPolicy = sourceConfig.getPropertyVisibilityStrategy();
    }

    /**
     * Returns instance of {@link JsonbCreator}.
     *
     * @return instance of creator
     */
    public JsonbCreator getCreator() {
        return instanceFactory;
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
     * Sets sorted properties.
     *
     * @param fieldSequence sorted names of properties
     */
    public void setPropertyOrder(String[] fieldSequence) {
        this.fieldSequence = fieldSequence;
    }

    /**
     * Property visibility strategy for this class model.
     * @return visibility strategy
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy() {
        return fieldVisibilityPolicy;
    }

    @Override
    public JsonbNumberFormatter getSerializeNumberFormatter() {
        return decimalFormatter;
    }

    @Override
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        return decimalFormatter;
    }

    @Override
    public JsonbDateFormatter getSerializeDateFormatter() {
        return timestampFormatter;
    }

    @Override
    public JsonbDateFormatter getDeserializeDateFormatter() {
        return timestampFormatter;
    }

}

/*
 * Copyright (c) 2015, 2022 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.JsonbDateFormatter;
import org.eclipse.yasson.internal.JsonbNumberFormatter;
import org.eclipse.yasson.internal.model.JsonbCreatorInvoker;

/**
 * Customization which could be applied on a class or package level.
 */
public class ClassCustomization extends CustomizationBase {

    private static final ClassCustomization EMPTY = new ClassCustomization(new Builder());

    private final JsonbCreatorInvoker creator;
    private final String[] propertyOrder;
    private final JsonbNumberFormatter numberFormatter;
    private final JsonbDateFormatter dateTimeFormatter;
    private final PropertyVisibilityStrategy propertyVisibilityStrategy;
    private final TypeInheritanceConfiguration typeInheritanceConfiguration;

    /**
     * The customization builder that would be used to build an instance of {@link ClassCustomization} to ensure its immutability.
     */
    public static class Builder extends CustomizationBase.Builder<Builder, ClassCustomization> {

        private JsonbCreatorInvoker creator;
        private String[] propertyOrder;
        private JsonbNumberFormatter numberFormatter;
        private JsonbDateFormatter dateTimeFormatter;
        private PropertyVisibilityStrategy propertyVisibilityStrategy;
        private TypeInheritanceConfiguration typeInheritanceConfiguration;

        public Builder propertyVisibilityStrategy(PropertyVisibilityStrategy propertyVisibilityStrategy) {
            this.propertyVisibilityStrategy = propertyVisibilityStrategy;
            return this;
        }

        public Builder dateTimeFormatter(JsonbDateFormatter dateTimeFormatter) {
            this.dateTimeFormatter = dateTimeFormatter;
            return this;
        }

        @Override
        public Builder of(ClassCustomization customization) {
            super.of(customization);
            creator(customization.creator);
            propertyOrder(customization.propertyOrder);
            numberFormatter(customization.numberFormatter);
            dateTimeFormatter(customization.dateTimeFormatter);
            propertyVisibilityStrategy(customization.propertyVisibilityStrategy);
            return this;
        }

        public Builder polymorphismConfig(TypeInheritanceConfiguration typeInheritanceConfiguration) {
            this.typeInheritanceConfiguration = typeInheritanceConfiguration;
            return this;
        }

        @Override
        public ClassCustomization build() {
            return new ClassCustomization(this);
        }

        private Builder() {
        }

        public Builder propertyOrder(String[] propertyOrder) {
            this.propertyOrder = propertyOrder;
            return this;
        }

        public Builder creator(JsonbCreatorInvoker creator) {
            this.creator = creator;
            return this;
        }

        public Builder numberFormatter(JsonbNumberFormatter numberFormatter) {
            this.numberFormatter = numberFormatter;
            return this;
        }

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
     * Returns instance of {@link JsonbCreatorInvoker}.
     *
     * @return instance of creator
     */
    public JsonbCreatorInvoker getCreator() {
        return creator;
    }

    public TypeInheritanceConfiguration getPolymorphismConfig() {
        return typeInheritanceConfiguration;
    }

    @Override
    public JsonbDateFormatter getDeserializeDateFormatter() {
        return dateTimeFormatter;
    }

    @Override
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        return numberFormatter;
    }

    /**
     * Property visibility strategy for this class model.
     *
     * @return visibility strategy
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy() {
        return propertyVisibilityStrategy;
    }

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param builder not null
     */
    private ClassCustomization(Builder builder) {
        super(builder);
        this.creator = builder.creator;
        this.propertyOrder = builder.propertyOrder;
        this.numberFormatter = builder.numberFormatter;
        this.dateTimeFormatter = builder.dateTimeFormatter;
        this.propertyVisibilityStrategy = builder.propertyVisibilityStrategy;
        this.typeInheritanceConfiguration = builder.typeInheritanceConfiguration;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public JsonbDateFormatter getSerializeDateFormatter() {
        return dateTimeFormatter;
    }

    public static ClassCustomization empty() {
        return EMPTY;
    }

}

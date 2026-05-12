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
import org.eclipse.yasson.internal.model.JsonbCreator;

/**
 * Customization which could be applied on a class or package level.
 */
public class ClassSerializationConfig extends CustomizationBase {

    private static final ClassSerializationConfig DEFAULT_INSTANCE = new ClassSerializationConfig(new BindingBuilder());

    private final JsonbCreator factory;
    private final String[] fieldOrder;
    private final JsonbNumberFormatter numericFormatter;
    private final JsonbDateFormatter temporalFormatter;
    private final PropertyVisibilityStrategy fieldVisibilityPolicy;
    private final TypeInheritanceSettings inheritanceSettings;

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param binding not null
     */
    private ClassSerializationConfig(BindingBuilder binding) {
        super(binding);
        this.factory = binding.factory;
        this.fieldOrder = binding.fieldOrder;
        this.numericFormatter = binding.numericFormatter;
        this.temporalFormatter = binding.temporalFormatter;
        this.fieldVisibilityPolicy = binding.fieldVisibilityPolicy;
        this.inheritanceSettings = binding.inheritanceSettings;
    }

    public static ClassSerializationConfig emptyConfig() {
        return DEFAULT_INSTANCE;
    }

    public static BindingBuilder newBuilder() {
        return new BindingBuilder();
    }

    /**
     * Returns instance of {@link JsonbCreator}.
     *
     * @return instance of creator
     */
    public JsonbCreator getCreator() {
        return factory;
    }

    /**
     * Names of properties to sort with.
     *
     * @return sorted names of properties
     */
    public String[] getPropertyOrder() {
        return fieldOrder;
    }

    /**
     * Property visibility strategy for this class model.
     *
     * @return visibility strategy
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy() {
        return fieldVisibilityPolicy;
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
        return temporalFormatter;
    }

    @Override
    public JsonbDateFormatter getDeserializeDateFormatter() {
        return temporalFormatter;
    }

    public TypeInheritanceSettings getPolymorphismConfig() {
        return inheritanceSettings;
    }

    /**
     * The customization builder that would be used to build an instance of {@link ClassSerializationConfig} to ensure its immutability.
     */
    public static class BindingBuilder extends CustomizationBase.Builder<BindingBuilder, ClassSerializationConfig> {

        private JsonbCreator factory;
        private String[] fieldOrder;
        private JsonbNumberFormatter numericFormatter;
        private JsonbDateFormatter temporalFormatter;
        private PropertyVisibilityStrategy fieldVisibilityPolicy;
        private TypeInheritanceSettings inheritanceSettings;

        private BindingBuilder() {
        }

        @Override
        public ClassSerializationConfig.BindingBuilder of(ClassSerializationConfig config) {
            super.of(config);
            withCreator(config.factory);
            withPropertyOrder(config.fieldOrder);
            withNumberFormatter(config.numericFormatter);
            withDateTimeFormatter(config.temporalFormatter);
            withPropertyVisibilityStrategy(config.fieldVisibilityPolicy);
            return this;
        }

        public BindingBuilder withCreator(JsonbCreator factory) {
            this.factory = factory;
            return this;
        }

        public BindingBuilder withPropertyOrder(String[] fieldOrder) {
            this.fieldOrder = fieldOrder;
            return this;
        }

        public BindingBuilder withNumberFormatter(JsonbNumberFormatter numericFormatter) {
            this.numericFormatter = numericFormatter;
            return this;
        }

        public BindingBuilder withDateTimeFormatter(JsonbDateFormatter temporalFormatter) {
            this.temporalFormatter = temporalFormatter;
            return this;
        }

        public BindingBuilder withPropertyVisibilityStrategy(PropertyVisibilityStrategy fieldVisibilityPolicy) {
            this.fieldVisibilityPolicy = fieldVisibilityPolicy;
            return this;
        }

        public BindingBuilder withPolymorphismConfig(TypeInheritanceSettings inheritanceSettings) {
            this.inheritanceSettings = inheritanceSettings;
            return this;
        }

        @Override
        public ClassSerializationConfig build() {
            return new ClassSerializationConfig(this);
        }

    }

}

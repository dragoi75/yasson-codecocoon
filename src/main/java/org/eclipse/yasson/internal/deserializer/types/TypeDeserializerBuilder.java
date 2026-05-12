/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.deserializer.types;

import java.util.Objects;

import org.eclipse.yasson.internal.JsonbConfigurationProperties;
import org.eclipse.yasson.internal.deserializer.ModelParser;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.model.customization.SerializationCustomizer;

class TypeDeserializerBuilder {

    private final Class<?> clazz;
    private final SerializationCustomizer customization;
    private final JsonbConfigurationProperties configProperties;
    private final ModelParser<Object> delegate;

    TypeDeserializerBuilder(Class<?> clazz,
                            SerializationCustomizer customization,
                            JsonbConfigurationProperties configProperties,
                            ModelParser<Object> delegate) {
        this.clazz = Objects.requireNonNull(clazz);
        this.customization = customization == null ? ClassSerializationConfig.emptyConfig() : customization;
        this.configProperties = configProperties;
        this.delegate = Objects.requireNonNull(delegate);
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public JsonbConfigurationProperties getConfigProperties() {
        return configProperties;
    }

    public ModelParser<Object> getDelegate() {
        return delegate;
    }

    public SerializationCustomizer getCustomization() {
        return customization;
    }

}

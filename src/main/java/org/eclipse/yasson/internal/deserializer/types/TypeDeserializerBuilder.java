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

import org.eclipse.yasson.internal.JsonbConfigProperties;
import org.eclipse.yasson.internal.deserializer.ModelUnmarshaller;
import org.eclipse.yasson.internal.model.customization.ClassCustomization;
import org.eclipse.yasson.internal.model.customization.Customization;

class TypeDeserializerBuilder {

    private final Class<?> clazz;
    private final Customization customization;
    private final JsonbConfigProperties configProperties;
    private final ModelUnmarshaller<Object> delegate;

    public ModelUnmarshaller<Object> getDelegate() {
        return delegate;
    }

    public Customization getCustomization() {
        return customization;
    }

    TypeDeserializerBuilder(Class<?> clazz,
                            Customization customization,
                            JsonbConfigProperties configProperties,
                            ModelUnmarshaller<Object> delegate) {
        this.clazz = Objects.requireNonNull(clazz);
        this.customization = customization == null ? ClassCustomization.empty() : customization;
        this.configProperties = configProperties;
        this.delegate = Objects.requireNonNull(delegate);
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public JsonbConfigProperties getConfigProperties() {
        return configProperties;
    }

}

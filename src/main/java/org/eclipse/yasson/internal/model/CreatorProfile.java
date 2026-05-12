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

package org.eclipse.yasson.internal.model;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

import org.eclipse.yasson.internal.JsonbAnnotationIntrospector;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.model.customization.ClassCustomizationBuilder;
import org.eclipse.yasson.internal.model.customization.CreatorConfiguration;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * Parameter for creator constructor / method model.
 */
public class CreatorProfile {

    private final String displayLabel;

    private final Type kind;

    private final CreatorConfiguration profileConfig;

    /**
     * Creates a new instance.
     *
     * @param displayLabel      Parameter name
     * @param arg constructor parameter
     * @param runtimeEnv   jsonb context
     */
    public CreatorProfile(String displayLabel, Parameter arg, JsonbRuntimeContext runtimeEnv) {
        this.displayLabel = displayLabel;
        this.kind = arg.getParameterizedType();

        JsonbAnnotationIntrospector introspectTool = runtimeEnv.getAnnotationIntrospector();

        JsonbAnnotationHolder<Parameter> annotationHolder = new JsonbAnnotationHolder<>(arg);
        JsonbNumberFormatter numericFormatter = runtimeEnv.getAnnotationIntrospector()
                .getConstructorNumberFormatter(annotationHolder);
        JsonbDateFormatter dateFormatter = runtimeEnv.getAnnotationIntrospector().getConstructorDateFormatter(annotationHolder);
        final JsonbAnnotationHolder<Class<?>> typeAnnotationHolder = introspectTool.gatherAnnotations(arg.getType());
        final ClassCustomizationBuilder classCustomizer = new ClassCustomizationBuilder();
        classCustomizer.setAdapterInfo(introspectTool.getAdapterBinding(typeAnnotationHolder));
        classCustomizer.setDeserializerBinding(introspectTool.getDeserializerBinding(typeAnnotationHolder));
        classCustomizer.setSerializerBinding(introspectTool.getSerializerBinding(typeAnnotationHolder));
        this.profileConfig = new CreatorConfiguration(classCustomizer, numericFormatter, dateFormatter);
    }

    /**
     * Gets parameter name.
     *
     * @return Parameter name.
     */
    public String getName() {
        return displayLabel;
    }

    public CreatorConfiguration getCustomization() {
        return profileConfig;
    }

    /**
     * Gets parameter type.
     *
     * @return Parameter type.
     */
    public Type getType() {
        return kind;
    }

}

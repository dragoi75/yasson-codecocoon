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
import org.eclipse.yasson.internal.model.customization.ClassCustomizationConfigurator;
import org.eclipse.yasson.internal.model.customization.CreatorCustomization;
import org.eclipse.yasson.internal.serializer.JsonbDateTimeFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumericFormatter;

/**
 * Parameter for creator constructor / method model.
 */
public class CreatorProfile {

    private final String displayLabel;

    private final Type valueKind;

    private final CreatorCustomization profileCustomization;

    /**
     * Gets parameter type.
     *
     * @return Parameter type.
     */
    public Type getType() {
        return valueKind;
    }

    public CreatorCustomization getCustomization() {
        return profileCustomization;
    }

    /**
     * Gets parameter name.
     *
     * @return Parameter name.
     */
    public String getName() {
        return displayLabel;
    }

    /**
     * Creates a new instance.
     *
     * @param displayLabel      Parameter name
     * @param paramInfo constructor parameter
     * @param jsonbRuntime   jsonb context
     */
    public CreatorProfile(String displayLabel, Parameter paramInfo, JsonbRuntimeContext jsonbRuntime) {
        this.displayLabel = displayLabel;
        this.valueKind = paramInfo.getParameterizedType();

        JsonbAnnotationIntrospector annotationInspector = jsonbRuntime.getAnnotationIntrospector();

        JsonbAnnotationHolder<Parameter> annotationHolder = new JsonbAnnotationHolder<>(paramInfo);
        JsonbNumericFormatter numericFormatter = jsonbRuntime.getAnnotationIntrospector()
                .getConstructorNumberFormatter(annotationHolder);
        JsonbDateTimeFormatter dateTimeFormatter = jsonbRuntime.getAnnotationIntrospector().getConstructorDateFormatter(annotationHolder);
        final JsonbAnnotationHolder<Class<?>> classAnnotationHolder = annotationInspector.gatherAnnotations(paramInfo.getType());
        final ClassCustomizationConfigurator configurator = new ClassCustomizationConfigurator();
        configurator.setAdapterInfo(annotationInspector.getAdapterBinding(classAnnotationHolder));
        configurator.setDeserializerBinding(annotationInspector.getDeserializerBinding(classAnnotationHolder));
        configurator.setSerializerBinding(annotationInspector.getSerializerBinding(classAnnotationHolder));
        this.profileCustomization = new CreatorCustomization(configurator, numericFormatter, dateTimeFormatter);
    }

}

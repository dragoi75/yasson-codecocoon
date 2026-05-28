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
import org.eclipse.yasson.internal.JsonbContextManager;
import org.eclipse.yasson.internal.model.customization.ClassCustomizationBuilder;
import org.eclipse.yasson.internal.model.customization.CreatorCustomizer;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * Parameter for creator constructor / method model.
 */
public class CreatorProfile {

    private final String displayLabel;

    private final Type category;

    private final CreatorCustomizer profileCustomizer;

    /**
     * Gets parameter type.
     *
     * @return Parameter type.
     */
    public Type getType() {
        return category;
    }

    public CreatorCustomizer getCustomization() {
        return profileCustomizer;
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
     * @param constructorArg constructor parameter
     * @param jsonbManager   jsonb context
     */
    public CreatorProfile(String displayLabel, Parameter constructorArg, JsonbContextManager jsonbManager) {
        this.displayLabel = displayLabel;
        this.category = constructorArg.getParameterizedType();

        JsonbAnnotationIntrospector introspector = jsonbManager.getAnnotationIntrospector();

        JsonbAnnotatedMember<Parameter> paramMember = new JsonbAnnotatedMember<>(constructorArg);
        JsonbNumberFormatter numberFormatter = jsonbManager.getAnnotationIntrospector()
                .getConstructorNumberFormatter(paramMember);
        JsonbDateFormatter dateFormatter = jsonbManager.getAnnotationIntrospector().getConstructorDateFormatter(paramMember);
        final JsonbAnnotatedMember<Class<?>> classMember = introspector.gatherAnnotations(constructorArg.getType());
        final ClassCustomizationBuilder customizationConfig = new ClassCustomizationBuilder();
        customizationConfig.setAdapterInfo(introspector.getAdapterBinding(classMember));
        customizationConfig.setDeserializerBinding(introspector.getDeserializerBinding(classMember));
        customizationConfig.setSerializerBinding(introspector.getSerializerBinding(classMember));
        this.profileCustomizer = new CreatorCustomizer(customizationConfig, numberFormatter, dateFormatter);
    }

}

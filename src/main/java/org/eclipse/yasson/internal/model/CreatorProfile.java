/*
 * Copyright (c) 2016, 2022 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import org.eclipse.yasson.internal.AnnotationIntrospector;
import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.JsonbDateFormatter;
import org.eclipse.yasson.internal.JsonbNumberFormatter;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.model.customization.CreatorCustomization;

/**
 * Parameter for creator constructor / method model.
 */
public class CreatorProfile {

    private final String label;

    private final Type kind;

    private final CreatorCustomization creationConfig;

    /**
     * Creates a new instance.
     *  @param label      Parameter name
     * @param methodArgument constructor parameter
     * @param invokable creator executable
     * @param jsonbScope   jsonb context
     */
    public CreatorProfile(String label, Parameter methodArgument, Executable invokable, JsonbContext jsonbScope) {
        this.label = label;
        this.kind = methodArgument.getParameterizedType();
        AnnotationIntrospector metadataInspector = jsonbScope.getAnnotationIntrospector();
        JsonbAnnotatedElement<Parameter> elementWrapper = new JsonbAnnotatedElement<>(methodArgument);
        boolean mandatory = jsonbScope.getAnnotationIntrospector().requiredParameters(invokable, elementWrapper);
        JsonbNumberFormatter numericFormatter = jsonbScope.getAnnotationIntrospector().getConstructorNumberFormatter(elementWrapper);
        JsonbDateFormatter dateFormatterForCtor = jsonbScope.getAnnotationIntrospector().getConstructorDateFormatter(elementWrapper);
        DeserializerBinding<?> deserializationBinding = metadataInspector.getDeserializerBinding(methodArgument);
        AdapterBinding adapterBinder = metadataInspector.getAdapterBinding(methodArgument);
        final JsonbAnnotatedElement<Class<?>> classElem = metadataInspector.collectAnnotations(methodArgument.getType());
        deserializationBinding = null == deserializationBinding ? metadataInspector.getDeserializerBinding(classElem) : deserializationBinding;
        adapterBinder = null == adapterBinder ? metadataInspector.getAdapterBinding(classElem) : adapterBinder;
        this.creationConfig = CreatorCustomization.builder().adapterBinding(adapterBinder).deserializerBinding(deserializationBinding).serializerBinding(metadataInspector.getSerializerBinding(classElem)).numberFormatter(numericFormatter).dateFormatter(dateFormatterForCtor).required(mandatory).build();
    }

    /**
     * Gets parameter name.
     *
     * @return Parameter name.
     */
    public String getName() {
        return label;
    }

    public CreatorCustomization getCustomization() {
        return creationConfig;
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

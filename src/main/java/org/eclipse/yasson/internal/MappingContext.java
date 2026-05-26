/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.yasson.internal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.JsonbAnnotationHolder;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.serializer.ContainerSerializerProvider;
import org.eclipse.yasson.internal.serializer.DefaultSerializers;

/**
 * JSONB mappingContext. Created once per {@link jakarta.json.bind.Jsonb} instance. Represents a global scope.
 * Holds internal model.
 *
 * Thread safe.
 */
public class MappingContext {

    private final JsonbRuntimeContext jsonbContext;

    private final ConcurrentHashMap<Class<?>, ClassDescriptor> classes = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Class<?>, ContainerSerializerProvider> serializers = new ConcurrentHashMap<>();

    private final ClassModelParser classParser;

    /**
     * Adds given serializer provider for given class.
     *
     * @param clazz              Class to add serializer provider for.
     * @param serializerProvider Serializer provider to add.
     */
    public void addSerializerProvider(Class<?> clazz, ContainerSerializerProvider serializerProvider) {
        serializers.putIfAbsent(clazz, serializerProvider);
    }

    /**
     * Gets serializer provider for given class.
     *
     * @param clazz Class to get serializer provider for.
     * @return Serializer provider.
     */
    public ContainerSerializerProvider getSerializerProvider(Class<?> clazz) {
        return serializers.get(clazz);
    }

    /**
     * Search for class model, without parsing if not found.
     *
     * @param clazz Class to search by or parse, not null.
     * @return Model of a class if found.
     */
    public ClassDescriptor getClassModel(Class<?> clazz) {
        return classes.get(clazz);
    }

    /**
     * Create mapping context which is scoped to jsonb runtime.
     *
     * @param jsonbContext Context. Required.
     */
    public MappingContext(JsonbRuntimeContext jsonbContext) {
        Objects.requireNonNull(jsonbContext);
        this.jsonbContext = jsonbContext;
        this.classParser = new ClassModelParser(jsonbContext);
    }

    private static Function<Class<?>, ClassDescriptor> createParseClassModelFunction(ClassDescriptor parentClassModel, ClassModelParser classParser, JsonbRuntimeContext jsonbContext) {
        return aClass -> {
            JsonbAnnotationHolder<Class<?>> clsElement = jsonbContext.getAnnotationIntrospector().gatherAnnotations(aClass);
            ClassSerializationConfig customization = jsonbContext.getAnnotationIntrospector().inspectCustomization(clsElement);
            ClassDescriptor newClassModel = new ClassDescriptor(aClass, customization, parentClassModel, jsonbContext.getConfigProperties().getPropertyNamingStrategy());
            if (!DefaultSerializers.isKnownType(aClass)) {
                classParser.extractProperties(newClassModel, clsElement);
            }
            return newClassModel;
        };
    }

    /**
     * Searches for class model for given class. Returns the existing instance. Creates a new instance if
     * it doesn't exist.
     *
     * @param clazz Class to search by or parse, not null.
     * @return {@link ClassDescriptor} for given class.
     */
    public ClassDescriptor getOrCreateClassModel(Class<?> clazz) {
        ClassDescriptor classModel = classes.get(clazz);
        if (null != classModel) {
            return classModel;
        }
        Deque<Class<?>> newClassModels = new ArrayDeque<>();
        Class<?> classToParse = clazz;
        while (Object.class != classToParse) {
            if (null == classToParse) {
                break;
            }
            newClassModels.push(classToParse);
            classToParse = classToParse.getSuperclass();
        }
        if (Object.class == clazz) {
            return classes.computeIfAbsent(clazz, (c) -> new ClassDescriptor(c, null, null, null));
        }
        ClassDescriptor parentClassModel = null;
        while (!newClassModels.isEmpty()) {
            Class<?> toParse = newClassModels.pop();
            parentClassModel = classes.computeIfAbsent(toParse, createParseClassModelFunction(parentClassModel, classParser, jsonbContext));
        }
        return classes.get(clazz);
    }

}

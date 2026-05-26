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
import org.eclipse.yasson.internal.serializer.ContainerSerializerFactory;

/**
 * JSONB mappingContext. Created once per {@link jakarta.json.bind.Jsonb} instance. Represents a global scope.
 * Holds internal model.
 *
 * Thread safe.
 */
public class ClassModelRegistry {

    private final JsonbRuntimeContext serializationContext;

    private final ConcurrentHashMap<Class<?>, ClassDescriptor> classDescriptorMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Class<?>, ContainerSerializerFactory> serializerFactoryMap = new ConcurrentHashMap<>();

    private final ClassModelParser modelParser;

    /**
     * Gets serializer provider for given class.
     *
     * @param type Class to get serializer provider for.
     * @return Serializer provider.
     */
    public ContainerSerializerFactory getSerializerProvider(Class<?> type) {
        return serializerFactoryMap.get(type);
    }

    /**
     * Create mapping context which is scoped to jsonb runtime.
     *
     * @param serializationContext Context. Required.
     */
    public ClassModelRegistry(JsonbRuntimeContext serializationContext) {
        Objects.requireNonNull(serializationContext);
        this.serializationContext = serializationContext;
        this.modelParser = new ClassModelParser(serializationContext);
    }

    /**
     * Adds given serializer provider for given class.
     *
     * @param type              Class to add serializer provider for.
     * @param provider Serializer provider to add.
     */
    public void registerSerializerProvider(Class<?> type, ContainerSerializerFactory provider) {
        serializerFactoryMap.putIfAbsent(type, provider);
    }

    /**
     * Search for class model, without parsing if not found.
     *
     * @param type Class to search by or parse, not null.
     * @return Model of a class if found.
     */
    public ClassDescriptor getClassModel(Class<?> type) {
        return classDescriptorMap.get(type);
    }

    /**
     * Searches for class model for given class. Returns the existing instance. Creates a new instance if
     * it doesn't exist.
     *
     * @param type Class to search by or parse, not null.
     * @return {@link ClassDescriptor} for given class.
     */
    public ClassDescriptor getOrCreateClassModel(Class<?> type) {
        ClassDescriptor descriptor = classDescriptorMap.get(type);
        if (null != descriptor) {
            return descriptor;
        }
        Deque<Class<?>> pendingClassesDeque = new ArrayDeque<>();
        Class<?> typeToParse = type;
        while (Object.class != typeToParse) {
            if (null == typeToParse) {
                break;
            }
            pendingClassesDeque.push(typeToParse);
            typeToParse = typeToParse.getSuperclass();
        }
        if (Object.class == type) {
            return classDescriptorMap.computeIfAbsent(type, (candidate) -> new ClassDescriptor(candidate, null, null, null));
        }
        ClassDescriptor parentDescriptor = null;
        while (!pendingClassesDeque.isEmpty()) {
            Class<?> parseTarget = pendingClassesDeque.pop();
            parentDescriptor = classDescriptorMap.computeIfAbsent(parseTarget, createClassModelParserFunction(parentDescriptor, modelParser, serializationContext));
        }
        return classDescriptorMap.get(type);
    }

    private static Function<Class<?>, ClassDescriptor> createClassModelParserFunction(ClassDescriptor parentDescriptor, ClassModelParser modelParser, JsonbRuntimeContext serializationContext) {
        return inputType -> {
            JsonbAnnotationHolder<Class<?>> classAnnotationHolder = serializationContext.getAnnotationIntrospector().gatherAnnotations(inputType);
            ClassSerializationConfig serializationConfig = serializationContext.getAnnotationIntrospector().analyzeCustomization(classAnnotationHolder);
            ClassDescriptor createdDescriptor = new ClassDescriptor(inputType, serializationConfig, parentDescriptor, serializationContext.getConfigProperties().getPropertyNamingStrategy());
            modelParser.collectProperties(createdDescriptor, classAnnotationHolder);
            return createdDescriptor;
        };
    }

}

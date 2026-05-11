/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  <p>
 *  Contributors:
 *  Dmitry Kornilov - initial implementation
 * ****************************************************************************
 */
package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.serializer.ContainerSerializerFactory;
import org.eclipse.yasson.internal.model.JsonbAnnotatedElement;
import org.eclipse.yasson.internal.model.customization.ClassCustomization;
import java.util.Iterator;
import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * JSONB mappingContext. Created once per {@link javax.json.bind.Jsonb} instance. Represents a global scope.
 * Holds internal model.
 *
 * Thread safe.
 *
 * @author Dmitry Kornilov
 * @author Roman Grigoriadi
 */
public class ClassMappingContext {

    private static class ClassModelParserFunction implements Function<Class, ClassDescriptor> {

        private ClassDescriptor parentDescriptor;

        private ClassParser descriptorParser;

        private JsonbRuntimeContext jsonbRuntime;

        public ClassModelParserFunction(ClassDescriptor parentDescriptor, ClassParser descriptorParser, JsonbRuntimeContext jsonbRuntime) {
            this.parentDescriptor = parentDescriptor;
            this.descriptorParser = descriptorParser;
            this.jsonbRuntime = jsonbRuntime;
        }

        @Override
        public ClassDescriptor apply(Class targetClass) {
            final JsonbAnnotatedElement<Class<?>> annotatedClassElement = jsonbRuntime.getAnnotationIntrospector().collectAnnotations(targetClass);
            final ClassCustomization classConfig = jsonbRuntime.getAnnotationIntrospector().introspectCustomization(annotatedClassElement);
            final ClassDescriptor createdClassDescriptor = new ClassDescriptor(targetClass, classConfig, parentDescriptor, jsonbRuntime.getConfigProperties().getPropertyNamingStrategy());
            descriptorParser.parseProperties(createdClassDescriptor, annotatedClassElement);
            return createdClassDescriptor;
        }
    }

    private final JsonbRuntimeContext jsonbRuntime;

    private final ConcurrentHashMap<Class<?>, ClassDescriptor> classModelMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Class<?>, ContainerSerializerFactory> serializerFactoryMap = new ConcurrentHashMap<>();

    private final ClassParser descriptorParser;

    /**
     * Create mapping context which is scoped to jsonb runtime.
     *
     * @param jsonbRuntime Context. Required.
     */
    public ClassMappingContext(JsonbRuntimeContext jsonbRuntime) {
        Objects.requireNonNull(jsonbRuntime);
        this.jsonbRuntime = jsonbRuntime;
        this.descriptorParser = new ClassParser(jsonbRuntime);
    }

    /**
     * Searches for class model for given class. Returns the existing instance. Creates a new instance if
     * it doesn't exist.
     *
     * @param targetType Class to search by or parse, not null.
     * @return {@link ClassDescriptor} for given class.
     */
    public ClassDescriptor getOrCreateClassModel(Class<?> targetType) {
        ClassDescriptor classDescriptor = classModelMap.get(targetType);
        if (null != classDescriptor) {
            return classDescriptor;
        }
        final Stack<Class> classStack = new Stack<>();
        Class targetClass = targetType;
        while (Object.class != targetClass) {
            if (null == targetClass) {
                break;
            }
            classStack.push(targetClass);
            targetClass = targetClass.getSuperclass();
        }
        if (Object.class == targetType) {
            classModelMap.computeIfAbsent(targetType, (cls) -> new ClassDescriptor(cls, null, null, null));
            return classModelMap.get(targetType);
        }
        ClassDescriptor parentDescriptor = null;
        while (!classStack.empty()) {
            Class parseTarget = classStack.pop();
            parentDescriptor = classModelMap.computeIfAbsent(parseTarget, new ClassModelParserFunction(parentDescriptor, descriptorParser, jsonbRuntime));
        }
        return classModelMap.get(targetType);
    }

    /**
     * Provided class class model is returned first by iterator.
     * Following class models are sorted by hierarchy from provided class up to the Object.class.
     *
     * @param targetType class to start iteration of class models from
     * @return iterator of class models
     */
    public Iterator<ClassDescriptor> classModelIterator(final Class<?> targetType) {
        return new Iterator<ClassDescriptor>() {

            private Class<?> next = targetType;

            @Override
            public boolean hasNext() {
                return Object.class != next;
            }

            @Override
            public ClassDescriptor next() {
                final ClassDescriptor result = classModelMap.get(next);
                next = next.getSuperclass();
                return result;
            }
        };
    }

    /**
     * Search for class model, without parsing if not found.
     *
     * @param targetType Class to search by or parse, not null.
     * @return Model of a class if found.
     */
    public ClassDescriptor getClassModel(Class<?> targetType) {
        return classModelMap.get(targetType);
    }

    /**
     * Gets serializer provider for given class.
     *
     * @param targetType Class to get serializer provider for.
     * @return Serializer provider.
     */
    public ContainerSerializerFactory getSerializerProvider(Class<?> targetType) {
        return serializerFactoryMap.get(targetType);
    }

    /**
     * Adds given serializer provider for given class.
     *
     * @param targetType Class to add serializer provider for.
     * @param containerFactory Serializer provider to add.
     */
    public void registerSerializerProvider(Class<?> targetType, ContainerSerializerFactory containerFactory) {
        serializerFactoryMap.putIfAbsent(targetType, containerFactory);
    }
}

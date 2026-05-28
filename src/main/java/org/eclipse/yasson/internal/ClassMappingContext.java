/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved.
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
import org.eclipse.yasson.internal.model.JsonbAnnotatedElement;
import org.eclipse.yasson.internal.model.customization.ClassCustomization;
import org.eclipse.yasson.internal.serializer.ContainerSerializerProvider;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
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

        private JsonbContext jsonbConfig;

        @Override
        public ClassDescriptor apply(Class inputClass) {
            final JsonbAnnotatedElement<Class<?>> classAnnotatedElement = jsonbConfig.getAnnotationIntrospector().collectAnnotations(inputClass);
            final ClassCustomization classConfig = jsonbConfig.getAnnotationIntrospector().introspectCustomization(classAnnotatedElement);
            final ClassDescriptor createdDescriptor = new ClassDescriptor(inputClass, classConfig, parentDescriptor, jsonbConfig.getConfigProperties().getPropertyNamingStrategy());
            descriptorParser.parseProperties(createdDescriptor, classAnnotatedElement);
            return createdDescriptor;
        }

        public ClassModelParserFunction(ClassDescriptor parentDescriptor, ClassParser descriptorParser, JsonbContext jsonbConfig) {
            this.parentDescriptor = parentDescriptor;
            this.descriptorParser = descriptorParser;
            this.jsonbConfig = jsonbConfig;
        }

    }

    private final JsonbContext jsonbConfig;

    private final ConcurrentHashMap<Class<?>, ClassDescriptor> classMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Class<?>, ContainerSerializerProvider> serializerProviderMap = new ConcurrentHashMap<>();

    private final ClassParser descriptorParser;

    /**
     * Adds given serializer provider for given class.
     *
     * @param targetClass Class to add serializer provider for.
     * @param provider Serializer provider to add.
     */
    public void registerSerializerProvider(Class<?> targetClass, ContainerSerializerProvider provider) {
        serializerProviderMap.putIfAbsent(targetClass, provider);
    }

    /**
     * Create mapping context which is scoped to jsonb runtime.
     *
     * @param jsonbConfig Context. Required.
     */
    public ClassMappingContext(JsonbContext jsonbConfig) {
        Objects.requireNonNull(jsonbConfig);
        this.jsonbConfig = jsonbConfig;
        this.descriptorParser = new ClassParser(jsonbConfig);
    }

    /**
     * Gets serializer provider for given class.
     *
     * @param targetClass Class to get serializer provider for.
     * @return Serializer provider.
     */
    public ContainerSerializerProvider getSerializerProvider(Class<?> targetClass) {
        return serializerProviderMap.get(targetClass);
    }

    /**
     * Provided class class model is returned first by iterator.
     * Following class models are sorted by hierarchy from provided class up to the Object.class.
     *
     * @param targetClass class to start iteration of class models from
     * @return iterator of class models
     */
    public Iterator<ClassDescriptor> classModelIterator(final Class<?> targetClass) {
        return new Iterator<ClassDescriptor>() {

            private Class<?> next = targetClass;

            @Override
            public boolean hasNext() {
                return Object.class != next;
            }

            @Override
            public ClassDescriptor next() {
                final ClassDescriptor result = classMap.get(next);
                next = next.getSuperclass();
                return result;
            }
        };
    }

    /**
     * Searches for class model for given class. Returns the existing instance. Creates a new instance if
     * it doesn't exist.
     *
     * @param targetClass Class to search by or parse, not null.
     * @return {@link ClassDescriptor} for given class.
     */
    public ClassDescriptor getOrCreateClassModel(Class<?> targetClass) {
        ClassDescriptor classDescriptor = classMap.get(targetClass);
        if (null != classDescriptor) {
            return classDescriptor;
        }
        final Deque<Class> classDeque = new ArrayDeque<>();
        Class candidateClass = targetClass;
        while (Object.class != candidateClass) {
            if (null == candidateClass) {
                break;
            }
            classDeque.push(candidateClass);
            candidateClass = candidateClass.getSuperclass();
        }
        if (Object.class == targetClass) {
            classMap.computeIfAbsent(targetClass, (clazzParam) -> new ClassDescriptor(clazzParam, null, null, null));
            return classMap.get(targetClass);
        }
        ClassDescriptor parentDescriptor = null;
        while (!classDeque.isEmpty()) {
            Class classToProcess = classDeque.pop();
            parentDescriptor = classMap.computeIfAbsent(classToProcess, new ClassModelParserFunction(parentDescriptor, descriptorParser, jsonbConfig));
        }
        return classMap.get(targetClass);
    }

    /**
     * Search for class model, without parsing if not found.
     *
     * @param targetClass Class to search by or parse, not null.
     * @return Model of a class if found.
     */
    public ClassDescriptor getClassModel(Class<?> targetClass) {
        return classMap.get(targetClass);
    }

}

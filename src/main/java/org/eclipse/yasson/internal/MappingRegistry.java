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
public class MappingRegistry {

    private static class ClassModelParserFunction implements Function<Class, ClassDescriptor> {

        private ClassDescriptor parentClassDescriptor;

        private ClassParser classModelParser;

        private JsonbConfigurationContext jsonbConfig;

        @Override
        public ClassDescriptor apply(Class targetClass) {
            final JsonbAnnotatedElement<Class<?>> classElement = jsonbConfig.getAnnotationIntrospector().collectAnnotations(targetClass);
            final ClassCustomization classCustomizer = jsonbConfig.getAnnotationIntrospector().introspectCustomization(classElement);
            final ClassDescriptor createdClassDescriptor = new ClassDescriptor(targetClass, classCustomizer, parentClassDescriptor, jsonbConfig.getConfigProperties().getPropertyNamingStrategy());
            classModelParser.parseProperties(createdClassDescriptor, classElement);
            return createdClassDescriptor;
        }

        public ClassModelParserFunction(ClassDescriptor parentClassDescriptor, ClassParser classModelParser, JsonbConfigurationContext jsonbConfig) {
            this.parentClassDescriptor = parentClassDescriptor;
            this.classModelParser = classModelParser;
            this.jsonbConfig = jsonbConfig;
        }

    }

    private final JsonbConfigurationContext jsonbConfig;

    private final ConcurrentHashMap<Class<?>, ClassDescriptor> classModels = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Class<?>, ContainerSerializerFactory> serializerFactories = new ConcurrentHashMap<>();

    private final ClassParser classModelParser;

    /**
     * Search for class model, without parsing if not found.
     *
     * @param targetClass Class to search by or parse, not null.
     * @return Model of a class if found.
     */
    public ClassDescriptor getClassModel(Class<?> targetClass) {
        return classModels.get(targetClass);
    }

    /**
     * Adds given serializer provider for given class.
     *
     * @param targetClass Class to add serializer provider for.
     * @param containerFactory Serializer provider to add.
     */
    public void registerSerializerProvider(Class<?> targetClass, ContainerSerializerFactory containerFactory) {
        serializerFactories.putIfAbsent(targetClass, containerFactory);
    }

    /**
     * Gets serializer provider for given class.
     *
     * @param targetClass Class to get serializer provider for.
     * @return Serializer provider.
     */
    public ContainerSerializerFactory getSerializerProvider(Class<?> targetClass) {
        return serializerFactories.get(targetClass);
    }

    /**
     * Searches for class model for given class. Returns the existing instance. Creates a new instance if
     * it doesn't exist.
     *
     * @param targetClass Class to search by or parse, not null.
     * @return {@link ClassDescriptor} for given class.
     */
    public ClassDescriptor getOrCreateClassModel(Class<?> targetClass) {
        ClassDescriptor descriptor = classModels.get(targetClass);
        if (null != descriptor) {
            return descriptor;
        }
        final Stack<Class> classStack = new Stack<>();
        Class currentClass = targetClass;
        while (Object.class != currentClass) {
            if (null == currentClass) {
                break;
            }
            classStack.push(currentClass);
            currentClass = currentClass.getSuperclass();
        }
        if (Object.class == targetClass) {
            classModels.computeIfAbsent(targetClass, (classArg) -> new ClassDescriptor(classArg, null, null, null));
            return classModels.get(targetClass);
        }
        ClassDescriptor parentClassDescriptor = null;
        while (!classStack.empty()) {
            Class parseTarget = classStack.pop();
            parentClassDescriptor = classModels.computeIfAbsent(parseTarget, new ClassModelParserFunction(parentClassDescriptor, classModelParser, jsonbConfig));
        }
        return classModels.get(targetClass);
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
                final ClassDescriptor result = classModels.get(next);
                next = next.getSuperclass();
                return result;
            }
        };
    }

    /**
     * Create mapping context which is scoped to jsonb runtime.
     *
     * @param jsonbConfig Context. Required.
     */
    public MappingRegistry(JsonbConfigurationContext jsonbConfig) {
        Objects.requireNonNull(jsonbConfig);
        this.jsonbConfig = jsonbConfig;
        this.classModelParser = new ClassParser(jsonbConfig);
    }

}

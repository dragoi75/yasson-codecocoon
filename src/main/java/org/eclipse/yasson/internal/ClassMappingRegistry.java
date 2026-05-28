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

import org.eclipse.yasson.internal.serializer.ContainerSerializerProvider;
import org.eclipse.yasson.internal.model.ClassModel;
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
public class ClassMappingRegistry {

    private static class ClassModelParserFunction implements Function<Class, ClassModel> {

        private ClassModel enclosingClassModel;

        private ClassParser typeParser;

        private JsonbRuntimeContext jsonbRuntime;

        @Override
        public ClassModel apply(Class inputClass) {
            final JsonbAnnotatedElement<Class<?>> annotatedElement = jsonbRuntime.getAnnotationIntrospector().collectAnnotations(inputClass);
            final ClassCustomization typeCustomization = jsonbRuntime.getAnnotationIntrospector().introspectCustomization(annotatedElement);
            final ClassModel constructedClassModel = new ClassModel(inputClass, typeCustomization, enclosingClassModel, jsonbRuntime.getConfigProperties().getPropertyNamingStrategy());
            typeParser.parseProperties(constructedClassModel, annotatedElement);
            return constructedClassModel;
        }

        public ClassModelParserFunction(ClassModel enclosingClassModel, ClassParser typeParser, JsonbRuntimeContext jsonbRuntime) {
            this.enclosingClassModel = enclosingClassModel;
            this.typeParser = typeParser;
            this.jsonbRuntime = jsonbRuntime;
        }

    }

    private final JsonbRuntimeContext jsonbRuntime;

    private final ConcurrentHashMap<Class<?>, ClassModel> classModelMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Class<?>, ContainerSerializerProvider> serializerProvidersMap = new ConcurrentHashMap<>();

    private final ClassParser typeParser;

    /**
     * Search for class model, without parsing if not found.
     *
     * @param targetClass Class to search by or parse, not null.
     * @return Model of a class if found.
     */
    public ClassModel getClassModel(Class<?> targetClass) {
        return classModelMap.get(targetClass);
    }

    /**
     * Provided class class model is returned first by iterator.
     * Following class models are sorted by hierarchy from provided class up to the Object.class.
     *
     * @param targetClass class to start iteration of class models from
     * @return iterator of class models
     */
    public Iterator<ClassModel> classModelIterator(final Class<?> targetClass) {
        return new Iterator<ClassModel>() {

            private Class<?> next = targetClass;

            @Override
            public boolean hasNext() {
                return Object.class != next;
            }

            @Override
            public ClassModel next() {
                final ClassModel result = classModelMap.get(next);
                next = next.getSuperclass();
                return result;
            }
        };
    }

    /**
     * Gets serializer provider for given class.
     *
     * @param targetClass Class to get serializer provider for.
     * @return Serializer provider.
     */
    public ContainerSerializerProvider getSerializerProvider(Class<?> targetClass) {
        return serializerProvidersMap.get(targetClass);
    }

    /**
     * Adds given serializer provider for given class.
     *
     * @param targetClass Class to add serializer provider for.
     * @param provider Serializer provider to add.
     */
    public void registerSerializerProvider(Class<?> targetClass, ContainerSerializerProvider provider) {
        serializerProvidersMap.putIfAbsent(targetClass, provider);
    }

    /**
     * Searches for class model for given class. Returns the existing instance. Creates a new instance if
     * it doesn't exist.
     *
     * @param targetClass Class to search by or parse, not null.
     * @return {@link ClassModel} for given class.
     */
    public ClassModel getOrCreateClassModel(Class<?> targetClass) {
        ClassModel foundModel = classModelMap.get(targetClass);
        if (null != foundModel) {
            return foundModel;
        }
        final Stack<Class> classStack = new Stack<>();
        Class candidateClass = targetClass;
        while (Object.class != candidateClass) {
            if (null == candidateClass) {
                break;
            }
            classStack.push(candidateClass);
            candidateClass = candidateClass.getSuperclass();
        }
        if (Object.class == targetClass) {
            classModelMap.computeIfAbsent(targetClass, (candidate) -> new ClassModel(candidate, null, null, null));
            return classModelMap.get(targetClass);
        }
        ClassModel enclosingClassModel = null;
        while (!classStack.empty()) {
            Class classToInspect = classStack.pop();
            enclosingClassModel = classModelMap.computeIfAbsent(classToInspect, new ClassModelParserFunction(enclosingClassModel, typeParser, jsonbRuntime));
        }
        return classModelMap.get(targetClass);
    }

    /**
     * Create mapping context which is scoped to jsonb runtime.
     *
     * @param jsonbRuntime Context. Required.
     */
    public ClassMappingRegistry(JsonbRuntimeContext jsonbRuntime) {
        Objects.requireNonNull(jsonbRuntime);
        this.jsonbRuntime = jsonbRuntime;
        this.typeParser = new ClassParser(jsonbRuntime);
    }

}

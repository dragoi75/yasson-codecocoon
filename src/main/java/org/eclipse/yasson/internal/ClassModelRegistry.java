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
import org.eclipse.yasson.internal.serializer.ContainerSerializerProvider;
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
public class ClassModelRegistry {

    private static class ClassModelParserFunction implements Function<Class, ClassDescriptor> {

        private ClassDescriptor parentDescriptor;

        private ClassParser typeParser;

        private JsonbRuntimeContext jsonbRuntime;

        public ClassModelParserFunction(ClassDescriptor parentDescriptor, ClassParser typeParser, JsonbRuntimeContext jsonbRuntime) {
            this.parentDescriptor = parentDescriptor;
            this.typeParser = typeParser;
            this.jsonbRuntime = jsonbRuntime;
        }

        @Override
        public ClassDescriptor apply(Class targetType) {
            final JsonbAnnotatedElement<Class<?>> typeAnnotatedElement = jsonbRuntime.getAnnotationIntrospector().collectAnnotations(targetType);
            final ClassCustomization classConfig = jsonbRuntime.getAnnotationIntrospector().introspectCustomization(typeAnnotatedElement);
            final ClassDescriptor createdDescriptor = new ClassDescriptor(targetType, classConfig, parentDescriptor, jsonbRuntime.getConfigProperties().getPropertyNamingStrategy());
            typeParser.parseProperties(createdDescriptor, typeAnnotatedElement);
            return createdDescriptor;
        }
    }

    private final JsonbRuntimeContext jsonbRuntime;

    private final ConcurrentHashMap<Class<?>, ClassDescriptor> classDescriptorMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Class<?>, ContainerSerializerProvider> serializerProviderMap = new ConcurrentHashMap<>();

    private final ClassParser typeParser;

    /**
     * Create mapping context which is scoped to jsonb runtime.
     *
     * @param jsonbRuntime Context. Required.
     */
    public ClassModelRegistry(JsonbRuntimeContext jsonbRuntime) {
        Objects.requireNonNull(jsonbRuntime);
        this.jsonbRuntime = jsonbRuntime;
        this.typeParser = new ClassParser(jsonbRuntime);
    }

    /**
     * Searches for class model for given class. Returns the existing instance. Creates a new instance if
     * it doesn't exist.
     *
     * @param typeKey Class to search by or parse, not null.
     * @return {@link ClassDescriptor} for given class.
     */
    public ClassDescriptor getOrCreateClassModel(Class<?> typeKey) {
        ClassDescriptor modelDescriptor = classDescriptorMap.get(typeKey);
        if (null != modelDescriptor) {
            return modelDescriptor;
        }
        final Stack<Class> classStack = new Stack<>();
        Class targetClass = typeKey;
        while (Object.class != targetClass) {
            if (null == targetClass) {
                break;
            }
            classStack.push(targetClass);
            targetClass = targetClass.getSuperclass();
        }
        if (Object.class == typeKey) {
            classDescriptorMap.computeIfAbsent(typeKey, (candidateClass) -> new ClassDescriptor(candidateClass, null, null, null));
            return classDescriptorMap.get(typeKey);
        }
        ClassDescriptor parentDescriptor = null;
        while (!classStack.empty()) {
            Class parseTarget = classStack.pop();
            parentDescriptor = classDescriptorMap.computeIfAbsent(parseTarget, new ClassModelParserFunction(parentDescriptor, typeParser, jsonbRuntime));
        }
        return classDescriptorMap.get(typeKey);
    }

    /**
     * Provided class class model is returned first by iterator.
     * Following class models are sorted by hierarchy from provided class up to the Object.class.
     *
     * @param typeKey class to start iteration of class models from
     * @return iterator of class models
     */
    public Iterator<ClassDescriptor> classModelIterator(final Class<?> typeKey) {
        return new Iterator<ClassDescriptor>() {

            private Class<?> next = typeKey;

            @Override
            public boolean hasNext() {
                return Object.class != next;
            }

            @Override
            public ClassDescriptor next() {
                final ClassDescriptor result = classDescriptorMap.get(next);
                next = next.getSuperclass();
                return result;
            }
        };
    }

    /**
     * Search for class model, without parsing if not found.
     *
     * @param typeKey Class to search by or parse, not null.
     * @return Model of a class if found.
     */
    public ClassDescriptor getClassModel(Class<?> typeKey) {
        return classDescriptorMap.get(typeKey);
    }

    /**
     * Gets serializer provider for given class.
     *
     * @param typeKey Class to get serializer provider for.
     * @return Serializer provider.
     */
    public ContainerSerializerProvider getSerializerProvider(Class<?> typeKey) {
        return serializerProviderMap.get(typeKey);
    }

    /**
     * Adds given serializer provider for given class.
     *
     * @param typeKey Class to add serializer provider for.
     * @param providerInstance Serializer provider to add.
     */
    public void registerSerializerProvider(Class<?> typeKey, ContainerSerializerProvider providerInstance) {
        serializerProviderMap.putIfAbsent(typeKey, providerInstance);
    }
}

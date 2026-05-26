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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

/**
 * Annotation holder for classes, superclasses, interfaces, fields, getters and setters.
 *
 * @param <T> annotated element
 */
public class JsonbAnnotationContainer<T extends AnnotatedElement> {

    private final Map<Class<? extends Annotation>, LinkedList<AnnotationMetadata<?>>> annotationMap = new HashMap<>(4);

    private final T value;

    public static final class AnnotationMetadata<T extends Annotation> {

        private final T marker;
        private final boolean fromSuperclass;
        private final Class<?> concreteType;

        public Class<?> getDefinedType() {
            return concreteType;
        }

        @Override
        public String toString() {
            return concreteType.getName();
        }

        public T getAnnotation() {
            return marker;
        }

        public boolean isInherited() {
            return fromSuperclass;
        }

        public AnnotationMetadata(T marker, boolean fromSuperclass, Class<?> concreteType) {
            this.marker = marker;
            this.fromSuperclass = fromSuperclass;
            this.concreteType = concreteType;
        }

    }

    public void addAnnotationWrapper(AnnotationMetadata<?> metadata) {
        annotationMap.computeIfAbsent(metadata.getAnnotation().annotationType(), aClass -> new LinkedList<>())
                .add(metadata);
    }

    @SuppressWarnings("unchecked")
    public <AT extends Annotation> JsonbAnnotationContainer.AnnotationMetadata<AT> getAnnotationWrapper(Class<AT> annotationType) {
        return (AnnotationMetadata<AT>) annotationMap.get(annotationType).getFirst();
    }

    /**
     * Adds annotation.
     *
     * @param marker Annotation to add.
     * @param concreteType
     */
    public void addAnnotation(Annotation marker, boolean fromSuperclass, Class<?> concreteType) {
//        if (annotations.containsKey(annotation.annotationType())) {
//            throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR,
//                                                         "Annotation already present: " + annotation));
//        }
//        annotations.put(annotation.annotationType(), new AnnotationWrapper(annotation, inherited));
        annotationMap.computeIfAbsent(marker.annotationType(), aClass -> new LinkedList<>())
                        .add(new AnnotationMetadata(marker, fromSuperclass, concreteType));
    }

    public Annotation[] getAnnotations() {
        return annotationMap.values().stream()
                .flatMap(Collection::stream)
                .map(AnnotationMetadata::getAnnotation)
                .toArray(Annotation[]::new);
    }

    /**
     * Gets element.
     *
     * @return Element.
     */
    public T getElement() {
        return value;
    }

    /**
     * Get an annotation by type.
     *
     * @param <AT>            Type of annotation
     * @param annotationType Type of annotation
     * @return Annotation by passed type
     */
    public <AT extends Annotation> Optional<AT> getAnnotation(Class<AT> annotationType) {
        return Optional.ofNullable(annotationMap.get(annotationType))
                .map(LinkedList::getFirst)
                .map(AnnotationMetadata::getAnnotation)
                .map(annotationType::cast);
    }

    public <AT extends Annotation> LinkedList<AnnotationMetadata<?>> getAnnotations(Class<AT> annotationType) {
        return annotationMap.getOrDefault(annotationType, new LinkedList<>());
    }

    /**
     * Creates a new instance.
     *
     * @param value Element.
     */
    public JsonbAnnotationContainer(T value) {
        for (Annotation marker : value.getAnnotations()) {
            if (value instanceof Class) {
                addAnnotation(marker, false, (Class<?>) value);
            } else {
                addAnnotation(marker, false, null);
            }
        }

        this.value = value;
    }

}

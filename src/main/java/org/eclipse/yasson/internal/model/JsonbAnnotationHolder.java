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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.HashMap;
import java.util.Map;

import jakarta.json.bind.JsonbException;

import org.eclipse.yasson.internal.properties.ErrorMessageKeys;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Annotation holder for classes, superclasses, interfaces, fields, getters and setters.
 *
 * @param <T> annotated element
 */
public class JsonbAnnotationHolder<T extends AnnotatedElement> {

    private final Map<Class<? extends Annotation>, Annotation> annotationMap = new HashMap<>(4);

    private final T item;

    /**
     * Get an annotation by type.
     * @param <AT> Type of annotation
     * @param annotationType Type of annotation
     * @return Annotation by passed type
     */
    public <AT extends Annotation> AT getAnnotation(Class<AT> annotationType) {
        return annotationType.cast(annotationMap.get(annotationType));
    }

    /**
     * Adds annotation.
     *
     * @param metaTag Annotation to add.
     */
    public void addAnnotation(Annotation metaTag) {
        if (annotationMap.containsKey(metaTag.annotationType())) {
            throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.INTERNAL_ERROR,
                                                         "Annotation already present: " + metaTag));
        }
        annotationMap.put(metaTag.annotationType(), metaTag);
    }

    public Annotation[] getAnnotations() {
        return annotationMap.values().toArray(new Annotation[0]);
    }

    /**
     * Creates a new instance.
     *
     * @param item Element.
     */
    public JsonbAnnotationHolder(T item) {
        for (Annotation meta : item.getAnnotations()) {
            annotationMap.put(meta.annotationType(), meta);
        }

        this.item = item;
    }

    /**
     * Gets element.
     *
     * @return Element.
     */
    public T getElement() {
        return item;
    }

}

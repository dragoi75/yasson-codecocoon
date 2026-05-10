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

import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Annotation holder for classes, superclasses, interfaces, fields, getters and setters.
 *
 * @param <T> annotated element
 */
public class JsonbAnnotatedMember<T extends AnnotatedElement> {

    private final Map<Class<? extends Annotation>, Annotation> annotationMap = new HashMap<>(4);

    private final T memberValue;

    /**
     * Creates a new instance.
     *
     * @param memberValue Element.
     */
    public JsonbAnnotatedMember(T memberValue) {
        for (Annotation annotation : memberValue.getAnnotations()) {
            annotationMap.put(annotation.annotationType(), annotation);
        }

        this.memberValue = memberValue;
    }

    /**
     * Gets element.
     *
     * @return Element.
     */
    public T getElement() {
        return memberValue;
    }

    /**
     * Get an annotation by type.
     * @param <AT> Type of annotation
     * @param annotationType Type of annotation
     * @return Annotation by passed type
     */
    public <AT extends Annotation> AT getAnnotation(Class<AT> annotationType) {
        return annotationType.cast(annotationMap.get(annotationType));
    }

    public Annotation[] getAnnotations() {
        return annotationMap.values().toArray(new Annotation[0]);
    }

    /**
     * Adds annotation.
     *
     * @param ann Annotation to add.
     */
    public void addAnnotation(Annotation ann) {
        if (annotationMap.containsKey(ann.annotationType())) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.INTERNAL_ERROR,
                                                         "Annotation already present: " + ann));
        }
        annotationMap.put(ann.annotationType(), ann);
    }
}

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
package org.eclipse.yasson.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Finds an annotation including inherited annotations (e.g. meta-annotations).
 */
class AnnotationLocator {

    private static final String CONSTRUCTOR_PROPERTIES_KEY = "java.beans.ConstructorProperties";

    private static final Logger LOG = Logger.getLogger(AnnotationLocator.class.getName());

    private final String annotationTypeName;

    // may be null
    private final Class<? extends Annotation> annotationType;

    @SuppressWarnings("unchecked")
    public <T extends Annotation> T findAnnotationIn(Annotation[] annArray) {
        if (null == annotationType) {
            return null;
        }
        return (T) locateAnnotation(annArray, annotationType, new HashSet<>());
    }

    @Override
    public String toString() {
        return "AnnotationFinder [annotationClassName=" + annotationTypeName + ", annotationClass=" + annotationType + "]";
    }

    /**
     * Searches for annotation, collects processed, to avoid StackOverflow.
     */
    // "static" to use it in a hybrid procedural and object oriented manner.
    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T locateAnnotation(Annotation[] baseAnnotations, Class<T> annotationDescriptor, Set<Annotation> seenAnnotations) {
        for (Annotation contender : baseAnnotations) {
            final Class<? extends Annotation> annotationType = contender.annotationType();
            if (annotationType.equals(annotationDescriptor)) {
                return (T) contender;
            }
            seenAnnotations.add(contender);
            final List<Annotation> inheritedAnnotationList = new ArrayList<>(Arrays.asList(annotationType.getDeclaredAnnotations()));
            inheritedAnnotationList.removeAll(seenAnnotations);
            if (0 < inheritedAnnotationList.size()) {
                final T parent = locateAnnotation(inheritedAnnotationList.toArray(new Annotation[inheritedAnnotationList.size()]), annotationDescriptor, seenAnnotations);
                if (null != parent) {
                    return parent;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Annotation> Class<T> getOptionalAnnotationClass(String classFqn) {
        try {
            return (Class<T>) Class.forName(classFqn);
        } catch (ClassNotFoundException e) {
            String msg = MessageBundle.getMessage(MessageKeyConstants.ANNOTATION_NOT_AVAILABLE, classFqn);
            LOG.finest(msg);
            return null;
        }
    }

    /**
     * Looks for the annotation {@link #findAnnotationIn(Annotation[])} <br>
     * and executes the "value" Method of it dynamically.
     *
     * @param annArray - Array of {@link Annotation}n.
     * @return {@link Object}
     */
    public Object getValueIn(Annotation[] annArray) {
        return invokeValueMethod(findAnnotationIn(annArray));
    }

    /**
     * Gets the {@link AnnotationLocator} for @ConstructorProperties-Annotation.
     *
     * @return {@link AnnotationLocator}
     */
    public static AnnotationLocator findConstructorPropertiesAnnotation() {
        return locateAnnotationByName(CONSTRUCTOR_PROPERTIES_KEY);
    }

    private Object invokeValueMethod(Annotation annClass) {
        if (null == annClass) {
            return null;
        }
        try {
            return annClass.annotationType().getMethod("value").invoke(annClass);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            String msg = MessageBundle.getMessage(MessageKeyConstants.MISSING_VALUE_PROPERTY_IN_ANNOTATION, annClass.annotationType().getName());
            LOG.finest(msg);
            return null;
        }
    }

    /**
     * Gets the {@link AnnotationLocator} for the given Annotation-Type.
     *
     * @param annClass {@link Class}, that is a sub-type of {@link Annotation}
     * @return {@link AnnotationLocator}
     */
    public static AnnotationLocator locateAnnotation(Class<?> annClass) {
        return locateAnnotationByName(annClass.getName());
    }

    /**
     * Gets the {@link AnnotationLocator} for the given Annotation-Type Name.
     *
     * @param annotationTypeName {@link String}, that is a sub-type of {@link Annotation}
     * @return {@link AnnotationLocator}
     */
    public static AnnotationLocator locateAnnotationByName(String annotationTypeName) {
        return new AnnotationLocator(annotationTypeName, getOptionalAnnotationClass(annotationTypeName));
    }

    private AnnotationLocator(String annotationTypeName, Class<? extends Annotation> annotationType) {
        this.annotationTypeName = annotationTypeName;
        this.annotationType = annotationType;
    }

}

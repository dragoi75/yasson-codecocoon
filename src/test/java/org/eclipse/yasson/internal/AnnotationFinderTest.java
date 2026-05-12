/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import static org.eclipse.yasson.internal.AnnotationFinderTestFixtures.TESTVALUE;
import static org.eclipse.yasson.internal.AnnotationFinderTestFixtures.getConstructorAnnotationsOf;
import static org.eclipse.yasson.internal.AnnotationFinderTestFixtures.getMethodAnnotationsOf;

import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.AnnotationAnnotatedWithDeprecated;
import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.AnnotationWithoutValueProperty;
import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.ObjectWithConstructAnnotation;
import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.ObjectWithDeprecatedAndIgnoredMethod;
import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.ObjectWithDeprecatedMethod;
import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.ObjectWithIgnoredAndInheritedDeprecatedMethod;
import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.ObjectWithIgnoredMethod;
import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.ObjectWithInheritedAndDirectlyDeprecatedMethod;
import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.ObjectWithInheritedDeprecatedMethod;
import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.ObjectWithMissingValuePropertyAnnotation;
import org.eclipse.yasson.internal.AnnotationFinderTestFixtures.ObjectWithNoAnnotations;

public class AnnotationFinderTest {

    /**
     * class under test.
     */
    private AnnotationLocator findAnnotation = AnnotationLocator.locateAnnotation(Deprecated.class);

    @Test
    public void testAnnotationShouldBeFound() {
        assertNotNull(findAnnotation.findAnnotationIn(getMethodAnnotationsOf(ObjectWithDeprecatedMethod.class)));
    }

    @Test
    public void testAnnotationShouldBeFoundWithinOthers() {
        assertNotNull(findAnnotation.findAnnotationIn(getMethodAnnotationsOf(ObjectWithDeprecatedAndIgnoredMethod.class)));
    }

    @Test
    public void testInheritedAnnotationShouldBeFound() {
        Deprecated annotation = findAnnotation.findAnnotationIn(getMethodAnnotationsOf(ObjectWithInheritedDeprecatedMethod.class));
        assertEquals("inherited", annotation.since());
    }

    @Test
    public void testInheritedAnnotationShouldBeFoundWithinOthers() {
        Deprecated annotation = findAnnotation.findAnnotationIn(getMethodAnnotationsOf(ObjectWithIgnoredAndInheritedDeprecatedMethod.class));
        assertEquals("inherited", annotation.since());
    }

    @Test
    public void testDirectAnnotationShouldBePreferedOverInheritedOnes() {
        Deprecated annotation = findAnnotation.findAnnotationIn(getMethodAnnotationsOf(ObjectWithInheritedAndDirectlyDeprecatedMethod.class));
        assertEquals("", annotation.since());
    }

    @Test
    public void testResultShouldBeNullWhenThereIsNoAnnotation() {
        assertNull(findAnnotation.findAnnotationIn(getMethodAnnotationsOf(ObjectWithNoAnnotations.class)));
    }

    @Test
    public void testResultShouldBeNullWhenThereAreOnlyOtherAnnotations() {
        assertNull(findAnnotation.findAnnotationIn(getMethodAnnotationsOf(ObjectWithIgnoredMethod.class)));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testValueOfAnnotationShouldBeExtractedDynamically() {
        findAnnotation = AnnotationLocator.locateAnnotation(AnnotationAnnotatedWithDeprecated.class);
        assertEquals(TESTVALUE, findAnnotation.getValueIn(getMethodAnnotationsOf(ObjectWithInheritedDeprecatedMethod.class)));
    }

    @Test
    public void testValueOfAnnotationShouldBeNullIfAnnotationDoesNotExist() {
        findAnnotation = AnnotationLocator.locateAnnotation(ObjectWithNoAnnotations.class);
        assertNull(findAnnotation.getValueIn(getMethodAnnotationsOf(ObjectWithInheritedDeprecatedMethod.class)));
    }

    @Test
    public void testValueOfConstructorAnnotation() {
        findAnnotation = AnnotationLocator.findConstructorPropertiesAnnotation();
        assertArrayEquals(new Object[] { TESTVALUE }, (Object[]) findAnnotation.getValueIn(getConstructorAnnotationsOf(ObjectWithConstructAnnotation.class)));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testValueOfConstructorMetaAnnotation() {
        findAnnotation = AnnotationLocator.locateAnnotation(AnnotationAnnotatedWithDeprecated.class);
        assertEquals(TESTVALUE, findAnnotation.getValueIn(getConstructorAnnotationsOf(ObjectWithConstructAnnotation.class)));
    }

    @Test
    public void testIgnorenNonExistingOptionalAnnotationClassName() {
        findAnnotation = AnnotationLocator.locateAnnotationByName("java.nnnooootttt.eexxxxiissttting.Class");
        assertNull(findAnnotation.findAnnotationIn(getMethodAnnotationsOf(ObjectWithIgnoredMethod.class)));
    }

    @Test
    public void testIgnorenAnnotationAnnotationWithoutValueProperty() {
        findAnnotation = AnnotationLocator.locateAnnotation(AnnotationWithoutValueProperty.class);
        assertNull(findAnnotation.getValueIn(getMethodAnnotationsOf(ObjectWithMissingValuePropertyAnnotation.class)));
    }
}

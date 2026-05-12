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

import org.eclipse.yasson.internal.model.JsonbInstantiator;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import static org.eclipse.yasson.internal.AnnotationIntrospectorTestAsserts.assertCreatedInstanceContainsAllParameters;
import static org.eclipse.yasson.internal.AnnotationIntrospectorTestAsserts.assertParameters;

import org.eclipse.yasson.internal.AnnotationIntrospectorTestFixtures.ObjectWithJsonbCreatorAndConstructorPropertiesAnnotation;
import org.eclipse.yasson.internal.AnnotationIntrospectorTestFixtures.ObjectWithJsonbCreatorAnnotatedConstructor;
import org.eclipse.yasson.internal.AnnotationIntrospectorTestFixtures.ObjectWithJsonbCreatorAnnotatedFactoryMethod;
import org.eclipse.yasson.internal.AnnotationIntrospectorTestFixtures.ObjectWithJsonbCreatorAnnotatedProtectedConstructor;
import org.eclipse.yasson.internal.AnnotationIntrospectorTestFixtures.ObjectWithNoArgAndJsonbCreatorAnnotatedProtectedConstructor;
import org.eclipse.yasson.internal.AnnotationIntrospectorTestFixtures.ObjectWithTwoJsonbCreatorAnnotatedSpots;
import org.eclipse.yasson.internal.AnnotationIntrospectorTestFixtures.ObjectWithoutAnnotatedConstructor;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;

import jakarta.json.spi.JsonProvider;

/**
 * Tests the {@link JsonbAnnotationIntrospector}.
 * 
 * @see AnnotationIntrospectorTestFixtures
 * @see AnnotationIntrospectorTestAsserts
 */
public class AnnotationIntrospectorTest {
    private final JsonbRuntimeContext jsonbContext = new JsonbRuntimeContext(new JsonbConfig(), JsonProvider.provider());

    /**
     * class under test.
     */
    private JsonbAnnotationIntrospector instrospector = new JsonbAnnotationIntrospector(jsonbContext);

    @Test
    public void testObjectShouldBeCreateableFromJsonbAnnotatedConstructor() {
        JsonbInstantiator creator = instrospector.getCreator(ObjectWithJsonbCreatorAnnotatedConstructor.class);
        assertParameters(ObjectWithJsonbCreatorAnnotatedConstructor.parameters(), creator);
        assertCreatedInstanceContainsAllParameters(ObjectWithJsonbCreatorAnnotatedConstructor.example(), creator);
    }

    @Test
    public void testObjectShouldBeCreateableFromJsonbAnnotatedStaticFactoryMethod() {
        JsonbInstantiator creator = instrospector.getCreator(ObjectWithJsonbCreatorAnnotatedFactoryMethod.class);
        assertParameters(ObjectWithJsonbCreatorAnnotatedFactoryMethod.parameters(), creator);
        assertCreatedInstanceContainsAllParameters(ObjectWithJsonbCreatorAnnotatedFactoryMethod.example(), creator);
    }

    @Test
    public void testObjectShouldBeCreateableFromJsonbAnnotatedStaticFactoryMethodIgnoringConstructorPorperties() {
        JsonbInstantiator creator = instrospector.getCreator(ObjectWithJsonbCreatorAndConstructorPropertiesAnnotation.class);
        assertParameters(ObjectWithJsonbCreatorAndConstructorPropertiesAnnotation.parameters(), creator);
        assertCreatedInstanceContainsAllParameters(ObjectWithJsonbCreatorAndConstructorPropertiesAnnotation.example(), creator);
    }

    @Test
    public void testJsonbAnnotatedProtectedConstructorLeadsToAnException() {
        assertThrows(JsonbException.class, () -> {
	        JsonbInstantiator creator = instrospector.getCreator(ObjectWithJsonbCreatorAnnotatedProtectedConstructor.class);
	        assertCreatedInstanceContainsAllParameters(ObjectWithJsonbCreatorAnnotatedProtectedConstructor.example(), creator);
        });
    }

    // TODO Under discussion: https://github.com/eclipse-ee4j/yasson/issues/326
    @Disabled
    @Test
    public void testNoArgConstructorShouldBePreferredOverUnusableJsonbAnnotatedProtectedConstructor() {
        JsonbInstantiator creator = instrospector.getCreator(ObjectWithNoArgAndJsonbCreatorAnnotatedProtectedConstructor.class);
        assertParameters(ObjectWithNoArgAndJsonbCreatorAnnotatedProtectedConstructor.parameters(), creator);
        assertCreatedInstanceContainsAllParameters(ObjectWithNoArgAndJsonbCreatorAnnotatedProtectedConstructor.example(), creator);
    }

    @Test
    public void testMoreThanOneAnnotatedCreatorMethodShouldLeadToAnException() {
        assertThrows(JsonbException.class, 
        			() -> instrospector.getCreator(ObjectWithTwoJsonbCreatorAnnotatedSpots.class),
        			() -> "More than one @" + JsonbInstantiator.class.getSimpleName());
    }

    @Test
    public void testCreatorShouldBeNullOnMissingConstructorAnnotation() {
        assertNull(instrospector.getCreator(ObjectWithoutAnnotatedConstructor.class));
    }

}

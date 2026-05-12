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

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * @author Roman Grigoriadi
 */
public class ReflectionUtilsTest {

    public static class Types<T> {

        public List<String> resolvedParameterizedField;

        public List<Map<Integer, String>> resolvedNestedParameterizedField;

        public String resolvedStr;

        public List<T> unresolvedParameterizedField;

        public List<Map<Integer, T>> unresolvedNestedParameterizedField;

        public T unresolvedField;

        public List<?> unresolvedWildcardField;
    }


    @Test
    public void testIsTypeResolved() {
        Types<String> types = new Types<>();
        assertTrue(ReflectionTypeResolver.isResolvedType(getFieldType("resolvedParameterizedField")));
        assertTrue(ReflectionTypeResolver.isResolvedType(getFieldType("resolvedNestedParameterizedField")));
        assertTrue(ReflectionTypeResolver.isResolvedType(getFieldType("resolvedStr")));
        assertFalse(ReflectionTypeResolver.isResolvedType(getFieldType("unresolvedParameterizedField")));
        assertFalse(ReflectionTypeResolver.isResolvedType(getFieldType("unresolvedNestedParameterizedField")));
        assertFalse(ReflectionTypeResolver.isResolvedType(getFieldType("unresolvedField")));
        assertFalse(ReflectionTypeResolver.isResolvedType(getFieldType("unresolvedWildcardField")));
    }

    private static Type getFieldType(String fieldName) {
        try {
            Field field = Types.class.getField(fieldName);
            return field.getGenericType();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}

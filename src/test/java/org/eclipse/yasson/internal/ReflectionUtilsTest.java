package org.eclipse.yasson.internal;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    private Type getFieldType(String fieldName) {
        try {
            Field field = Types.class.getField(fieldName);
            return field.getGenericType();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}

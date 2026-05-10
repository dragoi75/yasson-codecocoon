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
        assertTrue(ReflectiveTypeResolver.isResolvedType(getFieldType("resolvedParameterizedField")));
        assertTrue(ReflectiveTypeResolver.isResolvedType(getFieldType("resolvedNestedParameterizedField")));
        assertTrue(ReflectiveTypeResolver.isResolvedType(getFieldType("resolvedStr")));
        assertFalse(ReflectiveTypeResolver.isResolvedType(getFieldType("unresolvedParameterizedField")));
        assertFalse(ReflectiveTypeResolver.isResolvedType(getFieldType("unresolvedNestedParameterizedField")));
        assertFalse(ReflectiveTypeResolver.isResolvedType(getFieldType("unresolvedField")));
        assertFalse(ReflectiveTypeResolver.isResolvedType(getFieldType("unresolvedWildcardField")));
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

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
        assertTrue(ReflectiveTypeUtils.isResolvedType(getFieldType("resolvedParameterizedField")));
        assertTrue(ReflectiveTypeUtils.isResolvedType(getFieldType("resolvedNestedParameterizedField")));
        assertTrue(ReflectiveTypeUtils.isResolvedType(getFieldType("resolvedStr")));
        assertFalse(ReflectiveTypeUtils.isResolvedType(getFieldType("unresolvedParameterizedField")));
        assertFalse(ReflectiveTypeUtils.isResolvedType(getFieldType("unresolvedNestedParameterizedField")));
        assertFalse(ReflectiveTypeUtils.isResolvedType(getFieldType("unresolvedField")));
        assertFalse(ReflectiveTypeUtils.isResolvedType(getFieldType("unresolvedWildcardField")));
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

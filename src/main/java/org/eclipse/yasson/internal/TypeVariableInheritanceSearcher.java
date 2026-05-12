/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.Deque;
import jakarta.json.bind.JsonbException;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Search for type variable in inheritance hierarchy and resolve if possible.
 */
public class TypeVariableInheritanceSearcher {

    private final Deque<ParameterizedType> genericSubtypesDeque = new ArrayDeque<>();

    /**
     * Searches the hierarchy of classes to resolve a type variable. If typevar resolved value is another typevar redirection
     * (propagated from wrapping class),
     * this typevar is returned.
     *
     * <pre>
     *
     * Example 1: typevar is resolved
     *
     * class GenericClass &lt;T&gt; {
     *     private T genericField;
     * }
     * class ConcreteClass extends GenericClass&lt;MyPojo&gt; {
     *     //...
     * }
     *
     * In above case when ConcreteClass type is passed as runtime type and &lt;T&gt; as type variable, T is resolved to MyPojo.
     * </pre>
     *
     * <pre>
     * Example 2: typevar is resolved to another propagated typevar
     *
     * class WrapperGenericClass&lt;X&gt; {
     *     private GenericClass&lt;X&gt; propagatedGenericField
     * }
     *
     * class AnotherClass extends WrapperGenericClass&lt;MyPojo&gt; {
     * }
     *
     * In second case when GenericClass {@link ParameterizedType} is passed as runtime type and &lt;T&gt; as type variable,
     * T is resolved to propagated &lt;X&gt; by WrapperGenericClass.
     *
     * Resolution on &lt;X&gt; must be performed thereafter with AnotherClass runtime type.
     * </pre>
     *
     * @param targetType runtime type to search for typevar in, not null
     * @param typeVariable      type variable to resolve, not null
     * @return resolved runtime type, or type variable
     */
    public Type findParametrizedType(Type targetType, TypeVariable<?> typeVariable) {
        ParameterizedType paramTypeInstance = locateParameterizedSuperclass(targetType);
        if (null == paramTypeInstance) {
            return null;
        }
        Type resolvedType = findRuntimeTypeArgument(paramTypeInstance, typeVariable);
        if (null != resolvedType) {
            return resolvedType;
        }
        genericSubtypesDeque.push(paramTypeInstance);
        return findParametrizedType(((Class) paramTypeInstance.getRawType()).getGenericSuperclass(), typeVariable);
    }

    private Type verifySubclassRuntimeInfo(TypeVariable typeVariable) {
        if (0 == genericSubtypesDeque.size()) {
            return typeVariable;
        }
        ParameterizedType subclassGenericType = genericSubtypesDeque.pop();
        return findRuntimeTypeArgument(subclassGenericType, typeVariable);
    }

    private Type findRuntimeTypeArgument(ParameterizedType actualRuntimeType, TypeVariable<?> typeVariable) {
        if (typeVariable.getGenericDeclaration() != ReflectionTypeResolver.getRawType(actualRuntimeType)) {
            return null;
        }
        TypeVariable[] typeBounds = typeVariable.getGenericDeclaration().getTypeParameters();
        int idx = 0;
        while (typeBounds.length > idx) {
            if (typeBounds[idx].equals(typeVariable)) {
                Type resolvedType = actualRuntimeType.getActualTypeArguments()[idx];
                //Propagated generic types to another generic classes
                if (resolvedType instanceof TypeVariable<?>) {
                    return verifySubclassRuntimeInfo((TypeVariable) resolvedType);
                }
                //found runtime matchedGenericType
                return resolvedType;
            }
            idx += 1;
        }
        return null;
    }

    private static ParameterizedType locateParameterizedSuperclass(Type subjectType) {
        if (null == subjectType || subjectType instanceof ParameterizedType) {
            return (ParameterizedType) subjectType;
        }
        if (!(subjectType instanceof Class)) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.RESOLVE_PARAMETRIZED_TYPE, subjectType));
        }
        return locateParameterizedSuperclass(((Class) subjectType).getGenericSuperclass());
    }
}

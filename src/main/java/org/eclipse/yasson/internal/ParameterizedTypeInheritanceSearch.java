/*
 * Copyright (c) 2015, 2022 Oracle and/or its affiliates. All rights reserved.
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
import org.eclipse.yasson.internal.properties.MessageKeysEnum;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Search for type variable in inheritance hierarchy and resolve if possible.
 */
class ParameterizedTypeInheritanceSearch {

    private final Deque<ParameterizedType> genericSubtypeDeque = new ArrayDeque<>();

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
     * @param genericTypeParam      type variable to resolve, not null
     * @return resolved runtime type, or type variable
     */
    Type findParametrizedType(Type targetType, TypeVariable<?> genericTypeParam) {
        ParameterizedType paramTypeInstance = locateParameterizedSuperclass(targetType);
        if (null == paramTypeInstance) {
            return null;
        }
        Type matchedType = findRuntimeTypeArgument(paramTypeInstance, genericTypeParam);
        if (null != matchedType) {
            return matchedType;
        }
        genericSubtypeDeque.push(paramTypeInstance);
        return findParametrizedType(((Class) paramTypeInstance.getRawType()).getGenericSuperclass(), genericTypeParam);
    }

    private Type verifySubclassRuntimeInfo(TypeVariable genericTypeParam) {
        if (0 == genericSubtypeDeque.size()) {
            return genericTypeParam;
        }
        ParameterizedType subclassParamType = genericSubtypeDeque.pop();
        return findRuntimeTypeArgument(subclassParamType, genericTypeParam);
    }

    private Type findRuntimeTypeArgument(ParameterizedType runtimeParamType, TypeVariable<?> genericTypeParam) {
        if (genericTypeParam.getGenericDeclaration() != ReflectiveTypeResolver.getRawType(runtimeParamType)) {
            return null;
        }
        TypeVariable[] typeBounds = genericTypeParam.getGenericDeclaration().getTypeParameters();
        int index = 0;
        while (typeBounds.length > index) {
            if (typeBounds[index].equals(genericTypeParam)) {
                Type matchedType = runtimeParamType.getActualTypeArguments()[index];
                //Propagated generic types to another generic classes
                if (matchedType instanceof TypeVariable<?>) {
                    return verifySubclassRuntimeInfo((TypeVariable) matchedType);
                }
                //found runtime matchedGenericType
                return matchedType;
            }
            index += 1;
        }
        return null;
    }

    private static ParameterizedType locateParameterizedSuperclass(Type candidateType) {
        if (null == candidateType || candidateType instanceof ParameterizedType) {
            return (ParameterizedType) candidateType;
        }
        if (!(candidateType instanceof Class)) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeysEnum.RESOLVE_PARAMETRIZED_TYPE, candidateType));
        }
        return locateParameterizedSuperclass(((Class) candidateType).getGenericSuperclass());
    }
}

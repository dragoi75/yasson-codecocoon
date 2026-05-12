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
package org.eclipse.yasson.internal.model;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import jakarta.json.bind.JsonbException;

/**
 * Property of a class, field, getter and setter methods (javabean alike).
 * Used during class model initialization, than dereferenced.
 */
public class PropertyDescriptor {

    private final String identifier;

    private final JsonbAnnotationHolder<Class<?>> ownerClassHolder;

    private JsonbAnnotationHolder<Field> attributeHolder;

    private JsonbAnnotationHolder<Method> accessorHolder;

    private JsonbAnnotationHolder<Method> mutatorHolder;

    /**
     * Create instance of property.
     *
     * @param identifier                not null
     * @param sourceClassHolder Class model for a class declaring property.
     */
    public PropertyDescriptor(String identifier, JsonbAnnotationHolder<Class<?>> sourceClassHolder) {
        this.identifier = identifier;
        this.ownerClassHolder = sourceClassHolder;
    }

    /**
     * Name of a property, java bean convention.
     *
     * @return name
     */
    public String getName() {
        return identifier;
    }

    /**
     * {@link Field} representing property if any.
     *
     * @return field if present
     */
    public Field getField() {
        if (null == attributeHolder) {
            return null;
        }
        return attributeHolder.getElement();
    }

    /**
     * @param memberRef field not null
     */
    public void setField(Field memberRef) {
        this.attributeHolder = new JsonbAnnotationHolder<>(memberRef);
    }

    /**
     * {@link Method} representing getter of a property if any.
     *
     * @return getter if present
     */
    public Method getGetter() {
        if (null == accessorHolder) {
            return null;
        }
        return accessorHolder.getElement();
    }

    /**
     * @param readMethod not null
     */
    public void setGetter(Method readMethod) {
        this.accessorHolder = new JsonbAnnotationHolder<>(readMethod);
    }

    /**
     * {@link Method} representing setter of a property if any.
     *
     * @return setter if present
     */
    public Method getSetter() {
        if (null == mutatorHolder) {
            return null;
        }
        return mutatorHolder.getElement();
    }

    /**
     * @param writeMethod setter not null
     */
    public void setSetter(Method writeMethod) {
        this.mutatorHolder = new JsonbAnnotationHolder<>(writeMethod);
    }

    /**
     * Class element with annotation under construction for declaring class of this property.
     * This ClassModel is not fully initialized yet.
     *
     * @return ClassModel
     */
    public JsonbAnnotationHolder<Class<?>> getDeclaringClassElement() {
        return ownerClassHolder;
    }

    /**
     * Extracts type from first not null element:
     * Field, Getter, Setter.
     *
     * @return type of a property
     */
    public Type getPropertyType() {
        if (null == getField()) {
            if (null == getGetter()) {
                if (null != getSetter()) {
                    return getSetterType();
                }
            } else {
                return getGetterType();
            }
        } else {
            return getField().getGenericType();
        }
        throw new JsonbException("Empty property: " + identifier);
    }

    Type getGetterType() {
        if (null != getGetter()) {
            return getGetter().getGenericReturnType();
        }
        return null;
    }

    Type getSetterType() {
        Type[] typeArgs = getSetter().getGenericParameterTypes();
        if (1 != typeArgs.length) {
            throw new JsonbException("Invalid count of arguments for setter: " + getSetter());
        }
        return typeArgs[0];
    }

    /**
     * Element with field and its annotations.
     *
     * @return field with annotations
     */
    public JsonbAnnotationHolder<Field> getFieldElement() {
        return attributeHolder;
    }

    /**
     * Element with getter and its annotations.
     *
     * @return getter with annotations
     */
    public JsonbAnnotationHolder<Method> getGetterElement() {
        return accessorHolder;
    }

    /**
     * Element with setter and its annotations.
     *
     * @return setter with annotations
     */
    public JsonbAnnotationHolder<Method> getSetterElement() {
        return mutatorHolder;
    }
}

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
public class Property {

    private final String name;

    private final JsonbAnnotatedElement<Class<?>> declaringClassElement;

    private JsonbAnnotatedElement<Field> fieldElement;

    private JsonbAnnotatedElement<Method> getterElement;

    private JsonbAnnotatedElement<Method> setterElement;

    /**
     * Element with field and its annotations.
     *
     * @return field with annotations
     */
    public JsonbAnnotatedElement<Field> getFieldElement() {
        return fieldElement;
    }

    /**
     * Extracts type from first not null element:
     * Field, Getter, Setter.
     *
     * @return type of a property
     */
    public Type getPropertyType() {
        if (getField() != null) {
            return getField().getGenericType();
        } else if (getGetter() != null) {
            return getGetterType();
        } else if (getSetter() != null) {
            return getSetterType();
        }
        throw new JsonbException("Empty property: " + name);
    }

    /**
     * @param setter setter not null
     */
    public void setSetter(Method setter) {
        this.setterElement = new JsonbAnnotatedElement<>(setter);
    }

    /**
     * {@link Method} representing setter of a property if any.
     *
     * @return setter if present
     */
    public Method getSetter() {
        if (setterElement == null) {
            return null;
        }
        return setterElement.getElement();
    }

    /**
     * @param getter not null
     */
    public void setGetter(Method getter) {
        this.getterElement = new JsonbAnnotatedElement<>(getter);
    }

    Type getSetterType() {
        Type[] genericParameterTypes = getSetter().getGenericParameterTypes();
        if (genericParameterTypes.length != 1) {
            throw new JsonbException("Invalid count of arguments for setter: " + getSetter());
        }
        return genericParameterTypes[0];
    }

    Type getGetterType() {
        if (getGetter() != null) {
            return getGetter().getGenericReturnType();
        }
        return null;
    }

    /**
     * @param field field not null
     */
    public void setField(Field field) {
        this.fieldElement = new JsonbAnnotatedElement<>(field);
    }

    /**
     * Create instance of property.
     *
     * @param name                not null
     * @param declaringClassModel Class model for a class declaring property.
     */
    public Property(String name, JsonbAnnotatedElement<Class<?>> declaringClassModel) {
        this.name = name;
        this.declaringClassElement = declaringClassModel;
    }

    /**
     * Element with setter and its annotations.
     *
     * @return setter with annotations
     */
    public JsonbAnnotatedElement<Method> getSetterElement() {
        return setterElement;
    }

    /**
     * {@link Field} representing property if any.
     *
     * @return field if present
     */
    public Field getField() {
        if (fieldElement == null) {
            return null;
        }
        return fieldElement.getElement();
    }

    /**
     * Name of a property, java bean convention.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Class element with annotation under construction for declaring class of this property.
     * This ClassModel is not fully initialized yet.
     *
     * @return ClassModel
     */
    public JsonbAnnotatedElement<Class<?>> getDeclaringClassElement() {
        return declaringClassElement;
    }

    /**
     * {@link Method} representing getter of a property if any.
     *
     * @return getter if present
     */
    public Method getGetter() {
        if (getterElement == null) {
            return null;
        }
        return getterElement.getElement();
    }

    /**
     * Element with getter and its annotations.
     *
     * @return getter with annotations
     */
    public JsonbAnnotatedElement<Method> getGetterElement() {
        return getterElement;
    }

}

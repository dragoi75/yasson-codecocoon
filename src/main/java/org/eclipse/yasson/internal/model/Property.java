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

    private final JsonbAnnotationContainer<Class<?>> declaringClassElement;

    private JsonbAnnotationContainer<Field> fieldElement;

    private JsonbAnnotationContainer<Method> getterElement;

    private JsonbAnnotationContainer<Method> setterElement;

    /**
     * Create instance of property.
     *
     * @param name                not null
     * @param declaringClassModel Class model for a class declaring property.
     */
    public Property(String name, JsonbAnnotationContainer<Class<?>> declaringClassModel) {
        this.name = name;
        this.declaringClassElement = declaringClassModel;
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
     * {@link Field} representing property if any.
     *
     * @return field if present
     */
    public Field getField() {
        if (null == fieldElement) {
            return null;
        }
        return fieldElement.getElement();
    }

    /**
     * @param field field not null
     */
    public void setField(Field field) {
        this.fieldElement = new JsonbAnnotationContainer<>(field);
    }

    /**
     * {@link Method} representing getter of a property if any.
     *
     * @return getter if present
     */
    public Method getGetter() {
        if (null == getterElement) {
            return null;
        }
        return getterElement.getElement();
    }

    /**
     * @param getter not null
     */
    public void setGetter(Method getter) {
        this.getterElement = new JsonbAnnotationContainer<>(getter);
    }

    /**
     * {@link Method} representing setter of a property if any.
     *
     * @return setter if present
     */
    public Method getSetter() {
        if (null == setterElement) {
            return null;
        }
        return setterElement.getElement();
    }

    /**
     * @param setter setter not null
     */
    public void setSetter(Method setter) {
        this.setterElement = new JsonbAnnotationContainer<>(setter);
    }

    /**
     * Class element with annotation under construction for declaring class of this property.
     * This ClassModel is not fully initialized yet.
     *
     * @return ClassModel
     */
    public JsonbAnnotationContainer<Class<?>> getDeclaringClassElement() {
        return declaringClassElement;
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
        throw new JsonbException("Empty property: " + name);
    }

    Type getGetterType() {
        if (null != getGetter()) {
            return getGetter().getGenericReturnType();
        }
        return null;
    }

    Type getSetterType() {
        Type[] genericParameterTypes = getSetter().getGenericParameterTypes();
        if (1 != genericParameterTypes.length) {
            throw new JsonbException("Invalid count of arguments for setter: " + getSetter());
        }
        return genericParameterTypes[0];
    }

    /**
     * Element with field and its annotations.
     *
     * @return field with annotations
     */
    public JsonbAnnotationContainer<Field> getFieldElement() {
        return fieldElement;
    }

    /**
     * Element with getter and its annotations.
     *
     * @return getter with annotations
     */
    public JsonbAnnotationContainer<Method> getGetterElement() {
        return getterElement;
    }

    /**
     * Element with setter and its annotations.
     *
     * @return setter with annotations
     */
    public JsonbAnnotationContainer<Method> getSetterElement() {
        return setterElement;
    }
}

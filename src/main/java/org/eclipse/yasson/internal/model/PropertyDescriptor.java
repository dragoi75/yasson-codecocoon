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

    private final String propertyLabel;

    private final JsonbAnnotationHolder<Class<?>> parentClassHolder;

    private JsonbAnnotationHolder<Field> backingFieldHolder;

    private JsonbAnnotationHolder<Method> readMethodHolder;

    private JsonbAnnotationHolder<Method> writeMethodHolder;

    /**
     * @param readMethod not null
     */
    public void setGetter(Method readMethod) {
        this.readMethodHolder = new JsonbAnnotationHolder<>(readMethod);
    }

    /**
     * Element with field and its annotations.
     *
     * @return field with annotations
     */
    public JsonbAnnotationHolder<Field> getFieldElement() {
        return backingFieldHolder;
    }

    Type getSetterType() {
        Type[] genericParams = getSetter().getGenericParameterTypes();
        if (1 != genericParams.length) {
            throw new JsonbException("Invalid count of arguments for setter: " + getSetter());
        }
        return genericParams[0];
    }

    /**
     * Element with setter and its annotations.
     *
     * @return setter with annotations
     */
    public JsonbAnnotationHolder<Method> getSetterElement() {
        return writeMethodHolder;
    }

    /**
     * {@link Field} representing property if any.
     *
     * @return field if present
     */
    public Field getField() {
        if (null == backingFieldHolder) {
            return null;
        }
        return backingFieldHolder.getElement();
    }

    Type getGetterType() {
        if (null != getGetter()) {
            return getGetter().getGenericReturnType();
        }
        return null;
    }

    /**
     * @param backingMember field not null
     */
    public void setField(Field backingMember) {
        this.backingFieldHolder = new JsonbAnnotationHolder<>(backingMember);
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
        throw new JsonbException("Empty property: " + propertyLabel);
    }

    /**
     * Element with getter and its annotations.
     *
     * @return getter with annotations
     */
    public JsonbAnnotationHolder<Method> getGetterElement() {
        return readMethodHolder;
    }

    /**
     * @param writeMethod setter not null
     */
    public void setSetter(Method writeMethod) {
        this.writeMethodHolder = new JsonbAnnotationHolder<>(writeMethod);
    }

    /**
     * Name of a property, java bean convention.
     *
     * @return name
     */
    public String getName() {
        return propertyLabel;
    }

    /**
     * {@link Method} representing setter of a property if any.
     *
     * @return setter if present
     */
    public Method getSetter() {
        if (null == writeMethodHolder) {
            return null;
        }
        return writeMethodHolder.getElement();
    }

    /**
     * {@link Method} representing getter of a property if any.
     *
     * @return getter if present
     */
    public Method getGetter() {
        if (null == readMethodHolder) {
            return null;
        }
        return readMethodHolder.getElement();
    }

    /**
     * Create instance of property.
     *
     * @param propertyLabel                not null
     * @param ownerClassModel Class model for a class declaring property.
     */
    public PropertyDescriptor(String propertyLabel, JsonbAnnotationHolder<Class<?>> ownerClassModel) {
        this.propertyLabel = propertyLabel;
        this.parentClassHolder = ownerClassModel;
    }

    /**
     * Class element with annotation under construction for declaring class of this property.
     * This ClassModel is not fully initialized yet.
     *
     * @return ClassModel
     */
    public JsonbAnnotationHolder<Class<?>> getDeclaringClassElement() {
        return parentClassHolder;
    }

}

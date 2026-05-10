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

    private final String label;

    private final JsonbAnnotatedMember<Class<?>> declaringTypeMember;

    private JsonbAnnotatedMember<Field> memberVariable;

    private JsonbAnnotatedMember<Method> accessorMethodMember;

    private JsonbAnnotatedMember<Method> mutatorMethodMember;

    /**
     * Create instance of property.
     *
     * @param label                not null
     * @param declaringTypeModel Class model for a class declaring property.
     */
    public PropertyDescriptor(String label, JsonbAnnotatedMember<Class<?>> declaringTypeModel) {
        this.label = label;
        this.declaringTypeMember = declaringTypeModel;
    }

    /**
     * Name of a property, java bean convention.
     *
     * @return name
     */
    public String getName() {
        return label;
    }

    /**
     * {@link Field} representing property if any.
     *
     * @return field if present
     */
    public Field getField() {
        if (memberVariable == null) {
            return null;
        }
        return memberVariable.getElement();
    }

    /**
     * @param memberVariable field not null
     */
    public void setField(Field memberVariable) {
        this.memberVariable = new JsonbAnnotatedMember<>(memberVariable);
    }

    /**
     * {@link Method} representing getter of a property if any.
     *
     * @return getter if present
     */
    public Method getGetter() {
        if (accessorMethodMember == null) {
            return null;
        }
        return accessorMethodMember.getElement();
    }

    /**
     * @param accessor not null
     */
    public void setGetter(Method accessor) {
        this.accessorMethodMember = new JsonbAnnotatedMember<>(accessor);
    }

    /**
     * {@link Method} representing setter of a property if any.
     *
     * @return setter if present
     */
    public Method getSetter() {
        if (mutatorMethodMember == null) {
            return null;
        }
        return mutatorMethodMember.getElement();
    }

    /**
     * @param mutator setter not null
     */
    public void setSetter(Method mutator) {
        this.mutatorMethodMember = new JsonbAnnotatedMember<>(mutator);
    }

    /**
     * Class element with annotation under construction for declaring class of this property.
     * This ClassModel is not fully initialized yet.
     *
     * @return ClassModel
     */
    public JsonbAnnotatedMember<Class<?>> getDeclaringClassElement() {
        return declaringTypeMember;
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
        throw new JsonbException("Empty property: " + label);
    }

    Type getGetterType() {
        if (getGetter() != null) {
            return getGetter().getGenericReturnType();
        }
        return null;
    }

    Type getSetterType() {
        Type[] typeArguments = getSetter().getGenericParameterTypes();
        if (typeArguments.length != 1) {
            throw new JsonbException("Invalid count of arguments for setter: " + getSetter());
        }
        return typeArguments[0];
    }

    /**
     * Element with field and its annotations.
     *
     * @return field with annotations
     */
    public JsonbAnnotatedMember<Field> getFieldElement() {
        return memberVariable;
    }

    /**
     * Element with getter and its annotations.
     *
     * @return getter with annotations
     */
    public JsonbAnnotatedMember<Method> getGetterElement() {
        return accessorMethodMember;
    }

    /**
     * Element with setter and its annotations.
     *
     * @return setter with annotations
     */
    public JsonbAnnotatedMember<Method> getSetterElement() {
        return mutatorMethodMember;
    }

}

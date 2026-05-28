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
     * Element with setter and its annotations.
     *
     * @return setter with annotations
     */
    public JsonbAnnotatedMember<Method> getSetterElement() {
        return mutatorMethodMember;
    }

    /**
     * @param accessor not null
     */
    public void setGetter(Method accessor) {
        this.accessorMethodMember = new JsonbAnnotatedMember<>(accessor);
    }

    Type getSetterType() {
        Type[] typeArguments = getSetter().getGenericParameterTypes();
        if (1 != typeArguments.length) {
            throw new JsonbException("Invalid count of arguments for setter: " + getSetter());
        }
        return typeArguments[0];
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
     * Element with field and its annotations.
     *
     * @return field with annotations
     */
    public JsonbAnnotatedMember<Field> getFieldElement() {
        return memberVariable;
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
        throw new JsonbException("Empty property: " + label);
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
     * {@link Field} representing property if any.
     *
     * @return field if present
     */
    public Field getField() {
        if (null == memberVariable) {
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

    Type getGetterType() {
        if (null != getGetter()) {
            return getGetter().getGenericReturnType();
        }
        return null;
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
     * {@link Method} representing getter of a property if any.
     *
     * @return getter if present
     */
    public Method getGetter() {
        if (null == accessorMethodMember) {
            return null;
        }
        return accessorMethodMember.getElement();
    }

    /**
     * @param mutator setter not null
     */
    public void setSetter(Method mutator) {
        this.mutatorMethodMember = new JsonbAnnotatedMember<>(mutator);
    }

    /**
     * {@link Method} representing setter of a property if any.
     *
     * @return setter if present
     */
    public Method getSetter() {
        if (null == mutatorMethodMember) {
            return null;
        }
        return mutatorMethodMember.getElement();
    }

}

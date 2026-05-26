/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.model;

import javax.json.bind.JsonbException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * Property of a class, field, getter and setter methods (javabean alike).
 * Used during class model initialization, than dereferenced.
 *
 * @author Roman Grigoriadi
 */
public class PropertyDescriptor {

    private final String identifier;

    private final JsonbAnnotatedElement<Class<?>> enclosingClassMeta;

    private JsonbAnnotatedElement<Field> backingFieldMeta;

    private JsonbAnnotatedElement<Method> readMethodElement;

    private JsonbAnnotatedElement<Method> writeMethodElement;

    /**
     * {@link Method} representing setter of a property if any.
     *
     * @return setter if present
     */
    public Method getSetter() {
        if (null == writeMethodElement) {
            return null;
        }
        return writeMethodElement.getElement();
    }

    /**
     * Element with setter and its annotations.
     * @return setter with annotations
     */
    public JsonbAnnotatedElement<Method> getSetterElement() {
        return writeMethodElement;
    }

    public Type getGetterType() {
        if (null != getGetter()) {
            return getGetter().getGenericReturnType();
        }
        return null;
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

    /**
     * {@link Field} representing property if any
     *
     * @return field if present
     */
    public Field getField() {
        if (null == backingFieldMeta) {
            return null;
        }
        return backingFieldMeta.getElement();
    }

    /**
     * Element with field and its annotations.
     * @return field with annotations
     */
    public JsonbAnnotatedElement<Field> getFieldElement() {
        return backingFieldMeta;
    }

    /**
     * Class element with annotation under construction for declaring class of this property.
     * This ClassModel is not fully initialized yet.
     * @return ClassModel
     */
    public JsonbAnnotatedElement<Class<?>> getDeclaringClassElement() {
        return enclosingClassMeta;
    }

    /**
     * @param backingMember field not null
     */
    public void setField(Field backingMember) {
        this.backingFieldMeta = new JsonbAnnotatedElement<>(backingMember);
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
     * @param readMethod not null
     */
    public void setGetter(Method readMethod) {
        this.readMethodElement = new JsonbAnnotatedElement<>(readMethod);
    }

    /**
     * Element with getter and its annotations.
     * @return getter with annotations
     */
    public JsonbAnnotatedElement<Method> getGetterElement() {
        return readMethodElement;
    }

    /**
     * {@link Method} representing getter of a property if any.
     *
     * @return getter if present
     */
    public Method getGetter() {
        if (null == readMethodElement) {
            return null;
        }
        return readMethodElement.getElement();
    }

    public Type getSetterType() {
        Type[] typeParameters = getSetter().getGenericParameterTypes();
        if (1 != typeParameters.length) {
            throw new JsonbException("Invalid count of arguments for setter: " + getSetter());
        }
        return typeParameters[0];
    }

    /**
     * Create instance of property.
     * @param identifier not null
     * @param enclosingClassMeta Class model for a class declaring property.
     */
    public PropertyDescriptor(String identifier, JsonbAnnotatedElement<Class<?>> enclosingClassMeta) {
        this.identifier = identifier;
        this.enclosingClassMeta = enclosingClassMeta;
    }

    /**
     * @param writeMethod setter not null
     */
    public void setSetter(Method writeMethod) {
        this.writeMethodElement = new JsonbAnnotatedElement<>(writeMethod);
    }

}

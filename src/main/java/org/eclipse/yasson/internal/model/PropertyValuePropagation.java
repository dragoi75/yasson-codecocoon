/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  <p>
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.model;

import org.eclipse.yasson.internal.JsonbContext;
import javax.json.bind.config.PropertyVisibilityStrategy;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Optional;
import java.util.function.Function;

/**
 * Abstract class for getting / setting value into the property.
 *
 * @author Roman Grigoriadi
 */
public abstract class PropertyValuePropagation {

    private final Field field;

    private final Method getter;

    private final Method setter;

    private final PropertyVisibilityStrategy propertyVisibilityStrategy;

    /**
     * Mode of property propagation get or set.
     */
    public enum OperationMode {

        GET, SET
    }

    /**
     * Property can be written (unmarshalled from json)
     */
    protected boolean writable;

    /**
     * Property can be read (marshalled to json)
     */
    protected boolean readable;

    private final boolean getterVisible;

    private final boolean setterVisible;

    private static final class DefaultVisibilityStrategy implements PropertyVisibilityStrategy {

        private final Field field;

        private final Method method;

        @Override
        public boolean isVisible(Method method) {
            return Modifier.isPublic(method.getModifiers());
        }

        @Override
        public boolean isVisible(Field field) {
            //don't check field if getter is not visible (forced by spec)
            if (null != method && !isVisible(method)) {
                return false;
            }
            return Modifier.isPublic(field.getModifiers());
        }

        public DefaultVisibilityStrategy(Field field, Method method) {
            this.field = field;
            this.method = method;
        }

    }

    /**
     * Property is readable. Based on access policy and java field modifiers.
     * @return true if can be serialized to JSON
     */
    public boolean isReadable() {
        return readable;
    }

    public boolean isGetterVisible() {
        return getterVisible;
    }

    /**
     * Setter of a javabean property.
     *
     * @return {@link Method getter}
     */
    public Method getGetter() {
        return getter;
    }

    /**
     * Accept a {@link Method} to use value propagation.
     * @param method method
     * @param mode read or write
     */
    protected abstract void acceptMethod(Method method, OperationMode mode);

    /**
     * Field of a javabean property.
     *
     * @return {@link Field field}
     */
    public Field getField() {
        return field;
    }

    public boolean isSetterVisible() {
        return setterVisible;
    }

    /**
     * Getter of a javabean property.
     *
     * @return {@link Method setter}
     */
    public Method getSetter() {
        return setter;
    }

    /**
     * Look up class and package level @JsonbVisibility, or global config PropertyVisibilityStrategy.
     * If any is found it is used for resolving visibility by calling provided visibilityCheckFunction.
     *
     * @param visibilityCheckFunction function declaring visibility check
     * @return Optional with result of visibility check, or empty optional if no strategy is found
     */
    private Boolean isVisible(Function<PropertyVisibilityStrategy, Boolean> visibilityCheckFunction, Field field, Method method) {
        return null != propertyVisibilityStrategy ? visibilityCheckFunction.apply(propertyVisibilityStrategy) : visibilityCheckFunction.apply(new DefaultVisibilityStrategy(field, method));
    }

    private void initWritable(Field field, Method setter) {
        final boolean fieldWritable = null == field || 0 == (field.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC | Modifier.FINAL));
        if (!fieldWritable) {
            writable = false;
            return;
        }
        if (null == setter || !setterVisible || setter.getDeclaringClass().isAnonymousClass()) {
            if (isFieldVisible(field, setter) && !field.getDeclaringClass().isAnonymousClass()) {
                acceptField(field, OperationMode.SET);
                writable = true;
            }
        } else {
            acceptMethod(setter, OperationMode.SET);
            writable = true;
        }
    }

    /**
     * Accept a {@link Field} to use for value propagation.
     * @param field field
     * @param mode mod
     */
    protected abstract void acceptField(Field field, OperationMode mode);

    /**
     * Set a value to a field. Based on policy invokes a setter or sets directly to a field.
     *
     * @param object object to set value in
     * @param value value to set, null is valid
     */
    abstract void setValue(Object object, Object value);

    private void initReadable(Field field, Method getter) {
        final boolean fieldReadable = null == field || 0 == (field.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC));
        if (!fieldReadable) {
            readable = false;
            return;
        }
        if (null == getter || !getterVisible) {
            if (isFieldVisible(field, getter)) {
                acceptField(field, OperationMode.GET);
                readable = true;
            }
        } else {
            acceptMethod(getter, OperationMode.GET);
            readable = true;
        }
    }

    private boolean isMethodVisible(Field field, Method method) {
        if (null == method || Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        Boolean accessible = isVisible(strategy -> strategy.isVisible(method), field, method);
        //overridden by strategy, anonymous class, or lambda
        if (accessible && (!Modifier.isPublic(method.getModifiers()) || method.getDeclaringClass().isAnonymousClass() || method.getDeclaringClass().isSynthetic())) {
            overrideAccessible(method);
        }
        return accessible;
    }

    private void overrideAccessible(AccessibleObject accessibleObject) {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            accessibleObject.setAccessible(true);
            return null;
        });
    }

    private boolean isFieldVisible(Field field, Method method) {
        if (null == field) {
            return false;
        }
        Boolean accessible = isVisible(strategy -> strategy.isVisible(field), field, method);
        //overridden by strategy, or anonymous class (readable by spec)
        if (accessible && (!Modifier.isPublic(field.getModifiers()) || field.getDeclaringClass().isAnonymousClass())) {
            overrideAccessible(field);
        }
        return accessible;
    }

    /**
     * Property is writable. Based on access policy and java field modifiers.
     * @return true if can be deserialized from JSON
     */
    public boolean isWritable() {
        return writable;
    }

    /**
     * Construct a property propagation.
     *
     * @param property Provided property.
     */
    protected PropertyValuePropagation(Property property, PropertyVisibilityStrategy strategy) {
        this.field = property.getField();
        this.getter = property.getGetter();
        this.setter = property.getSetter();
        this.propertyVisibilityStrategy = strategy;
        this.getterVisible = isMethodVisible(field, getter);
        this.setterVisible = isMethodVisible(field, setter);
        initReadable(field, getter);
        initWritable(field, setter);
    }

    /**
     * Gets a value of a field. Based on policy invokes a getter or gets directly from a field.
     *
     * @param object object to get from
     */
    abstract Object getValue(Object object);

}

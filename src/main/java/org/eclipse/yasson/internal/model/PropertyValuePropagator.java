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
public abstract class PropertyValuePropagator {

    private final Field backingMember;

    private final Method readAccessor;

    private final Method writeAccessor;

    private final PropertyVisibilityStrategy visibilityResolver;

    /**
     * Mode of property propagation get or set.
     */
    public enum OperationType {

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

    private final boolean readAccessible;

    private final boolean writeAccessible;

    /**
     * Construct a property propagation.
     *
     * @param descriptor Provided property.
     */
    protected PropertyValuePropagator(PropertyDescriptor descriptor, PropertyVisibilityStrategy visibilityPolicy) {
        this.backingMember = descriptor.getField();
        this.readAccessor = descriptor.getGetter();
        this.writeAccessor = descriptor.getSetter();
        this.visibilityResolver = visibilityPolicy;
        this.readAccessible = isMethodVisible(backingMember, readAccessor);
        this.writeAccessible = isMethodVisible(backingMember, writeAccessor);
        initializeReadable(backingMember, readAccessor);
        initializeWritable(backingMember, writeAccessor);
    }

    private void initializeReadable(Field backingMember, Method readAccessor) {
        final boolean backingReadable = null == backingMember || 0 == (backingMember.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC));
        if (!backingReadable) {
            readable = false;
            return;
        }
        if (null == readAccessor || !readAccessible) {
            if (isFieldVisible(backingMember, readAccessor)) {
                registerField(backingMember, OperationType.GET);
                readable = true;
            }
        } else {
            registerMethod(readAccessor, OperationType.GET);
            readable = true;
        }
    }

    private void initializeWritable(Field backingMember, Method writeAccessor) {
        final boolean backingWritable = null == backingMember || 0 == (backingMember.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC | Modifier.FINAL));
        if (!backingWritable) {
            writable = false;
            return;
        }
        if (null == writeAccessor || !writeAccessible || writeAccessor.getDeclaringClass().isAnonymousClass()) {
            if (isFieldVisible(backingMember, writeAccessor) && !backingMember.getDeclaringClass().isAnonymousClass()) {
                registerField(backingMember, OperationType.SET);
                writable = true;
            }
        } else {
            registerMethod(writeAccessor, OperationType.SET);
            writable = true;
        }
    }

    private boolean isFieldVisible(Field backingMember, Method accessor) {
        if (null == backingMember) {
            return false;
        }
        Boolean isAccessAllowed = isVisible(visibilityPolicy -> visibilityPolicy.isVisible(backingMember), backingMember, accessor);
        //overridden by strategy, or anonymous class (readable by spec)
        if (isAccessAllowed && (!Modifier.isPublic(backingMember.getModifiers()) || backingMember.getDeclaringClass().isAnonymousClass())) {
            makeAccessible(backingMember);
        }
        return isAccessAllowed;
    }

    private boolean isMethodVisible(Field backingMember, Method accessor) {
        if (null == accessor || Modifier.isStatic(accessor.getModifiers())) {
            return false;
        }
        Boolean isAccessAllowed = isVisible(visibilityPolicy -> visibilityPolicy.isVisible(accessor), backingMember, accessor);
        //overridden by strategy, anonymous class, or lambda
        if (isAccessAllowed && (!Modifier.isPublic(accessor.getModifiers()) || accessor.getDeclaringClass().isAnonymousClass() || accessor.getDeclaringClass().isSynthetic())) {
            makeAccessible(accessor);
        }
        return isAccessAllowed;
    }

    private void makeAccessible(AccessibleObject reflectiveTarget) {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            reflectiveTarget.setAccessible(true);
            return null;
        });
    }

    /**
     * Look up class and package level @JsonbVisibility, or global config PropertyVisibilityStrategy.
     * If any is found it is used for resolving visibility by calling provided visibilityCheckFunction.
     *
     * @param visibilityTester function declaring visibility check
     * @return Optional with result of visibility check, or empty optional if no strategy is found
     */
    private Boolean isVisible(Function<PropertyVisibilityStrategy, Boolean> visibilityTester, Field backingMember, Method accessor) {
        return null != visibilityResolver ? visibilityTester.apply(visibilityResolver) : visibilityTester.apply(new StandardVisibilityStrategy(backingMember, accessor));
    }

    /**
     * Accept a {@link Method} to use value propagation.
     * @param accessor method
     * @param operationType read or write
     */
    protected abstract void registerMethod(Method accessor, OperationType operationType);

    /**
     * Accept a {@link Field} to use for value propagation.
     * @param backingMember field
     * @param operationType mod
     */
    protected abstract void registerField(Field backingMember, OperationType operationType);

    /**
     * Set a value to a field. Based on policy invokes a setter or sets directly to a field.
     *
     * @param target object to set value in
     * @param newContent value to set, null is valid
     */
    abstract void setValue(Object target, Object newContent);

    /**
     * Gets a value of a field. Based on policy invokes a getter or gets directly from a field.
     *
     * @param target object to get from
     */
    abstract Object getValue(Object target);

    /**
     * Property is writable. Based on access policy and java field modifiers.
     * @return true if can be deserialized from JSON
     */
    public boolean isWritable() {
        return writable;
    }

    /**
     * Property is readable. Based on access policy and java field modifiers.
     * @return true if can be serialized to JSON
     */
    public boolean isReadable() {
        return readable;
    }

    /**
     * Field of a javabean property.
     *
     * @return {@link Field field}
     */
    public Field getField() {
        return backingMember;
    }

    /**
     * Setter of a javabean property.
     *
     * @return {@link Method getter}
     */
    public Method getGetter() {
        return readAccessor;
    }

    /**
     * Getter of a javabean property.
     *
     * @return {@link Method setter}
     */
    public Method getSetter() {
        return writeAccessor;
    }

    public boolean isGetterVisible() {
        return readAccessible;
    }

    public boolean isSetterVisible() {
        return writeAccessible;
    }

    private static final class StandardVisibilityStrategy implements PropertyVisibilityStrategy {

        private final Field backingMember;

        private final Method accessor;

        public StandardVisibilityStrategy(Field backingMember, Method accessor) {
            this.backingMember = backingMember;
            this.accessor = accessor;
        }

        @Override
        public boolean isVisible(Field backingMember) {
            //don't check field if getter is not visible (forced by spec)
            if (null != accessor && !isVisible(accessor)) {
                return false;
            }
            return Modifier.isPublic(backingMember.getModifiers());
        }

        @Override
        public boolean isVisible(Method accessor) {
            return Modifier.isPublic(accessor.getModifiers());
        }
    }
}

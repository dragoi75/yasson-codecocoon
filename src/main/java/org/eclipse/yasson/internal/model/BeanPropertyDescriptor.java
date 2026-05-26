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
package org.eclipse.yasson.internal.model;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import org.eclipse.yasson.internal.AnnotationIntrospector;
import org.eclipse.yasson.internal.JsonBindingContext;
import org.eclipse.yasson.internal.JsonbDateFormatter;
import org.eclipse.yasson.internal.JsonbNumberFormatter;
import org.eclipse.yasson.internal.components.AdapterBindingInfo;
import org.eclipse.yasson.internal.components.JsonbSerializerBinding;
import org.eclipse.yasson.internal.model.customization.PropertyCustomization;

/**
 * A model for class property.
 * Property is JavaBean alike meta information field / getter / setter of a property in class.
 */
public final class BeanPropertyDescriptor implements Comparable<BeanPropertyDescriptor> {

    private static final MethodHandles.Lookup GLOBAL_RESOLVER = ModulesUtil.lookup();

    /**
     * Field propertyName as in class by java bean convention.
     */
    private final String propName;

    /**
     * Calculated name to be used when reading json document.
     */
    private final String getterName;

    /**
     * Calculated name to be used when writing json document.
     */
    private final String setterName;

    /**
     * Field propertyType.
     */
    private final Type propType;

    /**
     * Model of the class this field belongs to.
     */
    private final ClassDescriptor beanDescriptor;

    private final Property prop;

    /**
     * Customization of this property.
     */
    private final PropertyCustomization propertyCustomizer;

    private final MethodHandle valueHandle;

    private final MethodHandle setterHandle;

    private final Field backingField;

    private final Method readMethod;

    private final Method writeMethod;

    private final Type getterReturnType;

    private final Type setterParamType;

    /**
     * Create a new PropertyModel that merges two existing PropertyModel that have identical read/write names.
     * The input PropertyModel objects MUST be equal (a.equals(b) == true)
     *
     * @param primaryDescriptor a PropertyModel instance to merge
     * @param secondaryDescriptor the other PropertyModel instance to merge
     */
    public BeanPropertyDescriptor(BeanPropertyDescriptor primaryDescriptor, BeanPropertyDescriptor secondaryDescriptor) {
        if (!primaryDescriptor.equals(secondaryDescriptor)) {
            throw new IllegalStateException("Property models " + primaryDescriptor + " and " + secondaryDescriptor + " cannot be merged");
        }
        // Initial cloning steps
        this.beanDescriptor = primaryDescriptor.beanDescriptor;
        this.propName = primaryDescriptor.propName;
        this.getterName = primaryDescriptor.getterName;
        this.setterName = primaryDescriptor.setterName;
        this.propType = primaryDescriptor.propType;
        this.propertyCustomizer = primaryDescriptor.propertyCustomizer;
        // Merging steps
        this.getterReturnType = null != primaryDescriptor.getterReturnType ? primaryDescriptor.getterReturnType : secondaryDescriptor.getterReturnType;
        this.setterParamType = null != primaryDescriptor.setterParamType ? primaryDescriptor.setterParamType : secondaryDescriptor.setterParamType;
        this.prop = primaryDescriptor.prop;
        if (null != secondaryDescriptor.prop.getField()) {
            this.prop.setField(secondaryDescriptor.prop.getField());
        }
        if (null != secondaryDescriptor.prop.getGetter()) {
            this.prop.setGetter(secondaryDescriptor.prop.getGetter());
        }
        if (null != secondaryDescriptor.prop.getSetter()) {
            this.prop.setSetter(secondaryDescriptor.prop.getSetter());
        }
        this.backingField = prop.getField();
        this.readMethod = prop.getGetter();
        this.writeMethod = prop.getSetter();
        PropertyVisibilityStrategy visibilityStrategy = beanDescriptor.getClassCustomization().getPropertyVisibilityStrategy();
        this.valueHandle = createPropertyReadHandle(backingField, readMethod, isMethodVisible(readMethod, visibilityStrategy), visibilityStrategy);
        this.setterHandle = createPropertyWriteHandle(backingField, writeMethod, isMethodVisible(writeMethod, visibilityStrategy), visibilityStrategy);
    }

    /**
     * Creates an instance.
     *
     * @param beanDescriptor   Class model of declaring class.
     * @param prop     Property.
     * @param jsonbCtx Context.
     */
    public BeanPropertyDescriptor(ClassDescriptor beanDescriptor, Property prop, JsonBindingContext jsonbCtx) {
        this.beanDescriptor = beanDescriptor;
        this.prop = prop;
        this.propName = prop.getName();
        this.propType = prop.getPropertyType();
        this.backingField = prop.getField();
        this.readMethod = prop.getGetter();
        this.writeMethod = prop.getSetter();
        PropertyVisibilityStrategy visibilityStrategy = beanDescriptor.getClassCustomization().getPropertyVisibilityStrategy();
        boolean isGetterVisible = isMethodVisible(readMethod, visibilityStrategy);
        boolean isSetterVisible = isMethodVisible(writeMethod, visibilityStrategy);
        this.valueHandle = createPropertyReadHandle(backingField, readMethod, isGetterVisible, visibilityStrategy);
        this.setterHandle = createPropertyWriteHandle(backingField, writeMethod, isSetterVisible, visibilityStrategy);
        this.getterReturnType = isGetterVisible ? prop.getGetterType() : null;
        this.setterParamType = isSetterVisible ? prop.getSetterType() : null;
        this.propertyCustomizer = derivePropertyCustomization(prop, jsonbCtx);
        this.getterName = computeReadWriteName(propertyCustomizer.getJsonReadName(), propName, jsonbCtx.getConfigProperties().getPropertyNamingStrategy());
        this.setterName = computeReadWriteName(propertyCustomizer.getJsonWriteName(), propName, jsonbCtx.getConfigProperties().getPropertyNamingStrategy());
    }

    /**
     * Returns which type should be used to deserialization.
     *
     * @return deserialization type
     */
    public Type getPropertyDeserializationType() {
        return null == setterParamType ? propType : setterParamType;
    }

    /**
     * Returns which type should be used to serialization.
     *
     * @return serialization type
     */
    public Type getPropertySerializationType() {
        return null == getterReturnType ? propType : getterReturnType;
    }

    private JsonbSerializerBinding<?> getUserSerializerBinding(Property prop, JsonBindingContext jsonbCtx) {
        final JsonbSerializerBinding<?> userSerializer = jsonbCtx.getAnnotationIntrospector().getSerializerBinding(prop);
        if (null != userSerializer) {
            return userSerializer;
        }
        return jsonbCtx.getComponentMatcher().getSerializerBinding(getPropertySerializationType(), null).orElse(null);
    }

    private PropertyCustomization derivePropertyCustomization(Property prop, JsonBindingContext jsonbCtx) {
        final AnnotationIntrospector annotationIntrospector = jsonbCtx.getAnnotationIntrospector();
        final PropertyCustomization.Builder customizationBuilder = PropertyCustomization.builder();
        //drop all other annotations for transient properties
        EnumSet<AnnotationTarget> transientTargets = annotationIntrospector.getJsonbTransientCategorized(prop);
        if (0 != transientTargets.size()) {
            customizationBuilder.readTransient(transientTargets.contains(AnnotationTarget.GETTER));
            customizationBuilder.writeTransient(transientTargets.contains(AnnotationTarget.SETTER));
            if (transientTargets.contains(AnnotationTarget.PROPERTY)) {
                if (!transientTargets.contains(AnnotationTarget.GETTER)) {
                    customizationBuilder.readTransient(true);
                }
                if (!transientTargets.contains(AnnotationTarget.SETTER)) {
                    customizationBuilder.writeTransient(true);
                }
            }
            if (customizationBuilder.readTransient()) {
                annotationIntrospector.checkTransientIncompatible(prop.getFieldElement());
                annotationIntrospector.checkTransientIncompatible(prop.getGetterElement());
            }
            if (customizationBuilder.writeTransient()) {
                annotationIntrospector.checkTransientIncompatible(prop.getFieldElement());
                annotationIntrospector.checkTransientIncompatible(prop.getSetterElement());
            }
        }
        if (!customizationBuilder.readTransient()) {
            customizationBuilder.jsonWriteName(annotationIntrospector.getJsonbPropertyJsonWriteName(prop));
            customizationBuilder.nillable(annotationIntrospector.isPropertyNillable(prop).orElse(beanDescriptor.getClassCustomization().isNillable()));
            customizationBuilder.serializerBinding(getUserSerializerBinding(prop, jsonbCtx));
        }
        if (!customizationBuilder.writeTransient()) {
            customizationBuilder.jsonReadName(annotationIntrospector.getJsonbPropertyJsonReadName(prop));
            customizationBuilder.deserializerBinding(annotationIntrospector.getDeserializerBinding(prop));
        }
        final AdapterBindingInfo adapterInfo = jsonbCtx.getAnnotationIntrospector().getAdapterBinding(prop);
        if (null == adapterInfo) {
            customizationBuilder.serializeAdapter(jsonbCtx.getComponentMatcher().getSerializeAdapterBinding(getPropertySerializationType(), null).orElse(null));
            customizationBuilder.deserializeAdapter(jsonbCtx.getComponentMatcher().getDeserializeAdapterBinding(getPropertyDeserializationType(), null).orElse(null));
        } else {
            customizationBuilder.serializeAdapter(adapterInfo);
            customizationBuilder.deserializeAdapter(adapterInfo);
        }
        deriveDateFormatter(prop, annotationIntrospector, customizationBuilder, jsonbCtx);
        deriveNumberFormatter(prop, annotationIntrospector, customizationBuilder);
        customizationBuilder.implementationClass(annotationIntrospector.getImplementationClass(prop));
        return customizationBuilder.build();
    }

    private static void deriveDateFormatter(Property prop, AnnotationIntrospector annotationIntrospector, PropertyCustomization.Builder customizationBuilder, JsonBindingContext jsonbCtx) {
        /*
         * If @JsonbDateFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbDateFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbDateFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbDateFormatter> dateFormatByTarget = annotationIntrospector.getJsonbDateFormatCategorized(prop);
        final JsonbDateFormatter configFormatter = jsonbCtx.getConfigProperties().getConfigDateFormatter();
        if (!customizationBuilder.readTransient()) {
            final JsonbDateFormatter effectiveDateFormatter = getTargetForMostPreciseScope(dateFormatByTarget, AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);
            customizationBuilder.serializeDateFormatter(null != effectiveDateFormatter ? effectiveDateFormatter : configFormatter);
        }
        if (!customizationBuilder.writeTransient()) {
            final JsonbDateFormatter effectiveDateFormatter = getTargetForMostPreciseScope(dateFormatByTarget, AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);
            customizationBuilder.deserializeDateFormatter(null != effectiveDateFormatter ? effectiveDateFormatter : configFormatter);
        }
    }

    private static void deriveNumberFormatter(Property prop, AnnotationIntrospector annotationIntrospector, PropertyCustomization.Builder customizationBuilder) {
        /*
         * If @JsonbNumberFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbNumberFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbNumberFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbNumberFormatter> numberFormatMap = annotationIntrospector.getJsonNumberFormatter(prop);
        if (!customizationBuilder.readTransient()) {
            customizationBuilder.serializeNumberFormatter(getTargetForMostPreciseScope(numberFormatMap, AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS));
        }
        if (!customizationBuilder.writeTransient()) {
            customizationBuilder.deserializeNumberFormatter(getTargetForMostPreciseScope(numberFormatMap, AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS));
        }
    }

    /**
     * Pull result for most significant scope defined by order of annotation targets.
     *
     * @param annotationsMap all targets
     * @param targetArray              ordered target types by scope
     */
    private static <T> T getTargetForMostPreciseScope(Map<AnnotationTarget, T> annotationsMap, AnnotationTarget... targetArray) {
        for (AnnotationTarget annotationScope : targetArray) {
            final T selected = annotationsMap.get(annotationScope);
            if (null != selected) {
                return selected;
            }
        }
        return null;
    }

    /**
     * Gets property's value.
     *
     * @param instance object to read property from
     * @return property's value
     */
    public Object getValue(Object instance) {
        try {
            return valueHandle.invoke(instance);
        } catch (Throwable throwable) {
            throw new JsonbException("Error getting value on: " + instance, throwable);
        }
    }

    /**
     * Sets a property.
     *
     * If not writable (final, transient, static), ignores property.
     *
     * @param instance Object to set value in.
     * @param newVal  Value to set.
     */
    public void setValue(Object instance, Object newVal) {
        if (!isWritable()) {
            return;
        }
        try {
            setterHandle.invoke(instance, newVal);
        } catch (Throwable throwable) {
            throw new JsonbException("Error setting value on: " + instance, throwable);
        }
    }

    /**
     * Property is readable. Based on access policy and java field modifiers.
     *
     * @return true if can be serialized to JSON
     */
    public boolean isReadable() {
        return !propertyCustomizer.isReadTransient() && null != this.valueHandle;
    }

    /**
     * Property is writable. Based on access policy and java field modifiers.
     *
     * @return true if can be deserialized from JSON
     */
    public boolean isWritable() {
        return !propertyCustomizer.isWriteTransient() && null != this.setterHandle;
    }

    /**
     * Default property name according to Field / Getter / Setter method names.
     * This name is use for identifying properties, for JSON serialization is used customized name
     * which may be derived from default name.
     *
     * @return default name
     */
    public String getPropertyName() {
        return propName;
    }

    /**
     * Model of declaring class of this property.
     *
     * @return class model
     */
    public ClassDescriptor getClassModel() {
        return beanDescriptor;
    }

    /**
     * Introspected customization of a property.
     *
     * @return immutable property customization
     */
    public PropertyCustomization getCustomization() {
        return propertyCustomizer;
    }

    @Override
    public int compareTo(BeanPropertyDescriptor otherDescriptor) {
        int comparison = getterName.compareTo(otherDescriptor.getterName);
        return 0 == comparison ? setterName.compareTo(otherDescriptor.setterName) : comparison;
    }

    @Override
    public boolean equals(Object otherDescriptor) {
        if (otherDescriptor == this) {
            return true;
        }
        if (null == otherDescriptor || otherDescriptor.getClass() != getClass()) {
            return false;
        }
        BeanPropertyDescriptor comparedDescriptor = (BeanPropertyDescriptor) otherDescriptor;
        return Objects.equals(getterName, comparedDescriptor.getterName) && Objects.equals(setterName, comparedDescriptor.setterName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getterName, setterName);
    }

    /**
     * Gets a name of JSON document property to read this property from.
     *
     * @return Name of JSON document property.
     */
    public String getReadName() {
        return getterName;
    }

    public String getWriteName() {
        return setterName;
    }

    /**
     * If customized by JsonbPropertyAnnotation, than is used, otherwise use strategy to translate.
     * Since this is cached for performance reasons strategy has to be consistent
     * with calculated values for same input.
     */
    private static String computeReadWriteName(String readWriteKey, String propName, PropertyNamingStrategy visibilityStrategy) {
        return null != readWriteKey ? readWriteKey : visibilityStrategy.translateName(propName);
    }

    /**
     * Field of a javabean property.
     *
     * @return {@link Field field}
     */
    public Field getField() {
        return backingField;
    }

    /**
     * Setter of a javabean property.
     *
     * @return {@link Method getter}
     */
    public Method getGetter() {
        return readMethod;
    }

    /**
     * Getter of a javabean property.
     *
     * @return {@link Method setter}
     */
    public Method getSetter() {
        return writeMethod;
    }

    // Used in ClassParser
    public static boolean isPropertyReadable(Field backingField, Method readMethod, PropertyVisibilityStrategy visibilityStrategy) {
        return null != createPropertyReadHandle(backingField, readMethod, isMethodVisible(readMethod, visibilityStrategy), visibilityStrategy);
    }

    private static MethodHandle createPropertyReadHandle(Field backingField, Method readMethod, boolean isGetterVisible, PropertyVisibilityStrategy visibilityStrategy) {
        boolean isFieldReadable = null == backingField || 0 == (backingField.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC));
        if (isFieldReadable) {
            if (null != readMethod && isGetterVisible) {
                try {
                    return GLOBAL_RESOLVER.unreflect(readMethod);
                } catch (Throwable throwable) {
                    throw new JsonbException("Error accessing getter '" + readMethod.getName() + "' declared in '" + readMethod.getDeclaringClass() + "'", throwable);
                }
            }
            if (isFieldVisible(backingField, readMethod, visibilityStrategy)) {
                try {
                    return GLOBAL_RESOLVER.unreflectGetter(backingField);
                } catch (IllegalAccessException throwable) {
                    throw new JsonbException("Error accessing field '" + backingField.getName() + "' declared in '" + backingField.getDeclaringClass() + "'", throwable);
                }
            }
        }
        return null;
    }

    private static MethodHandle createPropertyWriteHandle(Field backingField, Method writeMethod, boolean isSetterVisible, PropertyVisibilityStrategy visibilityStrategy) {
        boolean isFieldWritable = null == backingField || 0 == (backingField.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC | Modifier.FINAL));
        if (isFieldWritable) {
            if (null != writeMethod && isSetterVisible && !writeMethod.getDeclaringClass().isAnonymousClass()) {
                try {
                    return GLOBAL_RESOLVER.unreflect(writeMethod);
                } catch (IllegalAccessException throwable) {
                    throw new JsonbException("Error accessing setter '" + writeMethod.getName() + "' declared in '" + writeMethod.getDeclaringClass() + "'", throwable);
                }
            }
            if (isFieldVisible(backingField, writeMethod, visibilityStrategy) && !backingField.getDeclaringClass().isAnonymousClass()) {
                try {
                    return GLOBAL_RESOLVER.unreflectSetter(backingField);
                } catch (IllegalAccessException throwable) {
                    throw new JsonbException("Error accessing field '" + backingField.getName() + "' declared in '" + backingField.getDeclaringClass() + "'", throwable);
                }
            }
        }
        return null;
    }

    private static boolean isFieldVisible(Field backingField, Method methodRef, PropertyVisibilityStrategy visibilityStrategy) {
        if (null == backingField) {
            return false;
        }
        boolean accessPermitted = isVisible(policyCandidate -> policyCandidate.isVisible(backingField), methodRef, visibilityStrategy);
        //overridden by strategy, or anonymous class (readable by spec)
        if (accessPermitted && (!Modifier.isPublic(backingField.getModifiers()) || backingField.getDeclaringClass().isAnonymousClass() || isNotPublicAndNonNested(backingField.getDeclaringClass()))) {
            setAccessiblePrivileged(backingField);
        }
        return accessPermitted;
    }

    private static boolean isNotPublicAndNonNested(Class<?> ownerType) {
        return !ownerType.isMemberClass() && !Modifier.isPublic(ownerType.getModifiers());
    }

    private static boolean isMethodVisible(Method methodRef, PropertyVisibilityStrategy visibilityStrategy) {
        if (null == methodRef || Modifier.isStatic(methodRef.getModifiers())) {
            return false;
        }
        boolean accessPermitted = isVisible(policyCandidate -> policyCandidate.isVisible(methodRef), methodRef, visibilityStrategy);
        //overridden by strategy, anonymous class, or lambda
        if (accessPermitted && (!Modifier.isPublic(methodRef.getModifiers()) || methodRef.getDeclaringClass().isAnonymousClass() || methodRef.getDeclaringClass().isSynthetic())) {
            setAccessiblePrivileged(methodRef);
        }
        return accessPermitted;
    }

    private static void setAccessiblePrivileged(AccessibleObject reflectiveObj) {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            reflectiveObj.setAccessible(true);
            return null;
        });
    }

    /**
     * Look up class and package level @JsonbVisibility, or global config PropertyVisibilityStrategy.
     * If any is found it is used for resolving visibility by calling provided visibilityCheckFunction.
     *
     * @param visibilityPredicate function declaring visibility check
     * @return Optional with result of visibility check, or empty optional if no strategy is found
     */
    private static boolean isVisible(Predicate<PropertyVisibilityStrategy> visibilityPredicate, Method methodRef, PropertyVisibilityStrategy visibilityStrategy) {
        return null != visibilityStrategy ? visibilityPredicate.test(visibilityStrategy) : visibilityPredicate.test(new DefaultVisibilityPolicy(methodRef));
    }

    private static final class DefaultVisibilityPolicy implements PropertyVisibilityStrategy {

        private final Method methodRef;

        DefaultVisibilityPolicy(Method methodRef) {
            this.methodRef = methodRef;
        }

        @Override
        public boolean isVisible(Field backingField) {
            //don't check field if getter is not visible (forced by spec)
            return (null == methodRef || isVisible(methodRef)) && Modifier.isPublic(backingField.getModifiers());
        }

        @Override
        public boolean isVisible(Method methodRef) {
            return Modifier.isPublic(methodRef.getModifiers());
        }
    }

    public MethodHandle getGetValueHandle() {
        return valueHandle;
    }

    public MethodHandle getSetValueHandle() {
        return setterHandle;
    }
}

/*
 * Copyright (c) 2015, 2021 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;
import java.util.function.Predicate;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.eclipse.yasson.internal.JsonbAnnotationIntrospector;
import org.eclipse.yasson.internal.JsonbContextManager;
import org.eclipse.yasson.internal.ReflectionUtils;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;
import org.eclipse.yasson.internal.model.customization.PropertyCustomization;
import org.eclipse.yasson.internal.model.customization.PropertyCustomizationBuilder;
import org.eclipse.yasson.internal.serializer.AdaptedObjectSerializer;
import org.eclipse.yasson.internal.serializer.StandardSerializerRegistry;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;
import org.eclipse.yasson.internal.serializer.SerializerProviderAdapter;
import org.eclipse.yasson.internal.serializer.UserSerializerSerializer;

/**
 * A model for class property.
 * Property is JavaBean alike meta information field / getter / setter of a property in class.
 */
public final class BeanPropertyModel implements Comparable<BeanPropertyModel> {

    private static final MethodHandles.Lookup PROPERTY_REGISTRY = ModulesUtil.lookup();

    /**
     * Field propertyName as in class by java bean convention.
     */
    private final String fieldName;

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
    private final Type valueType;

    /**
     * Model of the class this field belongs to.
     */
    private final ClassDescriptor beanDescriptor;

    private final PropertyDescriptor descriptor;

    /**
     * Customization of this property.
     */
    private final PropertyCustomization customConfig;

    private final JsonbSerializer<?> valueSerializer;

    private final MethodHandle valueHandle;

    private final MethodHandle setterHandle;

    private final Field member;

    private final Method accessor;

    private final Method mutator;

    private final Type accessorMethodType;

    private final Type mutatorMethodType;

    private static final class DefaultMethodVisibilityStrategy implements PropertyVisibilityStrategy {

        private final Method executableMember;

        @Override
        public boolean isVisible(Field member) {
            //don't check field if getter is not visible (forced by spec)
            return (null == executableMember || isVisible(executableMember)) && Modifier.isPublic(member.getModifiers());
        }

        @Override
        public boolean isVisible(Method executableMember) {
            return Modifier.isPublic(executableMember.getModifiers());
        }

        DefaultMethodVisibilityStrategy(Method executableMember) {
            this.executableMember = executableMember;
        }

    }

    /**
     * If customized by JsonbPropertyAnnotation, than is used, otherwise use strategy to translate.
     * Since this is cached for performance reasons strategy has to be consistent
     * with calculated values for same input.
     */
    private static String computeReadWriteName(String rwPropertyName, String fieldName, PropertyNamingStrategy visibilityPolicy) {
        return null != rwPropertyName ? rwPropertyName : visibilityPolicy.translateName(fieldName);
    }

    /**
     * Pull result for most significant scope defined by order of annotation targets.
     *
     * @param annotationMap all targets
     * @param annotationTargets              ordered target types by scope
     */
    private static <T> T getTargetForMostPreciseScope(Map<AnnotationTarget, T> annotationMap, AnnotationTarget... annotationTargets) {
        for (AnnotationTarget annotationTarget : annotationTargets) {
            final T selected = annotationMap.get(annotationTarget);
            if (null != selected) {
                return selected;
            }
        }
        return null;
    }

    private SerializerBinding<?> getUserSerializerBinding(PropertyDescriptor descriptor, JsonbContextManager contextManager) {
        final SerializerBinding<?> bindingEntry = contextManager.getAnnotationIntrospector().getSerializerBinding(descriptor);
        if (null != bindingEntry) {
            return bindingEntry;
        }
        return contextManager.getComponentMatcher().getSerializerBinding(getPropertySerializationType(), null).orElse(null);
    }

    /**
     * Gets a name of JSON document property to read this property from.
     *
     * @return Name of JSON document property.
     */
    public String getReadName() {
        return getterName;
    }

    private static void setAccessiblePrivileged(AccessibleObject targetObject) {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            targetObject.setAccessible(true);
            return null;
        });
    }

    /**
     * Returns which type should be used to serialization.
     *
     * @return serialization type
     */
    public Type getPropertySerializationType() {
        return null == accessorMethodType ? valueType : accessorMethodType;
    }

    private static MethodHandle createGetterHandle(Field member, Method accessor, boolean isGetterVisible, PropertyVisibilityStrategy visibilityPolicy) {
        boolean isFieldReadable = null == member || 0 == (member.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC));
        if (isFieldReadable) {
            if (null != accessor && isGetterVisible) {
                try {
                    return PROPERTY_REGISTRY.unreflect(accessor);
                } catch (Throwable ex) {
                    throw new JsonbException("Error accessing getter '" + accessor.getName() + "' declared in '" + accessor.getDeclaringClass() + "'", ex);
                }
            }
            if (isFieldVisible(member, accessor, visibilityPolicy)) {
                try {
                    return PROPERTY_REGISTRY.unreflectGetter(member);
                } catch (IllegalAccessException ex) {
                    throw new JsonbException("Error accessing field '" + member.getName() + "' declared in '" + member.getDeclaringClass() + "'", ex);
                }
            }
        }
        return null;
    }

    /**
     * Look up class and package level @JsonbVisibility, or global config PropertyVisibilityStrategy.
     * If any is found it is used for resolving visibility by calling provided visibilityCheckFunction.
     *
     * @param visibilityPredicate function declaring visibility check
     * @return Optional with result of visibility check, or empty optional if no strategy is found
     */
    private static boolean isVisible(Predicate<PropertyVisibilityStrategy> visibilityPredicate, Method executableMember, PropertyVisibilityStrategy visibilityPolicy) {
        return null != visibilityPolicy ? visibilityPredicate.test(visibilityPolicy) : visibilityPredicate.test(new DefaultMethodVisibilityStrategy(executableMember));
    }

    /**
     * Property is writable. Based on access policy and java field modifiers.
     *
     * @return true if can be deserialized from JSON
     */
    public boolean isWritable() {
        return !customConfig.isWriteTransient() && null != this.setterHandle;
    }

    private static boolean isNotPublicAndNonNested(Class<?> ownerClass) {
        return !ownerClass.isMemberClass() && !Modifier.isPublic(ownerClass.getModifiers());
    }

    /**
     * Default property name according to Field / Getter / Setter method names.
     * This name is use for identifying properties, for JSON serialization is used customized name
     * which may be derived from default name.
     *
     * @return default name
     */
    public String getPropertyName() {
        return fieldName;
    }

    private static boolean isFieldVisible(Field member, Method executableMember, PropertyVisibilityStrategy visibilityPolicy) {
        if (null == member) {
            return false;
        }
        boolean canAccess = isVisible(policyArg -> policyArg.isVisible(member), executableMember, visibilityPolicy);
        //overridden by strategy, or anonymous class (readable by spec)
        if (canAccess && (!Modifier.isPublic(member.getModifiers()) || member.getDeclaringClass().isAnonymousClass() || isNotPublicAndNonNested(member.getDeclaringClass()))) {
            setAccessiblePrivileged(member);
        }
        return canAccess;
    }

    /**
     * Field of a javabean property.
     *
     * @return {@link Field field}
     */
    public Field getField() {
        return member;
    }

    public String getWriteName() {
        return setterName;
    }

    /**
     * Try to cache serializer for this bean property. Only if type cannot be changed during runtime.
     *
     * @return serializer instance to be cached
     */
    @SuppressWarnings("unchecked")
    private JsonbSerializer<?> getCachedSerializer() {
        Type serializedType = getPropertySerializationType();
        if (!ReflectionUtils.isResolvedType(serializedType)) {
            return null;
        }
        if (null != customConfig.getSerializeAdapterBinding()) {
            return new AdaptedObjectSerializer<>(beanDescriptor, customConfig.getSerializeAdapterBinding());
        }
        if (null != customConfig.getSerializerBinding()) {
            return new UserSerializerSerializer<>(beanDescriptor, customConfig.getSerializerBinding().getJsonbSerializer());
        }
        final Class<?> rawValueClass = ReflectionUtils.getRawType(serializedType);
        final Optional<SerializerProviderAdapter> serializerProviderOptional = StandardSerializerRegistry.locateValueSerializerProvider(rawValueClass);
        if (serializerProviderOptional.isPresent()) {
            return serializerProviderOptional.get().getSerializerProvider().provideSerializer(customConfig);
        }
        return null;
    }

    private PropertyCustomization inspectCustomization(PropertyDescriptor descriptor, JsonbContextManager contextManager) {
        final JsonbAnnotationIntrospector annotationInspector = contextManager.getAnnotationIntrospector();
        final PropertyCustomizationBuilder customizationBuilderInstance = new PropertyCustomizationBuilder();
        //drop all other annotations for transient properties
        EnumSet<AnnotationTarget> transientTargets = annotationInspector.getJsonbTransientCategorized(descriptor);
        if (0 != transientTargets.size()) {
            customizationBuilderInstance.setReadTransient(transientTargets.contains(AnnotationTarget.GETTER));
            customizationBuilderInstance.setWriteTransient(transientTargets.contains(AnnotationTarget.SETTER));
            if (transientTargets.contains(AnnotationTarget.PROPERTY)) {
                if (!transientTargets.contains(AnnotationTarget.GETTER)) {
                    customizationBuilderInstance.setReadTransient(true);
                }
                if (!transientTargets.contains(AnnotationTarget.SETTER)) {
                    customizationBuilderInstance.setWriteTransient(true);
                }
            }
            if (customizationBuilderInstance.isReadTransient()) {
                annotationInspector.validateTransientCompatibility(descriptor.getFieldElement());
                annotationInspector.validateTransientCompatibility(descriptor.getGetterElement());
            }
            if (customizationBuilderInstance.isWriteTransient()) {
                annotationInspector.validateTransientCompatibility(descriptor.getFieldElement());
                annotationInspector.validateTransientCompatibility(descriptor.getSetterElement());
            }
        }
        if (!customizationBuilderInstance.isReadTransient()) {
            customizationBuilderInstance.setJsonWriteName(annotationInspector.getJsonbPropertyJsonWriteName(descriptor));
            customizationBuilderInstance.setNillable(annotationInspector.isPropertyNillable(descriptor).orElse(beanDescriptor.getClassCustomization().isNillable()));
            customizationBuilderInstance.setSerializerBinding(getUserSerializerBinding(descriptor, contextManager));
        }
        if (!customizationBuilderInstance.isWriteTransient()) {
            customizationBuilderInstance.setJsonReadName(annotationInspector.getJsonbPropertyJsonReadName(descriptor));
            customizationBuilderInstance.setDeserializerBinding(annotationInspector.getDeserializerBinding(descriptor));
        }
        final AdapterBinding adapterLink = contextManager.getAnnotationIntrospector().getAdapterBinding(descriptor);
        if (null == adapterLink) {
            customizationBuilderInstance.setSerializeAdapter(contextManager.getComponentMatcher().getSerializeAdapterBinding(getPropertySerializationType(), null).orElse(null));
            customizationBuilderInstance.setDeserializeAdapter(contextManager.getComponentMatcher().getDeserializeAdapterBinding(getPropertyDeserializationType(), null).orElse(null));
        } else {
            customizationBuilderInstance.setSerializeAdapter(adapterLink);
            customizationBuilderInstance.setDeserializeAdapter(adapterLink);
        }
        determineDateFormatter(descriptor, annotationInspector, customizationBuilderInstance, contextManager);
        determineNumberFormatter(descriptor, annotationInspector, customizationBuilderInstance);
        customizationBuilderInstance.setImplementationClass(annotationInspector.getImplementationClass(descriptor));
        return customizationBuilderInstance.buildPropertyCustomization();
    }

    private static void determineDateFormatter(PropertyDescriptor descriptor, JsonbAnnotationIntrospector annotationInspector, PropertyCustomizationBuilder customizationBuilderInstance, JsonbContextManager contextManager) {
        /*
         * If @JsonbDateFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbDateFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbDateFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbDateFormatter> dateFormatByTarget = annotationInspector.getJsonbDateFormatCategorized(descriptor);
        final JsonbDateFormatter configuredDateFormatter = contextManager.getConfigProperties().getConfigDateFormatter();
        if (!customizationBuilderInstance.isReadTransient()) {
            final JsonbDateFormatter resolvedDateFormatter = getTargetForMostPreciseScope(dateFormatByTarget, AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);
            customizationBuilderInstance.setSerializeDateFormatter(null != resolvedDateFormatter ? resolvedDateFormatter : configuredDateFormatter);
        }
        if (!customizationBuilderInstance.isWriteTransient()) {
            final JsonbDateFormatter resolvedDateFormatter = getTargetForMostPreciseScope(dateFormatByTarget, AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);
            customizationBuilderInstance.setDeserializeDateFormatter(null != resolvedDateFormatter ? resolvedDateFormatter : configuredDateFormatter);
        }
    }

    /**
     * Getter of a javabean property.
     *
     * @return {@link Method setter}
     */
    public Method getSetter() {
        return mutator;
    }

    private static MethodHandle createSetterHandle(Field member, Method mutator, boolean isSetterVisible, PropertyVisibilityStrategy visibilityPolicy) {
        boolean isFieldWritable = null == member || 0 == (member.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC | Modifier.FINAL));
        if (isFieldWritable) {
            if (null != mutator && isSetterVisible && !mutator.getDeclaringClass().isAnonymousClass()) {
                try {
                    return PROPERTY_REGISTRY.unreflect(mutator);
                } catch (IllegalAccessException ex) {
                    throw new JsonbException("Error accessing setter '" + mutator.getName() + "' declared in '" + mutator.getDeclaringClass() + "'", ex);
                }
            }
            if (isFieldVisible(member, mutator, visibilityPolicy) && !member.getDeclaringClass().isAnonymousClass()) {
                try {
                    return PROPERTY_REGISTRY.unreflectSetter(member);
                } catch (IllegalAccessException ex) {
                    throw new JsonbException("Error accessing field '" + member.getName() + "' declared in '" + member.getDeclaringClass() + "'", ex);
                }
            }
        }
        return null;
    }

    /**
     * Introspected customization of a property.
     *
     * @return immutable property customization
     */
    public PropertyCustomization getCustomization() {
        return customConfig;
    }

    /**
     * Setter of a javabean property.
     *
     * @return {@link Method getter}
     */
    public Method getGetter() {
        return accessor;
    }

    // Used in ClassParser
    public static boolean isPropertyReadable(Field member, Method accessor, PropertyVisibilityStrategy visibilityPolicy) {
        return null != createGetterHandle(member, accessor, isMethodVisible(accessor, visibilityPolicy), visibilityPolicy);
    }

    /**
     * Gets serializer.
     *
     * @return Serializer.
     */
    public JsonbSerializer<?> getPropertySerializer() {
        return valueSerializer;
    }

    @Override
    public int compareTo(BeanPropertyModel otherModel) {
        int cmpResult = getterName.compareTo(otherModel.getterName);
        return 0 == cmpResult ? setterName.compareTo(otherModel.setterName) : cmpResult;
    }

    /**
     * Returns which type should be used to deserialization.
     *
     * @return deserialization type
     */
    public Type getPropertyDeserializationType() {
        return null == mutatorMethodType ? valueType : mutatorMethodType;
    }

    private static boolean isMethodVisible(Method executableMember, PropertyVisibilityStrategy visibilityPolicy) {
        if (null == executableMember || Modifier.isStatic(executableMember.getModifiers())) {
            return false;
        }
        boolean canAccess = isVisible(policyArg -> policyArg.isVisible(executableMember), executableMember, visibilityPolicy);
        //overridden by strategy, anonymous class, or lambda
        if (canAccess && (!Modifier.isPublic(executableMember.getModifiers()) || executableMember.getDeclaringClass().isAnonymousClass() || executableMember.getDeclaringClass().isSynthetic())) {
            setAccessiblePrivileged(executableMember);
        }
        return canAccess;
    }

    @Override
    public boolean equals(Object otherPropertyModel) {
        if (otherPropertyModel == this) {
            return true;
        }
        if (null == otherPropertyModel || otherPropertyModel.getClass() != getClass()) {
            return false;
        }
        BeanPropertyModel otherModel = (BeanPropertyModel) otherPropertyModel;
        return Objects.equals(getterName, otherModel.getterName) && Objects.equals(setterName, otherModel.setterName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getterName, setterName);
    }

    /**
     * Property is readable. Based on access policy and java field modifiers.
     *
     * @return true if can be serialized to JSON
     */
    public boolean isReadable() {
        return !customConfig.isReadTransient() && null != this.valueHandle;
    }

    /**
     * Creates an instance.
     *
     * @param beanDescriptor   Class model of declaring class.
     * @param descriptor     Property.
     * @param contextManager Context.
     */
    public BeanPropertyModel(ClassDescriptor beanDescriptor, PropertyDescriptor descriptor, JsonbContextManager contextManager) {
        this.beanDescriptor = beanDescriptor;
        this.descriptor = descriptor;
        this.fieldName = descriptor.getName();
        this.valueType = descriptor.getPropertyType();
        this.member = descriptor.getField();
        this.accessor = descriptor.getGetter();
        this.mutator = descriptor.getSetter();
        PropertyVisibilityStrategy visibilityPolicy = beanDescriptor.getClassCustomization().getPropertyVisibilityStrategy();
        boolean isGetterVisible = isMethodVisible(accessor, visibilityPolicy);
        boolean isSetterVisible = isMethodVisible(mutator, visibilityPolicy);
        this.valueHandle = createGetterHandle(member, accessor, isGetterVisible, visibilityPolicy);
        this.setterHandle = createSetterHandle(member, mutator, isSetterVisible, visibilityPolicy);
        this.accessorMethodType = isGetterVisible ? descriptor.getGetterType() : null;
        this.mutatorMethodType = isSetterVisible ? descriptor.getSetterType() : null;
        this.customConfig = inspectCustomization(descriptor, contextManager);
        this.getterName = computeReadWriteName(customConfig.getJsonReadName(), fieldName, contextManager.getConfigProperties().getPropertyNamingStrategy());
        this.setterName = computeReadWriteName(customConfig.getJsonWriteName(), fieldName, contextManager.getConfigProperties().getPropertyNamingStrategy());
        this.valueSerializer = getCachedSerializer();
    }

    /**
     * Sets a property.
     *
     * If not writable (final, transient, static), ignores property.
     *
     * @param source Object to set value in.
     * @param newVal  Value to set.
     */
    public void setValue(Object source, Object newVal) {
        if (!isWritable()) {
            return;
        }
        try {
            setterHandle.invoke(source, newVal);
        } catch (Throwable ex) {
            throw new JsonbException("Error setting value on: " + source, ex);
        }
    }

    /**
     * Create a new PropertyModel that merges two existing PropertyModel that have identical read/write names.
     * The input PropertyModel objects MUST be equal (a.equals(b) == true)
     * @param other a PropertyModel instance to merge
     * @param another the other PropertyModel instance to merge
     */
    public BeanPropertyModel(BeanPropertyModel other, BeanPropertyModel another) {
        if (!other.equals(another)) {
            throw new IllegalStateException("Property models " + other + " and " + another + " cannot be merged");
        }
        // Initial cloning steps
        this.beanDescriptor = other.beanDescriptor;
        this.fieldName = other.fieldName;
        this.getterName = other.getterName;
        this.setterName = other.setterName;
        this.valueType = other.valueType;
        this.customConfig = other.customConfig;
        // Merging steps
        this.accessorMethodType = null != other.accessorMethodType ? other.accessorMethodType : another.accessorMethodType;
        this.mutatorMethodType = null != other.mutatorMethodType ? other.mutatorMethodType : another.mutatorMethodType;
        this.descriptor = other.descriptor;
        if (null != another.descriptor.getField()) {
            this.descriptor.setField(another.descriptor.getField());
        }
        if (null != another.descriptor.getGetter()) {
            this.descriptor.setGetter(another.descriptor.getGetter());
        }
        if (null != another.descriptor.getSetter()) {
            this.descriptor.setSetter(another.descriptor.getSetter());
        }
        this.member = descriptor.getField();
        this.accessor = descriptor.getGetter();
        this.mutator = descriptor.getSetter();
        PropertyVisibilityStrategy visibilityPolicy = beanDescriptor.getClassCustomization().getPropertyVisibilityStrategy();
        this.valueHandle = createGetterHandle(member, accessor, isMethodVisible(accessor, visibilityPolicy), visibilityPolicy);
        this.setterHandle = createSetterHandle(member, mutator, isMethodVisible(mutator, visibilityPolicy), visibilityPolicy);
        this.valueSerializer = getCachedSerializer();
    }

    /**
     * Gets property's value.
     *
     * @param source object to read property from
     * @return property's value
     */
    public Object getValue(Object source) {
        try {
            return valueHandle.invoke(source);
        } catch (Throwable ex) {
            throw new JsonbException("Error getting value on: " + source, ex);
        }
    }

    private static void determineNumberFormatter(PropertyDescriptor descriptor, JsonbAnnotationIntrospector annotationInspector, PropertyCustomizationBuilder customizationBuilderInstance) {
        /*
         * If @JsonbNumberFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbNumberFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbNumberFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbNumberFormatter> numberFormatByTarget = annotationInspector.getJsonNumberFormatter(descriptor);
        if (!customizationBuilderInstance.isReadTransient()) {
            customizationBuilderInstance.setSerializeNumberFormatter(getTargetForMostPreciseScope(numberFormatByTarget, AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS));
        }
        if (!customizationBuilderInstance.isWriteTransient()) {
            customizationBuilderInstance.setDeserializeNumberFormatter(getTargetForMostPreciseScope(numberFormatByTarget, AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS));
        }
    }

    /**
     * Model of declaring class of this property.
     *
     * @return class model
     */
    public ClassDescriptor getClassModel() {
        return beanDescriptor;
    }

}

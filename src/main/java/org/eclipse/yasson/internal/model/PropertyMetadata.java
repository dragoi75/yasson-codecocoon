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
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.ReflectionUtils;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;
import org.eclipse.yasson.internal.model.customization.PropertyCustomization;
import org.eclipse.yasson.internal.model.customization.PropertyCustomizationBuilder;
import org.eclipse.yasson.internal.serializer.AdaptedObjectSerializer;
import org.eclipse.yasson.internal.serializer.DefaultSerializers;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;
import org.eclipse.yasson.internal.serializer.SerializerProviderWrapper;
import org.eclipse.yasson.internal.serializer.UserSerializerSerializer;

/**
 * A model for class property.
 * Property is JavaBean alike meta information field / getter / setter of a property in class.
 */
public final class PropertyMetadata implements Comparable<PropertyMetadata> {

    private static final MethodHandles.Lookup DEFAULT_RESOLVER = ModulesUtil.lookup();

    /**
     * Field propertyName as in class by java bean convention.
     */
    private final String attributeName;

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
    private final Type attributeType;

    /**
     * Model of the class this field belongs to.
     */
    private final ClassDescriptor declaringClass;

    private final PropertyDescriptor attributeDescriptor;

    /**
     * Customization of this property.
     */
    private final PropertyCustomization customOptions;

    private final JsonbSerializer<?> valueSerializer;

    private final MethodHandle getterHandle;

    private final MethodHandle setterHandle;

    private final Field reflectedField;

    private final Method readMethod;

    private final Method writeMethod;

    private final Type readMethodType;

    private final Type writeMethodType;

    /**
     * Create a new PropertyModel that merges two existing PropertyModel that have identical read/write names.
     * The input PropertyModel objects MUST be equal (a.equals(b) == true)
     * @param firstMetadata a PropertyModel instance to merge
     * @param secondMetadata the other PropertyModel instance to merge
     */
    public PropertyMetadata(PropertyMetadata firstMetadata, PropertyMetadata secondMetadata) {
        if (!firstMetadata.equals(secondMetadata)) {
            throw new IllegalStateException("Property models " + firstMetadata + " and " + secondMetadata + " cannot be merged");
        }
        // Initial cloning steps
        this.declaringClass = firstMetadata.declaringClass;
        this.attributeName = firstMetadata.attributeName;
        this.getterName = firstMetadata.getterName;
        this.setterName = firstMetadata.setterName;
        this.attributeType = firstMetadata.attributeType;
        this.customOptions = firstMetadata.customOptions;
        // Merging steps
        this.readMethodType = null != firstMetadata.readMethodType ? firstMetadata.readMethodType : secondMetadata.readMethodType;
        this.writeMethodType = null != firstMetadata.writeMethodType ? firstMetadata.writeMethodType : secondMetadata.writeMethodType;
        this.attributeDescriptor = firstMetadata.attributeDescriptor;
        if (null != secondMetadata.attributeDescriptor.getField()) {
            this.attributeDescriptor.setField(secondMetadata.attributeDescriptor.getField());
        }
        if (null != secondMetadata.attributeDescriptor.getGetter()) {
            this.attributeDescriptor.setGetter(secondMetadata.attributeDescriptor.getGetter());
        }
        if (null != secondMetadata.attributeDescriptor.getSetter()) {
            this.attributeDescriptor.setSetter(secondMetadata.attributeDescriptor.getSetter());
        }
        this.reflectedField = attributeDescriptor.getField();
        this.readMethod = attributeDescriptor.getGetter();
        this.writeMethod = attributeDescriptor.getSetter();
        PropertyVisibilityStrategy visibilityPolicy = declaringClass.getClassCustomization().getPropertyVisibilityStrategy();
        this.getterHandle = createGetterHandle(reflectedField, readMethod, isMethodVisible(readMethod, visibilityPolicy), visibilityPolicy);
        this.setterHandle = createSetterHandle(reflectedField, writeMethod, isMethodVisible(writeMethod, visibilityPolicy), visibilityPolicy);
        this.valueSerializer = getCachedSerializer();
    }

    /**
     * Creates an instance.
     *
     * @param declaringClass   Class model of declaring class.
     * @param attributeDescriptor     Property.
     * @param runtimeContext Context.
     */
    public PropertyMetadata(ClassDescriptor declaringClass, PropertyDescriptor attributeDescriptor, JsonbRuntimeContext runtimeContext) {
        this.declaringClass = declaringClass;
        this.attributeDescriptor = attributeDescriptor;
        this.attributeName = attributeDescriptor.getName();
        this.attributeType = attributeDescriptor.getPropertyType();
        this.reflectedField = attributeDescriptor.getField();
        this.readMethod = attributeDescriptor.getGetter();
        this.writeMethod = attributeDescriptor.getSetter();
        PropertyVisibilityStrategy visibilityPolicy = declaringClass.getClassCustomization().getPropertyVisibilityStrategy();
        boolean readAccessible = isMethodVisible(readMethod, visibilityPolicy);
        boolean writeAccessible = isMethodVisible(writeMethod, visibilityPolicy);
        this.getterHandle = createGetterHandle(reflectedField, readMethod, readAccessible, visibilityPolicy);
        this.setterHandle = createSetterHandle(reflectedField, writeMethod, writeAccessible, visibilityPolicy);
        this.readMethodType = readAccessible ? attributeDescriptor.getGetterType() : null;
        this.writeMethodType = writeAccessible ? attributeDescriptor.getSetterType() : null;
        this.customOptions = inspectCustomization(attributeDescriptor, runtimeContext);
        this.getterName = determineReadWriteName(customOptions.getJsonReadName(), attributeName, runtimeContext.getConfigProperties().getPropertyNamingStrategy());
        this.setterName = determineReadWriteName(customOptions.getJsonWriteName(), attributeName, runtimeContext.getConfigProperties().getPropertyNamingStrategy());
        this.valueSerializer = getCachedSerializer();
    }

    /**
     * Try to cache serializer for this bean property. Only if type cannot be changed during runtime.
     *
     * @return serializer instance to be cached
     */
    @SuppressWarnings("unchecked")
    private JsonbSerializer<?> getCachedSerializer() {
        Type formatType = getPropertySerializationType();
        if (!ReflectionUtils.isResolvedType(formatType)) {
            return null;
        }
        if (null != customOptions.getSerializeAdapterBinding()) {
            return new AdaptedObjectSerializer<>(declaringClass, customOptions.getSerializeAdapterBinding());
        }
        if (null != customOptions.getSerializerBinding()) {
            return new UserSerializerSerializer<>(declaringClass, customOptions.getSerializerBinding().getJsonbSerializer());
        }
        final Class<?> rawTypeClass = ReflectionUtils.getRawType(formatType);
        final Optional<SerializerProviderWrapper> serializerProviderOptional = DefaultSerializers.findValueSerializerProvider(rawTypeClass);
        if (serializerProviderOptional.isPresent()) {
            return serializerProviderOptional.get().getSerializerProvider().provideSerializer(customOptions);
        }
        return null;
    }

    /**
     * Returns which type should be used to deserialization.
     *
     * @return deserialization type
     */
    public Type getPropertyDeserializationType() {
        return null == writeMethodType ? attributeType : writeMethodType;
    }

    /**
     * Returns which type should be used to serialization.
     *
     * @return serialization type
     */
    public Type getPropertySerializationType() {
        return null == readMethodType ? attributeType : readMethodType;
    }

    private SerializerBinding<?> getUserSerializerBinding(PropertyDescriptor attributeDescriptor, JsonbRuntimeContext runtimeContext) {
        final SerializerBinding<?> bindingEntry = runtimeContext.getAnnotationIntrospector().getSerializerBinding(attributeDescriptor);
        if (null != bindingEntry) {
            return bindingEntry;
        }
        return runtimeContext.getComponentMatcher().getSerializerBinding(getPropertySerializationType(), null).orElse(null);
    }

    private PropertyCustomization inspectCustomization(PropertyDescriptor attributeDescriptor, JsonbRuntimeContext runtimeContext) {
        final JsonbAnnotationIntrospector annotationInspector = runtimeContext.getAnnotationIntrospector();
        final PropertyCustomizationBuilder customizationFactory = new PropertyCustomizationBuilder();
        //drop all other annotations for transient properties
        EnumSet<AnnotationTarget> ignoredAnnotationTargets = annotationInspector.getJsonbTransientCategorized(attributeDescriptor);
        if (0 != ignoredAnnotationTargets.size()) {
            customizationFactory.setReadTransient(ignoredAnnotationTargets.contains(AnnotationTarget.GETTER));
            customizationFactory.setWriteTransient(ignoredAnnotationTargets.contains(AnnotationTarget.SETTER));
            if (ignoredAnnotationTargets.contains(AnnotationTarget.PROPERTY)) {
                if (!ignoredAnnotationTargets.contains(AnnotationTarget.GETTER)) {
                    customizationFactory.setReadTransient(true);
                }
                if (!ignoredAnnotationTargets.contains(AnnotationTarget.SETTER)) {
                    customizationFactory.setWriteTransient(true);
                }
            }
            if (customizationFactory.isReadTransient()) {
                annotationInspector.validateTransientCompatibility(attributeDescriptor.getFieldElement());
                annotationInspector.validateTransientCompatibility(attributeDescriptor.getGetterElement());
            }
            if (customizationFactory.isWriteTransient()) {
                annotationInspector.validateTransientCompatibility(attributeDescriptor.getFieldElement());
                annotationInspector.validateTransientCompatibility(attributeDescriptor.getSetterElement());
            }
        }
        if (!customizationFactory.isReadTransient()) {
            customizationFactory.setJsonWriteName(annotationInspector.getJsonbPropertyJsonWriteName(attributeDescriptor));
            customizationFactory.setNillable(annotationInspector.isPropertyNillable(attributeDescriptor).orElse(declaringClass.getClassCustomization().isNillable()));
            customizationFactory.setSerializerBinding(getUserSerializerBinding(attributeDescriptor, runtimeContext));
        }
        if (!customizationFactory.isWriteTransient()) {
            customizationFactory.setJsonReadName(annotationInspector.getJsonbPropertyJsonReadName(attributeDescriptor));
            customizationFactory.setDeserializerBinding(annotationInspector.getDeserializerBinding(attributeDescriptor));
        }
        final AdapterBinding adapterRef = runtimeContext.getAnnotationIntrospector().getAdapterBinding(attributeDescriptor);
        if (null == adapterRef) {
            customizationFactory.setSerializeAdapter(runtimeContext.getComponentMatcher().getSerializeAdapterBinding(getPropertySerializationType(), null).orElse(null));
            customizationFactory.setDeserializeAdapter(runtimeContext.getComponentMatcher().getDeserializeAdapterBinding(getPropertyDeserializationType(), null).orElse(null));
        } else {
            customizationFactory.setSerializeAdapter(adapterRef);
            customizationFactory.setDeserializeAdapter(adapterRef);
        }
        resolveDateFormatter(attributeDescriptor, annotationInspector, customizationFactory, runtimeContext);
        resolveNumberFormatter(attributeDescriptor, annotationInspector, customizationFactory);
        customizationFactory.setImplementationClass(annotationInspector.getImplementationClass(attributeDescriptor));
        return customizationFactory.buildPropertyCustomization();
    }

    private static void resolveDateFormatter(PropertyDescriptor attributeDescriptor, JsonbAnnotationIntrospector annotationInspector, PropertyCustomizationBuilder customizationFactory, JsonbRuntimeContext runtimeContext) {
        /*
         * If @JsonbDateFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbDateFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbDateFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbDateFormatter> dateFormatByTarget = annotationInspector.getJsonbDateFormatCategorized(attributeDescriptor);
        final JsonbDateFormatter configuredDateFormatter = runtimeContext.getConfigProperties().getConfigDateFormatter();
        if (!customizationFactory.isReadTransient()) {
            final JsonbDateFormatter resolvedDateFormatter = getTargetForMostPreciseScope(dateFormatByTarget, AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);
            customizationFactory.setSerializeDateFormatter(null != resolvedDateFormatter ? resolvedDateFormatter : configuredDateFormatter);
        }
        if (!customizationFactory.isWriteTransient()) {
            final JsonbDateFormatter resolvedDateFormatter = getTargetForMostPreciseScope(dateFormatByTarget, AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);
            customizationFactory.setDeserializeDateFormatter(null != resolvedDateFormatter ? resolvedDateFormatter : configuredDateFormatter);
        }
    }

    private static void resolveNumberFormatter(PropertyDescriptor attributeDescriptor, JsonbAnnotationIntrospector annotationInspector, PropertyCustomizationBuilder customizationFactory) {
        /*
         * If @JsonbNumberFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbNumberFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbNumberFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbNumberFormatter> numberFormatByTarget = annotationInspector.getJsonNumberFormatter(attributeDescriptor);
        if (!customizationFactory.isReadTransient()) {
            customizationFactory.setSerializeNumberFormatter(getTargetForMostPreciseScope(numberFormatByTarget, AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS));
        }
        if (!customizationFactory.isWriteTransient()) {
            customizationFactory.setDeserializeNumberFormatter(getTargetForMostPreciseScope(numberFormatByTarget, AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS));
        }
    }

    /**
     * Pull result for most significant scope defined by order of annotation targets.
     *
     * @param annotationsMap all targets
     * @param annotationTargets              ordered target types by scope
     */
    private static <T> T getTargetForMostPreciseScope(Map<AnnotationTarget, T> annotationsMap, AnnotationTarget... annotationTargets) {
        for (AnnotationTarget annotationTarget : annotationTargets) {
            final T resolvedValue = annotationsMap.get(annotationTarget);
            if (null != resolvedValue) {
                return resolvedValue;
            }
        }
        return null;
    }

    /**
     * Gets property's value.
     *
     * @param sourceObject object to read property from
     * @return property's value
     */
    public Object getValue(Object sourceObject) {
        try {
            return getterHandle.invoke(sourceObject);
        } catch (Throwable cause) {
            throw new JsonbException("Error getting value on: " + sourceObject, cause);
        }
    }

    /**
     * Sets a property.
     *
     * If not writable (final, transient, static), ignores property.
     *
     * @param sourceObject Object to set value in.
     * @param newValue  Value to set.
     */
    public void setValue(Object sourceObject, Object newValue) {
        if (!isWritable()) {
            return;
        }
        try {
            setterHandle.invoke(sourceObject, newValue);
        } catch (Throwable cause) {
            throw new JsonbException("Error setting value on: " + sourceObject, cause);
        }
    }

    /**
     * Property is readable. Based on access policy and java field modifiers.
     *
     * @return true if can be serialized to JSON
     */
    public boolean isReadable() {
        return !customOptions.isReadTransient() && null != this.getterHandle;
    }

    /**
     * Property is writable. Based on access policy and java field modifiers.
     *
     * @return true if can be deserialized from JSON
     */
    public boolean isWritable() {
        return !customOptions.isWriteTransient() && null != this.setterHandle;
    }

    /**
     * Default property name according to Field / Getter / Setter method names.
     * This name is use for identifying properties, for JSON serialization is used customized name
     * which may be derived from default name.
     *
     * @return default name
     */
    public String getPropertyName() {
        return attributeName;
    }

    /**
     * Model of declaring class of this property.
     *
     * @return class model
     */
    public ClassDescriptor getClassModel() {
        return declaringClass;
    }

    /**
     * Introspected customization of a property.
     *
     * @return immutable property customization
     */
    public PropertyCustomization getCustomization() {
        return customOptions;
    }

    @Override
    public int compareTo(PropertyMetadata otherMetadata) {
        int comparisonResult = getterName.compareTo(otherMetadata.getterName);
        return 0 == comparisonResult ? setterName.compareTo(otherMetadata.setterName) : comparisonResult;
    }

    @Override
    public boolean equals(Object otherProp) {
        if (otherProp == this) {
            return true;
        }
        if (null == otherProp || otherProp.getClass() != getClass()) {
            return false;
        }
        PropertyMetadata otherMetadata = (PropertyMetadata) otherProp;
        return Objects.equals(getterName, otherMetadata.getterName) && Objects.equals(setterName, otherMetadata.setterName);
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
     * Gets serializer.
     *
     * @return Serializer.
     */
    public JsonbSerializer<?> getPropertySerializer() {
        return valueSerializer;
    }

    /**
     * If customized by JsonbPropertyAnnotation, than is used, otherwise use strategy to translate.
     * Since this is cached for performance reasons strategy has to be consistent
     * with calculated values for same input.
     */
    private static String determineReadWriteName(String rwName, String attributeName, PropertyNamingStrategy visibilityPolicy) {
        return null != rwName ? rwName : visibilityPolicy.translateName(attributeName);
    }

    /**
     * Field of a javabean property.
     *
     * @return {@link Field field}
     */
    public Field getField() {
        return reflectedField;
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
    public static boolean isPropertyReadable(Field reflectedField, Method readMethod, PropertyVisibilityStrategy visibilityPolicy) {
        return null != createGetterHandle(reflectedField, readMethod, isMethodVisible(readMethod, visibilityPolicy), visibilityPolicy);
    }

    private static MethodHandle createGetterHandle(Field reflectedField, Method readMethod, boolean readAccessible, PropertyVisibilityStrategy visibilityPolicy) {
        boolean isFieldReadable = null == reflectedField || 0 == (reflectedField.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC));
        if (isFieldReadable) {
            if (null != readMethod && readAccessible) {
                try {
                    return DEFAULT_RESOLVER.unreflect(readMethod);
                } catch (Throwable cause) {
                    throw new JsonbException("Error accessing getter '" + readMethod.getName() + "' declared in '" + readMethod.getDeclaringClass() + "'", cause);
                }
            }
            if (isFieldVisible(reflectedField, readMethod, visibilityPolicy)) {
                try {
                    return DEFAULT_RESOLVER.unreflectGetter(reflectedField);
                } catch (IllegalAccessException cause) {
                    throw new JsonbException("Error accessing field '" + reflectedField.getName() + "' declared in '" + reflectedField.getDeclaringClass() + "'", cause);
                }
            }
        }
        return null;
    }

    private static MethodHandle createSetterHandle(Field reflectedField, Method writeMethod, boolean writeAccessible, PropertyVisibilityStrategy visibilityPolicy) {
        boolean isFieldWritable = null == reflectedField || 0 == (reflectedField.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC | Modifier.FINAL));
        if (isFieldWritable) {
            if (null != writeMethod && writeAccessible && !writeMethod.getDeclaringClass().isAnonymousClass()) {
                try {
                    return DEFAULT_RESOLVER.unreflect(writeMethod);
                } catch (IllegalAccessException cause) {
                    throw new JsonbException("Error accessing setter '" + writeMethod.getName() + "' declared in '" + writeMethod.getDeclaringClass() + "'", cause);
                }
            }
            if (isFieldVisible(reflectedField, writeMethod, visibilityPolicy) && !reflectedField.getDeclaringClass().isAnonymousClass()) {
                try {
                    return DEFAULT_RESOLVER.unreflectSetter(reflectedField);
                } catch (IllegalAccessException cause) {
                    throw new JsonbException("Error accessing field '" + reflectedField.getName() + "' declared in '" + reflectedField.getDeclaringClass() + "'", cause);
                }
            }
        }
        return null;
    }

    private static boolean isFieldVisible(Field reflectedField, Method accessor, PropertyVisibilityStrategy visibilityPolicy) {
        if (null == reflectedField) {
            return false;
        }
        boolean canAccess = isVisible(visPolicy -> visPolicy.isVisible(reflectedField), accessor, visibilityPolicy);
        //overridden by strategy, or anonymous class (readable by spec)
        if (canAccess && (!Modifier.isPublic(reflectedField.getModifiers()) || reflectedField.getDeclaringClass().isAnonymousClass() || isNotPublicAndNonNested(reflectedField.getDeclaringClass()))) {
            setAccessiblePrivileged(reflectedField);
        }
        return canAccess;
    }

    private static boolean isNotPublicAndNonNested(Class<?> ownerClass) {
        return !ownerClass.isMemberClass() && !Modifier.isPublic(ownerClass.getModifiers());
    }

    private static boolean isMethodVisible(Method accessor, PropertyVisibilityStrategy visibilityPolicy) {
        if (null == accessor || Modifier.isStatic(accessor.getModifiers())) {
            return false;
        }
        boolean canAccess = isVisible(visPolicy -> visPolicy.isVisible(accessor), accessor, visibilityPolicy);
        //overridden by strategy, anonymous class, or lambda
        if (canAccess && (!Modifier.isPublic(accessor.getModifiers()) || accessor.getDeclaringClass().isAnonymousClass() || accessor.getDeclaringClass().isSynthetic())) {
            setAccessiblePrivileged(accessor);
        }
        return canAccess;
    }

    private static void setAccessiblePrivileged(AccessibleObject targetObject) {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            targetObject.setAccessible(true);
            return null;
        });
    }

    /**
     * Look up class and package level @JsonbVisibility, or global config PropertyVisibilityStrategy.
     * If any is found it is used for resolving visibility by calling provided visibilityCheckFunction.
     *
     * @param visibilityChecker function declaring visibility check
     * @return Optional with result of visibility check, or empty optional if no strategy is found
     */
    private static boolean isVisible(Predicate<PropertyVisibilityStrategy> visibilityChecker, Method accessor, PropertyVisibilityStrategy visibilityPolicy) {
        return null != visibilityPolicy ? visibilityChecker.test(visibilityPolicy) : visibilityChecker.test(new DefaultVisibilityPolicy(accessor));
    }

    private static final class DefaultVisibilityPolicy implements PropertyVisibilityStrategy {

        private final Method accessor;

        DefaultVisibilityPolicy(Method accessor) {
            this.accessor = accessor;
        }

        @Override
        public boolean isVisible(Field reflectedField) {
            //don't check field if getter is not visible (forced by spec)
            return (null == accessor || isVisible(accessor)) && Modifier.isPublic(reflectedField.getModifiers());
        }

        @Override
        public boolean isVisible(Method accessor) {
            return Modifier.isPublic(accessor.getModifiers());
        }
    }
}

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
import org.eclipse.yasson.internal.AnnotationIntrospector;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.ReflectiveTypeResolver;
import org.eclipse.yasson.internal.components.AdapterBindingEntry;
import org.eclipse.yasson.internal.components.SerializerBindingEntry;
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
public final class PropertyModel implements Comparable<PropertyModel> {

    private static final MethodHandles.Lookup LOOKUP = ModulesUtil.lookup();

    /**
     * Field propertyName as in class by java bean convention.
     */
    private final String propertyName;

    /**
     * Calculated name to be used when reading json document.
     */
    private final String readName;

    /**
     * Calculated name to be used when writing json document.
     */
    private final String writeName;

    /**
     * Field propertyType.
     */
    private final Type propertyType;

    /**
     * Model of the class this field belongs to.
     */
    private final ClassModel classModel;

    private final Property property;

    /**
     * Customization of this property.
     */
    private final PropertyCustomization customization;

    private final JsonbSerializer<?> propertySerializer;

    private final MethodHandle getValueHandle;

    private final MethodHandle setValueHandle;

    private final Field field;

    private final Method getter;

    private final Method setter;

    private final Type getterMethodType;

    private final Type setterMethodType;

    private static final class DefaultVisibilityStrategy implements PropertyVisibilityStrategy {

        private final Method method;

        @Override
        public boolean isVisible(Method method) {
            return Modifier.isPublic(method.getModifiers());
        }

        @Override
        public boolean isVisible(Field field) {
            //don't check field if getter is not visible (forced by spec)
            return (null == method || isVisible(method)) && Modifier.isPublic(field.getModifiers());
        }

        DefaultVisibilityStrategy(Method method) {
            this.method = method;
        }

    }

    private static MethodHandle createReadHandle(Field field, Method getter, boolean getterVisible, PropertyVisibilityStrategy strategy) {
        boolean fieldReadable = null == field || 0 == (field.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC));
        if (fieldReadable) {
            if (null != getter && getterVisible) {
                try {
                    return LOOKUP.unreflect(getter);
                } catch (Throwable e) {
                    throw new JsonbException("Error accessing getter '" + getter.getName() + "' declared in '" + getter.getDeclaringClass() + "'", e);
                }
            }
            if (isFieldVisible(field, getter, strategy)) {
                try {
                    return LOOKUP.unreflectGetter(field);
                } catch (IllegalAccessException e) {
                    throw new JsonbException("Error accessing field '" + field.getName() + "' declared in '" + field.getDeclaringClass() + "'", e);
                }
            }
        }
        return null;
    }

    private static void introspectNumberFormatter(Property property, AnnotationIntrospector introspector, PropertyCustomizationBuilder builder) {
        /*
         * If @JsonbNumberFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbNumberFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbNumberFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbNumberFormatter> jsonNumberFormatCategorized = introspector.getJsonNumberFormatter(property);
        if (!builder.isReadTransient()) {
            builder.setSerializeNumberFormatter(getTargetForMostPreciseScope(jsonNumberFormatCategorized, AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS));
        }
        if (!builder.isWriteTransient()) {
            builder.setDeserializeNumberFormatter(getTargetForMostPreciseScope(jsonNumberFormatCategorized, AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS));
        }
    }

    /**
     * Field of a javabean property.
     *
     * @return {@link Field field}
     */
    public Field getField() {
        return field;
    }

    /**
     * Model of declaring class of this property.
     *
     * @return class model
     */
    public ClassModel getClassModel() {
        return classModel;
    }

    /**
     * Look up class and package level @JsonbVisibility, or global config PropertyVisibilityStrategy.
     * If any is found it is used for resolving visibility by calling provided visibilityCheckFunction.
     *
     * @param visibilityCheckFunction function declaring visibility check
     * @return Optional with result of visibility check, or empty optional if no strategy is found
     */
    private static boolean isVisible(Predicate<PropertyVisibilityStrategy> visibilityCheckFunction, Method method, PropertyVisibilityStrategy strategy) {
        return null != strategy ? visibilityCheckFunction.test(strategy) : visibilityCheckFunction.test(new DefaultVisibilityStrategy(method));
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
     * Property is readable. Based on access policy and java field modifiers.
     *
     * @return true if can be serialized to JSON
     */
    public boolean isReadable() {
        return !customization.isReadTransient() && null != this.getValueHandle;
    }

    private PropertyCustomization introspectCustomization(Property property, JsonbRuntimeContext jsonbContext) {
        final AnnotationIntrospector introspector = jsonbContext.getAnnotationIntrospector();
        final PropertyCustomizationBuilder builder = new PropertyCustomizationBuilder();
        //drop all other annotations for transient properties
        EnumSet<AnnotationTarget> transientInfo = introspector.getJsonbTransientCategorized(property);
        if (0 != transientInfo.size()) {
            builder.setReadTransient(transientInfo.contains(AnnotationTarget.GETTER));
            builder.setWriteTransient(transientInfo.contains(AnnotationTarget.SETTER));
            if (transientInfo.contains(AnnotationTarget.PROPERTY)) {
                if (!transientInfo.contains(AnnotationTarget.GETTER)) {
                    builder.setReadTransient(true);
                }
                if (!transientInfo.contains(AnnotationTarget.SETTER)) {
                    builder.setWriteTransient(true);
                }
            }
            if (builder.isReadTransient()) {
                introspector.checkTransientIncompatible(property.getFieldElement());
                introspector.checkTransientIncompatible(property.getGetterElement());
            }
            if (builder.isWriteTransient()) {
                introspector.checkTransientIncompatible(property.getFieldElement());
                introspector.checkTransientIncompatible(property.getSetterElement());
            }
        }
        if (!builder.isReadTransient()) {
            builder.setJsonWriteName(introspector.getJsonbPropertyJsonWriteName(property));
            builder.setNillable(introspector.isPropertyNillable(property).orElse(classModel.getClassCustomization().isNillable()));
            builder.setSerializerBinding(getUserSerializerBinding(property, jsonbContext));
        }
        if (!builder.isWriteTransient()) {
            builder.setJsonReadName(introspector.getJsonbPropertyJsonReadName(property));
            builder.setDeserializerBinding(introspector.getDeserializerBinding(property));
        }
        final AdapterBindingEntry adapterBinding = jsonbContext.getAnnotationIntrospector().getAdapterBinding(property);
        if (null == adapterBinding) {
            builder.setSerializeAdapter(jsonbContext.getComponentMatcher().getSerializeAdapterBinding(getPropertySerializationType(), null).orElse(null));
            builder.setDeserializeAdapter(jsonbContext.getComponentMatcher().getDeserializeAdapterBinding(getPropertyDeserializationType(), null).orElse(null));
        } else {
            builder.setSerializeAdapter(adapterBinding);
            builder.setDeserializeAdapter(adapterBinding);
        }
        introspectDateFormatter(property, introspector, builder, jsonbContext);
        introspectNumberFormatter(property, introspector, builder);
        builder.setImplementationClass(introspector.getImplementationClass(property));
        return builder.buildPropertyCustomization();
    }

    private static MethodHandle createWriteHandle(Field field, Method setter, boolean setterVisible, PropertyVisibilityStrategy strategy) {
        boolean fieldWritable = null == field || 0 == (field.getModifiers() & (Modifier.TRANSIENT | Modifier.STATIC | Modifier.FINAL));
        if (fieldWritable) {
            if (null != setter && setterVisible && !setter.getDeclaringClass().isAnonymousClass()) {
                try {
                    return LOOKUP.unreflect(setter);
                } catch (IllegalAccessException e) {
                    throw new JsonbException("Error accessing setter '" + setter.getName() + "' declared in '" + setter.getDeclaringClass() + "'", e);
                }
            }
            if (isFieldVisible(field, setter, strategy) && !field.getDeclaringClass().isAnonymousClass()) {
                try {
                    return LOOKUP.unreflectSetter(field);
                } catch (IllegalAccessException e) {
                    throw new JsonbException("Error accessing field '" + field.getName() + "' declared in '" + field.getDeclaringClass() + "'", e);
                }
            }
        }
        return null;
    }

    /**
     * Try to cache serializer for this bean property. Only if type cannot be changed during runtime.
     *
     * @return serializer instance to be cached
     */
    @SuppressWarnings("unchecked")
    private JsonbSerializer<?> resolveCachedSerializer() {
        Type serializationType = getPropertySerializationType();
        if (!ReflectiveTypeResolver.isResolvedType(serializationType)) {
            return null;
        }
        if (null != customization.getSerializeAdapterBinding()) {
            return new AdaptedObjectSerializer<>(classModel, customization.getSerializeAdapterBinding());
        }
        if (null != customization.getSerializerBinding()) {
            return new UserSerializerSerializer<>(classModel, customization.getSerializerBinding().getJsonbSerializer());
        }
        final Class<?> propertyRawType = ReflectiveTypeResolver.getRawType(serializationType);
        final Optional<SerializerProviderWrapper> valueSerializerProvider = DefaultSerializers.findValueSerializerProvider(propertyRawType);
        if (valueSerializerProvider.isPresent()) {
            return valueSerializerProvider.get().getSerializerProvider().provideSerializer(customization);
        }
        return null;
    }

    /**
     * Gets serializer.
     *
     * @return Serializer.
     */
    public JsonbSerializer<?> getPropertySerializer() {
        return propertySerializer;
    }

    /**
     * Gets a name of JSON document property to read this property from.
     *
     * @return Name of JSON document property.
     */
    public String getReadName() {
        return readName;
    }

    @Override
    public int hashCode() {
        return Objects.hash(readName, writeName);
    }

    /**
     * Returns which type should be used to serialization.
     *
     * @return serialization type
     */
    public Type getPropertySerializationType() {
        return null == getterMethodType ? propertyType : getterMethodType;
    }

    public String getWriteName() {
        return writeName;
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
     * Pull result for most significant scope defined by order of annotation targets.
     *
     * @param collectedAnnotations all targets
     * @param targets              ordered target types by scope
     */
    private static <T> T getTargetForMostPreciseScope(Map<AnnotationTarget, T> collectedAnnotations, AnnotationTarget... targets) {
        for (AnnotationTarget target : targets) {
            final T result = collectedAnnotations.get(target);
            if (null != result) {
                return result;
            }
        }
        return null;
    }

    private static void overrideAccessible(AccessibleObject accessibleObject) {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            accessibleObject.setAccessible(true);
            return null;
        });
    }

    /**
     * Sets a property.
     *
     * If not writable (final, transient, static), ignores property.
     *
     * @param object Object to set value in.
     * @param value  Value to set.
     */
    public void setValue(Object object, Object value) {
        if (!isWritable()) {
            return;
        }
        try {
            setValueHandle.invoke(object, value);
        } catch (Throwable e) {
            throw new JsonbException("Error setting value on: " + object, e);
        }
    }

    /**
     * Property is writable. Based on access policy and java field modifiers.
     *
     * @return true if can be deserialized from JSON
     */
    public boolean isWritable() {
        return !customization.isWriteTransient() && null != this.setValueHandle;
    }

    /**
     * If customized by JsonbPropertyAnnotation, than is used, otherwise use strategy to translate.
     * Since this is cached for performance reasons strategy has to be consistent
     * with calculated values for same input.
     */
    private static String calculateReadWriteName(String readWriteName, String propertyName, PropertyNamingStrategy strategy) {
        return null != readWriteName ? readWriteName : strategy.translateName(propertyName);
    }

    private static void introspectDateFormatter(Property property, AnnotationIntrospector introspector, PropertyCustomizationBuilder builder, JsonbRuntimeContext jsonbContext) {
        /*
         * If @JsonbDateFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbDateFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbDateFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbDateFormatter> jsonDateFormatCategorized = introspector.getJsonbDateFormatCategorized(property);
        final JsonbDateFormatter configDateFormatter = jsonbContext.getConfigProperties().getConfigDateFormatter();
        if (!builder.isReadTransient()) {
            final JsonbDateFormatter dateFormatter = getTargetForMostPreciseScope(jsonDateFormatCategorized, AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);
            builder.setSerializeDateFormatter(null != dateFormatter ? dateFormatter : configDateFormatter);
        }
        if (!builder.isWriteTransient()) {
            final JsonbDateFormatter dateFormatter = getTargetForMostPreciseScope(jsonDateFormatCategorized, AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);
            builder.setDeserializeDateFormatter(null != dateFormatter ? dateFormatter : configDateFormatter);
        }
    }

    private SerializerBindingEntry<?> getUserSerializerBinding(Property property, JsonbRuntimeContext jsonbContext) {
        final SerializerBindingEntry<?> serializerBinding = jsonbContext.getAnnotationIntrospector().getSerializerBinding(property);
        if (null != serializerBinding) {
            return serializerBinding;
        }
        return jsonbContext.getComponentMatcher().getSerializerBinding(getPropertySerializationType(), null).orElse(null);
    }

    private static boolean isMethodVisible(Method method, PropertyVisibilityStrategy strategy) {
        if (null == method || Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        boolean accessible = isVisible(strat -> strat.isVisible(method), method, strategy);
        //overridden by strategy, anonymous class, or lambda
        if (accessible && (!Modifier.isPublic(method.getModifiers()) || method.getDeclaringClass().isAnonymousClass() || method.getDeclaringClass().isSynthetic())) {
            overrideAccessible(method);
        }
        return accessible;
    }

    private static boolean isFieldVisible(Field field, Method method, PropertyVisibilityStrategy strategy) {
        if (null == field) {
            return false;
        }
        boolean accessible = isVisible(strat -> strat.isVisible(field), method, strategy);
        //overridden by strategy, or anonymous class (readable by spec)
        if (accessible && (!Modifier.isPublic(field.getModifiers()) || field.getDeclaringClass().isAnonymousClass() || isNotPublicAndNonNested(field.getDeclaringClass()))) {
            overrideAccessible(field);
        }
        return accessible;
    }

    // Used in ClassParser
    public static boolean isPropertyReadable(Field field, Method getter, PropertyVisibilityStrategy strategy) {
        return null != createReadHandle(field, getter, isMethodVisible(getter, strategy), strategy);
    }

    /**
     * Creates an instance.
     *
     * @param classModel   Class model of declaring class.
     * @param property     Property.
     * @param jsonbContext Context.
     */
    public PropertyModel(ClassModel classModel, Property property, JsonbRuntimeContext jsonbContext) {
        this.classModel = classModel;
        this.property = property;
        this.propertyName = property.getName();
        this.propertyType = property.getPropertyType();
        this.field = property.getField();
        this.getter = property.getGetter();
        this.setter = property.getSetter();
        PropertyVisibilityStrategy strategy = classModel.getClassCustomization().getPropertyVisibilityStrategy();
        boolean getterVisible = isMethodVisible(getter, strategy);
        boolean setterVisible = isMethodVisible(setter, strategy);
        this.getValueHandle = createReadHandle(field, getter, getterVisible, strategy);
        this.setValueHandle = createWriteHandle(field, setter, setterVisible, strategy);
        this.getterMethodType = getterVisible ? property.getGetterType() : null;
        this.setterMethodType = setterVisible ? property.getSetterType() : null;
        this.customization = introspectCustomization(property, jsonbContext);
        this.readName = calculateReadWriteName(customization.getJsonReadName(), propertyName, jsonbContext.getConfigProperties().getPropertyNamingStrategy());
        this.writeName = calculateReadWriteName(customization.getJsonWriteName(), propertyName, jsonbContext.getConfigProperties().getPropertyNamingStrategy());
        this.propertySerializer = resolveCachedSerializer();
    }

    /**
     * Introspected customization of a property.
     *
     * @return immutable property customization
     */
    public PropertyCustomization getCustomization() {
        return customization;
    }

    /**
     * Default property name according to Field / Getter / Setter method names.
     * This name is use for identifying properties, for JSON serialization is used customized name
     * which may be derived from default name.
     *
     * @return default name
     */
    public String getPropertyName() {
        return propertyName;
    }

    private static boolean isNotPublicAndNonNested(Class<?> declaringClass) {
        return !declaringClass.isMemberClass() && !Modifier.isPublic(declaringClass.getModifiers());
    }

    /**
     * Gets property's value.
     *
     * @param object object to read property from
     * @return property's value
     */
    public Object getValue(Object object) {
        try {
            return getValueHandle.invoke(object);
        } catch (Throwable e) {
            throw new JsonbException("Error getting value on: " + object, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (null == o || o.getClass() != getClass()) {
            return false;
        }
        PropertyModel other = (PropertyModel) o;
        return Objects.equals(readName, other.readName) && Objects.equals(writeName, other.writeName);
    }

    /**
     * Returns which type should be used to deserialization.
     *
     * @return deserialization type
     */
    public Type getPropertyDeserializationType() {
        return null == setterMethodType ? propertyType : setterMethodType;
    }

    @Override
    public int compareTo(PropertyModel o) {
        int compare = readName.compareTo(o.readName);
        return 0 == compare ? writeName.compareTo(o.writeName) : compare;
    }

    /**
     * Create a new PropertyModel that merges two existing PropertyModel that have identical read/write names.
     * The input PropertyModel objects MUST be equal (a.equals(b) == true)
     * @param a a PropertyModel instance to merge
     * @param b the other PropertyModel instance to merge
     */
    public PropertyModel(PropertyModel a, PropertyModel b) {
        if (!a.equals(b)) {
            throw new IllegalStateException("Property models " + a + " and " + b + " cannot be merged");
        }
        // Initial cloning steps
        this.classModel = a.classModel;
        this.propertyName = a.propertyName;
        this.readName = a.readName;
        this.writeName = a.writeName;
        this.propertyType = a.propertyType;
        this.customization = a.customization;
        // Merging steps
        this.getterMethodType = null != a.getterMethodType ? a.getterMethodType : b.getterMethodType;
        this.setterMethodType = null != a.setterMethodType ? a.setterMethodType : b.setterMethodType;
        this.property = a.property;
        if (null != b.property.getField()) {
            this.property.setField(b.property.getField());
        }
        if (null != b.property.getGetter()) {
            this.property.setGetter(b.property.getGetter());
        }
        if (null != b.property.getSetter()) {
            this.property.setSetter(b.property.getSetter());
        }
        this.field = property.getField();
        this.getter = property.getGetter();
        this.setter = property.getSetter();
        PropertyVisibilityStrategy strategy = classModel.getClassCustomization().getPropertyVisibilityStrategy();
        this.getValueHandle = createReadHandle(field, getter, isMethodVisible(getter, strategy), strategy);
        this.setValueHandle = createWriteHandle(field, setter, isMethodVisible(setter, strategy), strategy);
        this.propertySerializer = resolveCachedSerializer();
    }

}

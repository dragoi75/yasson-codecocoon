/*******************************************************************************
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/
package org.eclipse.yasson.internal.model;

import org.eclipse.yasson.internal.AnnotationIntrospector;
import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.ReflectiveTypeResolver;
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

import javax.json.bind.config.PropertyNamingStrategy;
import javax.json.bind.serializer.JsonbSerializer;
import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A model for class property.
 * Property is JavaBean alike meta information field / getter / setter of a property in class.
 *
 * @author Dmitry Kornilov
 * @author Roman Grigoriadi
 */
public class PropertyDescriptor implements Comparable<PropertyDescriptor> {

    /**
     * Field propertyName as in class by java bean convention.
     */
    private final String fieldName;

    /**
     * Calculated name to be used when reading json document.
     */
    private final String getterIdentifier;

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
    private final ClassDescriptor classDescriptor;

    /**
     * Customization of this property.
     */
    final private PropertyCustomization customOptions;

    private final PropertyValuePropagation valueFlow;

    private final JsonbSerializer<?> valueSerializer;

    private final AccessMethodType readAccessType;

    private final AccessMethodType writeAccessType;

    /**
     * Creates an instance.
     *
     * @param classDescriptor Class model of declaring class.
     * @param propInfo Property.
     * @param jsonContext Context.
     */
    public PropertyDescriptor(ClassDescriptor classDescriptor, Property propInfo, JsonbContext jsonContext) {
        this.classDescriptor = classDescriptor;
        this.fieldName = propInfo.getName();
        this.valueType = propInfo.getPropertyType();
        this.valueFlow = new ReflectionPropagation(propInfo, classDescriptor.getClassCustomization().getPropertyVisibilityStrategy());
        this.readAccessType = valueFlow.isGetterVisible() ? new AccessMethodType(propInfo.getGetterType()) : null;
        this.writeAccessType = valueFlow.isSetterVisible() ? new AccessMethodType(propInfo.getSetterType()) : null;
        this.customOptions = inspectCustomization(propInfo, jsonContext);
        this.getterIdentifier = computeReadWriteName(customOptions.getJsonReadName(), jsonContext.getConfigProperties().getPropertyNamingStrategy());
        this.setterName = computeReadWriteName(customOptions.getJsonWriteName(), jsonContext.getConfigProperties().getPropertyNamingStrategy());
        this.valueSerializer = resolveSerializer();
    }


    /**
     * Try to cache serializer for this bean property. Only if type cannot be changed during runtime.
     *
     * @return serializer instance to be cached
     */
    @SuppressWarnings("unchecked")
    private JsonbSerializer<?> resolveSerializer() {
        Type targetType = getPropertySerializationType();
        if (!ReflectiveTypeResolver.isResolvedType(targetType)) {
            return null;
        }
        if (customOptions.getAdapterBinding() != null) {
            return new AdaptedObjectSerializer<>(classDescriptor, customOptions.getAdapterBinding());
        }
        if (customOptions.getSerializerBinding() != null) {
            return new UserSerializerSerializer<>(classDescriptor, customOptions.getSerializerBinding().getJsonbSerializer());
        }

        final Class<?> rawType = ReflectiveTypeResolver.getRawType(targetType);
        final Optional<SerializerProviderWrapper> serializerProviderOpt = DefaultSerializers.getInstance().findValueSerializerProvider(rawType);
        if (serializerProviderOpt.isPresent()) {
            return serializerProviderOpt.get().getSerializerProvider().provideSerializer(customOptions);
        }

        return null;
    }

    /**
     * Returns which type should be used to deserialization
     *
     * @return deserialization type
     */
    public Type getPropertyDeserializationType() {
        return writeAccessType == null ? valueType : writeAccessType.getMethodType();
    }

    /**
     * Returns which type should be used to serialization
     *
     * @return serialization type
     */
    public Type getPropertySerializationType() {
        return readAccessType == null ? valueType : readAccessType.getMethodType();
    }

    private AdapterBinding getUserAdapterBinding(Property propInfo, JsonbContext jsonContext) {
        final AdapterBinding adapterRef = jsonContext.getAnnotationIntrospector().getAdapterBinding(propInfo);
        if (adapterRef != null) {
            return adapterRef;
        }
        return jsonContext.getComponentMatcher().getAdapterBinding(valueType, null).orElse(null);
    }

    private SerializerBinding<?> getUserSerializerBinding(Property propInfo, JsonbContext jsonContext) {
        final SerializerBinding serializerRef = jsonContext.getAnnotationIntrospector().getSerializerBinding(propInfo);
        if (serializerRef != null) {
            return serializerRef;
        }
        return jsonContext.getComponentMatcher().getSerializerBinding(getPropertySerializationType(), null).orElse(null);
    }

    private PropertyCustomization inspectCustomization(Property propInfo, JsonbContext jsonContext) {
        final AnnotationIntrospector annotationReader = jsonContext.getAnnotationIntrospector();
        final PropertyCustomizationBuilder customizationCreator = new PropertyCustomizationBuilder();
        //drop all other annotations for transient properties
        EnumSet<AnnotationTarget> ignoredTargets = annotationReader.getJsonbTransientCategorized(propInfo);
        if (ignoredTargets.size() != 0) {
            customizationCreator.setReadTransient(ignoredTargets.contains(AnnotationTarget.GETTER));
            customizationCreator.setWriteTransient(ignoredTargets.contains(AnnotationTarget.SETTER));

            if (ignoredTargets.contains(AnnotationTarget.PROPERTY)) {
                if(!ignoredTargets.contains(AnnotationTarget.GETTER)){
                    customizationCreator.setReadTransient(true);
                }
                if(!ignoredTargets.contains(AnnotationTarget.SETTER)){
                    customizationCreator.setWriteTransient(true);
                }
            }

            if (customizationCreator.isReadTransient()) {
                annotationReader.checkTransientIncompatible(propInfo.getFieldElement());
                annotationReader.checkTransientIncompatible(propInfo.getGetterElement());
            }
            if (customizationCreator.isWriteTransient()) {
                annotationReader.checkTransientIncompatible(propInfo.getFieldElement());
                annotationReader.checkTransientIncompatible(propInfo.getSetterElement());
            }
        }

        if(!customizationCreator.isReadTransient()){
            customizationCreator.setJsonWriteName(annotationReader.getJsonbPropertyJsonWriteName(propInfo));
            customizationCreator.setNillable(annotationReader.isPropertyNillable(propInfo).orElse(classDescriptor.getClassCustomization().isNillable()));
            customizationCreator.setSerializerBinding(getUserSerializerBinding(propInfo, jsonContext));
        }

        if(!customizationCreator.isWriteTransient()){
            customizationCreator.setJsonReadName(annotationReader.getJsonbPropertyJsonReadName(propInfo));
            customizationCreator.setDeserializerBinding(annotationReader.getDeserializerBinding(propInfo));
        }

        customizationCreator.setAdapterInfo(getUserAdapterBinding(propInfo, jsonContext));

        determineDateFormatter(propInfo, annotationReader, customizationCreator, jsonContext);
        determineNumberFormatter(propInfo, annotationReader, customizationCreator);
        customizationCreator.setImplementationClass(annotationReader.getImplementationClass(propInfo));

        return customizationCreator.buildPropertyCustomization();
    }

    private void determineDateFormatter(Property propInfo, AnnotationIntrospector annotationReader, PropertyCustomizationBuilder customizationCreator, JsonbContext jsonContext) {
        /*
         * If @JsonbDateFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbDateFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbDateFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbDateFormatter> dateFormatByTarget = annotationReader.getJsonbDateFormatCategorized(propInfo);
        final JsonbDateFormatter configuredFormatter = jsonContext.getConfigProperties().getConfigDateFormatter();

        if(!customizationCreator.isReadTransient()){
            final JsonbDateFormatter resolvedFormatter = getTargetForMostPreciseScope(dateFormatByTarget,
                    AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);

            customizationCreator.setSerializeDateFormatter(resolvedFormatter != null ? resolvedFormatter : configuredFormatter);
        }

        if(!customizationCreator.isWriteTransient()){
            final JsonbDateFormatter resolvedFormatter = getTargetForMostPreciseScope(dateFormatByTarget,
                    AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);

            customizationCreator.setDeserializeDateFormatter(resolvedFormatter != null ? resolvedFormatter : configuredFormatter);
        }
    }

    private void determineNumberFormatter(Property propInfo, AnnotationIntrospector annotationReader, PropertyCustomizationBuilder customizationCreator) {
        /*
         * If @JsonbNumberFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbNumberFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbNumberFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbNumberFormatter> numberFormatByTarget = annotationReader.getJsonNumberFormatter(propInfo);


        if(!customizationCreator.isReadTransient()){
            customizationCreator.setSerializeNumberFormatter(getTargetForMostPreciseScope(numberFormatByTarget,
                    AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS));
        }

        if(!customizationCreator.isWriteTransient()){
            customizationCreator.setDeserializeNumberFormatter(getTargetForMostPreciseScope(numberFormatByTarget,
                            AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS));
        }
    }

    /**
     * Pull result for most significant scope defined by order of annotation targets.
     *
     * @param annotationsByTarget all targets
     * @param annotationArray ordered target types by scope
     */
    private <T> T getTargetForMostPreciseScope(Map<AnnotationTarget, T> annotationsByTarget, AnnotationTarget... annotationArray) {
        for (AnnotationTarget annotationKind : annotationArray) {
            final T computedValue = annotationsByTarget.get(annotationKind);
            if (computedValue != null) {
                return computedValue;
            }
        }
        return null;
    }

    /**
     * Gets property's value.
     *
     * @param receiver object to read property from
     * @return property's value
     */
    public Object getValue(Object receiver) {
        return valueFlow.getValue(receiver);
    }

    /**
     * Sets a property.
     *
     * If not writable (final, transient, static), ignores property.
     *
     * @param receiver Object to set value in.
     * @param newVal  Value to set.
     */
    public void setValue(Object receiver, Object newVal) {
        if (!isWritable()) {
            return;
        }
        valueFlow.setValue(receiver, newVal);
    }

    /**
     * Property is readable. Based on access policy and java field modifiers.
     * @return true if can be serialized to JSON
     */
    public boolean isReadable() {
        return !customOptions.isReadTransient() && valueFlow.isReadable();
    }

    /**
     * Property is writable. Based on access policy and java field modifiers.
     * @return true if can be deserialized from JSON
     */
    public boolean isWritable() {
        return !customOptions.isWriteTransient() && valueFlow.isWritable();
    }

    /**
     * Default property name according to Field / Getter / Setter method names.
     * This name is use for identifying properties, for JSON serialization is used customized name
     * which may be derived from default name.
     * @return default name
     */
    public String getPropertyName() {
        return fieldName;
    }

    /**
     * Runtime type of a property. May be a TypeVariable or WildcardType.
     *
     * @return type of a property
     */
    public Type getPropertyType() {
        return valueType;
    }

    /**
     * Model of declaring class of this property.
     * @return class model
     */
    public ClassDescriptor getClassModel() {
        return classDescriptor;
    }

    /**
     * Introspected customization of a property.
     * @return immutable property customization
     */
    public PropertyCustomization getCustomization() {
        return customOptions;
    }

    @Override
    public int compareTo(PropertyDescriptor otherDescriptor) {
        return fieldName.compareTo(otherDescriptor.getPropertyName());
    }

    @Override
    public boolean equals(Object otherPropDesc) {
        if (this == otherPropDesc) return true;
        if (otherPropDesc == null || getClass() != otherPropDesc.getClass()) return false;
        PropertyDescriptor otherDescriptor = (PropertyDescriptor) otherPropDesc;
        return Objects.equals(fieldName, otherDescriptor.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName);
    }

    /**
     * Gets a name of JSON document property to read this property from.
     *
     * @return Name of JSON document property.
     */
    public String getReadName() {
        return getterIdentifier;
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
    private String computeReadWriteName(String accessorName, PropertyNamingStrategy namingPolicy) {
        return accessorName != null ? accessorName : namingPolicy.translateName(fieldName);
    }

    /**
     * Wrapper object of {@code java.lang.reflect} representations of this javabean property.
     *
     * @return Property model
     */
    public PropertyValuePropagation getPropagation() {
        return valueFlow;
    }
}

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

import org.eclipse.yasson.internal.AnnotationIntrospector;
import org.eclipse.yasson.internal.JsonbContext;
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
public class BeanPropertyDescriptor implements Comparable<BeanPropertyDescriptor> {

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
    private final ClassDescriptor beanTypeDescriptor;

    /**
     * Customization of this property.
     */
    final private PropertyCustomization propConfig;

    private final PropertyValuePropagation valueFlow;

    private final JsonbSerializer<?> valueSerializer;

    private final AccessMethodType getterAccessType;

    private final AccessMethodType setterAccessType;

    /**
     * Creates an instance.
     *
     * @param beanTypeDescriptor Class model of declaring class.
     * @param prop Property.
     * @param jsonbEnv Context.
     */
    public BeanPropertyDescriptor(ClassDescriptor beanTypeDescriptor, Property prop, JsonbContext jsonbEnv) {
        this.beanTypeDescriptor = beanTypeDescriptor;
        this.fieldName = prop.getName();
        this.valueType = prop.getPropertyType();
        this.valueFlow = new ReflectionPropagation(prop, beanTypeDescriptor.getClassCustomization().getPropertyVisibilityStrategy());
        this.getterAccessType = valueFlow.isGetterVisible() ? new AccessMethodType(prop.getGetterType()) : null;
        this.setterAccessType = valueFlow.isSetterVisible() ? new AccessMethodType(prop.getSetterType()) : null;
        this.propConfig = inspectCustomization(prop, jsonbEnv);
        this.getterName = computeReadWriteName(propConfig.getJsonReadName(), jsonbEnv.getConfigProperties().getPropertyNamingStrategy());
        this.setterName = computeReadWriteName(propConfig.getJsonWriteName(), jsonbEnv.getConfigProperties().getPropertyNamingStrategy());
        this.valueSerializer = resolveSerializer();
    }

    /**
     * Try to cache serializer for this bean property. Only if type cannot be changed during runtime.
     *
     * @return serializer instance to be cached
     */
    @SuppressWarnings("unchecked")
    private JsonbSerializer<?> resolveSerializer() {
        Type serialType = getPropertySerializationType();
        if (!ReflectionUtils.isResolvedType(serialType)) {
            return null;
        }
        if (null != propConfig.getAdapterBinding()) {
            return new AdaptedObjectSerializer<>(beanTypeDescriptor, propConfig.getAdapterBinding());
        }
        if (null != propConfig.getSerializerBinding()) {
            return new UserSerializerSerializer<>(beanTypeDescriptor, propConfig.getSerializerBinding().getJsonbSerializer());
        }
        final Class<?> rawType = ReflectionUtils.getRawType(serialType);
        final Optional<SerializerProviderWrapper> serializerProviderOpt = DefaultSerializers.getInstance().findValueSerializerProvider(rawType);
        if (serializerProviderOpt.isPresent()) {
            return serializerProviderOpt.get().getSerializerProvider().provideSerializer(propConfig);
        }
        return null;
    }

    /**
     * Returns which type should be used to deserialization
     *
     * @return deserialization type
     */
    public Type getPropertyDeserializationType() {
        return null == setterAccessType ? valueType : setterAccessType.getMethodType();
    }

    /**
     * Returns which type should be used to serialization
     *
     * @return serialization type
     */
    public Type getPropertySerializationType() {
        return null == getterAccessType ? valueType : getterAccessType.getMethodType();
    }

    private AdapterBinding getUserAdapterBinding(Property prop, JsonbContext jsonbEnv) {
        final AdapterBinding adapterMapping = jsonbEnv.getAnnotationIntrospector().getAdapterBinding(prop);
        if (null != adapterMapping) {
            return adapterMapping;
        }
        return jsonbEnv.getComponentMatcher().getAdapterBinding(valueType, null).orElse(null);
    }

    private SerializerBinding<?> getUserSerializerBinding(Property prop, JsonbContext jsonbEnv) {
        final SerializerBinding serializerLink = jsonbEnv.getAnnotationIntrospector().getSerializerBinding(prop);
        if (null != serializerLink) {
            return serializerLink;
        }
        return jsonbEnv.getComponentMatcher().getSerializerBinding(getPropertySerializationType(), null).orElse(null);
    }

    private PropertyCustomization inspectCustomization(Property prop, JsonbContext jsonbEnv) {
        final AnnotationIntrospector annotationScanner = jsonbEnv.getAnnotationIntrospector();
        final PropertyCustomizationBuilder customizationFactory = new PropertyCustomizationBuilder();
        //drop all other annotations for transient properties
        EnumSet<AnnotationTarget> transientTargets = annotationScanner.getJsonbTransientCategorized(prop);
        if (0 != transientTargets.size()) {
            customizationFactory.setReadTransient(transientTargets.contains(AnnotationTarget.GETTER));
            customizationFactory.setWriteTransient(transientTargets.contains(AnnotationTarget.SETTER));
            if (transientTargets.contains(AnnotationTarget.PROPERTY)) {
                if (!transientTargets.contains(AnnotationTarget.GETTER)) {
                    customizationFactory.setReadTransient(true);
                }
                if (!transientTargets.contains(AnnotationTarget.SETTER)) {
                    customizationFactory.setWriteTransient(true);
                }
            }
            if (customizationFactory.isReadTransient()) {
                annotationScanner.checkTransientIncompatible(prop.getFieldElement());
                annotationScanner.checkTransientIncompatible(prop.getGetterElement());
            }
            if (customizationFactory.isWriteTransient()) {
                annotationScanner.checkTransientIncompatible(prop.getFieldElement());
                annotationScanner.checkTransientIncompatible(prop.getSetterElement());
            }
        }
        if (!customizationFactory.isReadTransient()) {
            customizationFactory.setJsonWriteName(annotationScanner.getJsonbPropertyJsonWriteName(prop));
            customizationFactory.setNillable(annotationScanner.isPropertyNillable(prop).orElse(beanTypeDescriptor.getClassCustomization().isNillable()));
            customizationFactory.setSerializerBinding(getUserSerializerBinding(prop, jsonbEnv));
        }
        if (!customizationFactory.isWriteTransient()) {
            customizationFactory.setJsonReadName(annotationScanner.getJsonbPropertyJsonReadName(prop));
            customizationFactory.setDeserializerBinding(annotationScanner.getDeserializerBinding(prop));
        }
        customizationFactory.setAdapterInfo(getUserAdapterBinding(prop, jsonbEnv));
        determineDateFormatter(prop, annotationScanner, customizationFactory, jsonbEnv);
        determineNumberFormatter(prop, annotationScanner, customizationFactory);
        customizationFactory.setImplementationClass(annotationScanner.getImplementationClass(prop));
        return customizationFactory.buildPropertyCustomization();
    }

    private void determineDateFormatter(Property prop, AnnotationIntrospector annotationScanner, PropertyCustomizationBuilder customizationFactory, JsonbContext jsonbEnv) {
        /*
         * If @JsonbDateFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbDateFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbDateFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbDateFormatter> dateFormatByTarget = annotationScanner.getJsonbDateFormatCategorized(prop);
        final JsonbDateFormatter configFormatter = jsonbEnv.getConfigProperties().getConfigDateFormatter();
        if (!customizationFactory.isReadTransient()) {
            final JsonbDateFormatter resolvedDateFormatter = getTargetForMostPreciseScope(dateFormatByTarget, AnnotationTarget.GETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);
            customizationFactory.setSerializeDateFormatter(null != resolvedDateFormatter ? resolvedDateFormatter : configFormatter);
        }
        if (!customizationFactory.isWriteTransient()) {
            final JsonbDateFormatter resolvedDateFormatter = getTargetForMostPreciseScope(dateFormatByTarget, AnnotationTarget.SETTER, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS);
            customizationFactory.setDeserializeDateFormatter(null != resolvedDateFormatter ? resolvedDateFormatter : configFormatter);
        }
    }

    private void determineNumberFormatter(Property prop, AnnotationIntrospector annotationScanner, PropertyCustomizationBuilder customizationFactory) {
        /*
         * If @JsonbNumberFormat is placed on getter implementation must use this format on serialization.
         * If @JsonbNumberFormat is placed on setter implementation must use this format on deserialization.
         * If @JsonbNumberFormat is placed on field implementation must use this format on serialization and deserialization.
         *
         * Priority from high to low is getter / setter > field > class > package > global configuration
         */
        Map<AnnotationTarget, JsonbNumberFormatter> numberFormatByTarget = annotationScanner.getJsonNumberFormatter(prop);
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
     * @param annotationsByTarget all targets
     * @param annotationScopes ordered target types by scope
     */
    private <T> T getTargetForMostPreciseScope(Map<AnnotationTarget, T> annotationsByTarget, AnnotationTarget... annotationScopes) {
        for (AnnotationTarget annotationScope : annotationScopes) {
            final T foundValue = annotationsByTarget.get(annotationScope);
            if (null != foundValue) {
                return foundValue;
            }
        }
        return null;
    }

    /**
     * Gets property's value.
     *
     * @param beanInstance object to read property from
     * @return property's value
     */
    public Object getValue(Object beanInstance) {
        return valueFlow.getValue(beanInstance);
    }

    /**
     * Sets a property.
     *
     * If not writable (final, transient, static), ignores property.
     *
     * @param beanInstance Object to set value in.
     * @param newVal  Value to set.
     */
    public void setValue(Object beanInstance, Object newVal) {
        if (!isWritable()) {
            return;
        }
        valueFlow.setValue(beanInstance, newVal);
    }

    /**
     * Property is readable. Based on access policy and java field modifiers.
     * @return true if can be serialized to JSON
     */
    public boolean isReadable() {
        return !propConfig.isReadTransient() && valueFlow.isReadable();
    }

    /**
     * Property is writable. Based on access policy and java field modifiers.
     * @return true if can be deserialized from JSON
     */
    public boolean isWritable() {
        return !propConfig.isWriteTransient() && valueFlow.isWritable();
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
        return beanTypeDescriptor;
    }

    /**
     * Introspected customization of a property.
     * @return immutable property customization
     */
    public PropertyCustomization getCustomization() {
        return propConfig;
    }

    @Override
    public int compareTo(BeanPropertyDescriptor otherDescriptor) {
        return fieldName.compareTo(otherDescriptor.getPropertyName());
    }

    @Override
    public boolean equals(Object otherPropertyDescriptor) {
        if (otherPropertyDescriptor == this)
            return true;
        if (null == otherPropertyDescriptor || otherPropertyDescriptor.getClass() != getClass())
            return false;
        BeanPropertyDescriptor otherDescriptor = (BeanPropertyDescriptor) otherPropertyDescriptor;
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
    private String computeReadWriteName(String accessorName, PropertyNamingStrategy namer) {
        return null != accessorName ? accessorName : namer.translateName(fieldName);
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

/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.yasson.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.annotation.JsonbTypeSerializer;
import jakarta.json.bind.annotation.JsonbVisibility;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.eclipse.yasson.ImplementationClass;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;
import org.eclipse.yasson.internal.model.AnnotationTarget;
import org.eclipse.yasson.internal.model.CreatorProfile;
import org.eclipse.yasson.internal.model.JsonbAnnotatedMember;
import org.eclipse.yasson.internal.model.JsonbInstantiator;
import org.eclipse.yasson.internal.model.PropertyDescriptor;
import org.eclipse.yasson.internal.model.customization.ClassConfiguration;
import org.eclipse.yasson.internal.model.customization.ClassCustomizationBuilder;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.serializer.StandardSerializerRegistry;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * Introspects configuration on classes and their properties by reading annotations.
 */
public class JsonbAnnotationIntrospector {

    private final JsonbContextManager contextManager;

    private final ConstructorPropertiesAnnotationIntrospector constructorIntrospector;

    /**
     * Annotations to report exception when used in combination with {@link JsonbTransient}.
     */
    public static final List<Class<? extends Annotation>> TRANSIENT_INCOMPATIBLE = Arrays.asList(JsonbDateFormat.class, JsonbNumberFormat.class, JsonbProperty.class, JsonbTypeAdapter.class, JsonbTypeSerializer.class, JsonbTypeDeserializer.class);

    /**
     * Creates annotation introspecting component passing {@link JsonbContextManager} inside.
     *
     * @param contextManager mandatory
     */
    public JsonbAnnotationIntrospector(JsonbContextManager contextManager) {
        Objects.requireNonNull(contextManager);
        this.contextManager = contextManager;
        this.constructorIntrospector = ConstructorPropertiesAnnotationIntrospector.forContext(contextManager);
    }

    /**
     * Gets a name of property for JSON marshalling.
     * Can be different writeName for same property.
     *
     * @param propDescriptor property representation - field, getter, setter (not null)
     * @return read name
     */
    public String getJsonbPropertyJsonWriteName(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        return getJsonbPropertyCustomizedName(propDescriptor, propDescriptor.getGetterElement());
    }

    /**
     * Gets a name of property for JSON unmarshalling.
     * Can be different from writeName for same property.
     *
     * @param propDescriptor property representation - field, getter, setter (not null)
     * @return write name
     */
    public String getJsonbPropertyJsonReadName(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        return getJsonbPropertyCustomizedName(propDescriptor, propDescriptor.getSetterElement());
    }

    private String getJsonbPropertyCustomizedName(PropertyDescriptor propDescriptor, JsonbAnnotatedMember<Method> annotatedMethodMember) {
        JsonbProperty jsonbProperty = getMethodAnnotation(JsonbProperty.class, annotatedMethodMember);
        if (null != jsonbProperty && !jsonbProperty.value().isEmpty()) {
            return jsonbProperty.value();
        }
        //in case of property name getter/setter override field value
        JsonbProperty jsonbFieldProperty = getFieldAnnotation(JsonbProperty.class, propDescriptor.getFieldElement());
        if (null != jsonbFieldProperty && !jsonbFieldProperty.value().isEmpty()) {
            return jsonbFieldProperty.value();
        }
        return null;
    }

    /**
     * Searches for JsonbCreator annotation on constructors and static methods.
     *
     * @param targetClass class to search
     * @return JsonbCreator metadata object
     */
    public JsonbInstantiator getCreator(Class<?> targetClass) {
        JsonbInstantiator instantiator = null;
        Constructor<?>[] constructors = AccessController.doPrivileged((PrivilegedAction<Constructor<?>[]>) targetClass::getDeclaredConstructors);
        for (Constructor<?> ctor : constructors) {
            final jakarta.json.bind.annotation.JsonbCreator jsonbCreatorAnnotation = locateAnnotation(ctor.getDeclaredAnnotations(), jakarta.json.bind.annotation.JsonbCreator.class);
            if (null != jsonbCreatorAnnotation) {
                instantiator = buildJsonbCreator(ctor, instantiator, targetClass);
            }
        }
        Method[] methods = AccessController.doPrivileged((PrivilegedAction<Method[]>) targetClass::getDeclaredMethods);
        for (Method candidateMethod : methods) {
            final jakarta.json.bind.annotation.JsonbCreator jsonbCreatorAnnotation = locateAnnotation(candidateMethod.getDeclaredAnnotations(), jakarta.json.bind.annotation.JsonbCreator.class);
            if (null != jsonbCreatorAnnotation && Modifier.isStatic(candidateMethod.getModifiers())) {
                if (!targetClass.equals(candidateMethod.getReturnType())) {
                    throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.INCOMPATIBLE_FACTORY_CREATOR_RETURN_TYPE, candidateMethod, targetClass));
                }
                instantiator = buildJsonbCreator(candidateMethod, instantiator, targetClass);
            }
        }
        if (null == instantiator) {
            instantiator = constructorIntrospector.getCreator(constructors);
        }
        return instantiator;
    }

    private JsonbInstantiator buildJsonbCreator(Executable executableMember, JsonbInstantiator currentInstantiator, Class<?> targetClass) {
        if (null != currentInstantiator) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.MULTIPLE_JSONB_CREATORS, targetClass));
        }
        final Parameter[] execParameters = executableMember.getParameters();
        CreatorProfile[] creatorProfiles = new CreatorProfile[execParameters.length];
        int idx = 0;
        while (execParameters.length > idx) {
            final Parameter ctorParameter = execParameters[idx];
            final JsonbProperty propAnnotation = ctorParameter.getAnnotation(JsonbProperty.class);
            if (null == propAnnotation || propAnnotation.value().isEmpty()) {
                creatorProfiles[idx] = new CreatorProfile(ctorParameter.getName(), ctorParameter, contextManager);
            } else {
                creatorProfiles[idx] = new CreatorProfile(propAnnotation.value(), ctorParameter, contextManager);
            }
            idx += 1;
        }
        return new JsonbInstantiator(executableMember, creatorProfiles);
    }

    /**
     * Checks for {@link JsonbAdapter} on a property.
     *
     * @param propDescriptor property not null
     * @return components info
     */
    public AdapterBinding getAdapterBinding(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        JsonbTypeAdapter typeAdapterAnnot = getAnnotationFromProperty(JsonbTypeAdapter.class, propDescriptor).orElseGet(() -> getAnnotationFromPropertyType(propDescriptor, JsonbTypeAdapter.class));
        if (null == typeAdapterAnnot) {
            return null;
        }
        return getAdapterBindingFromAnnotation(typeAdapterAnnot, ReflectionUtils.getOptionalRawType(propDescriptor.getPropertyType()));
    }

    /**
     * Checks for {@link JsonbAdapter} on a type.
     *
     * @param classElement type not null
     * @return components info
     */
    public AdapterBinding getAdapterBinding(JsonbAnnotatedMember<Class<?>> classElement) {
        Objects.requireNonNull(classElement);
        JsonbTypeAdapter typeAdapterAnnot = classElement.getElement().getAnnotation(JsonbTypeAdapter.class);
        if (null == typeAdapterAnnot) {
            return null;
        }
        return getAdapterBindingFromAnnotation(typeAdapterAnnot, Optional.ofNullable(classElement.getElement()));
    }

    private AdapterBinding getAdapterBindingFromAnnotation(JsonbTypeAdapter typeAdapterAnnot, Optional<Class<?>> expectedType) {
        final Class<? extends JsonbAdapter> adapterImplClass = typeAdapterAnnot.value();
        final AdapterBinding binding = contextManager.getComponentMatcher().introspectAdapterBinding(adapterImplClass, null);
        if (expectedType.isPresent() && !(ReflectionUtils.getRawType(binding.getBindingType()).isAssignableFrom(expectedType.get()))) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.ADAPTER_INCOMPATIBLE, binding.getBindingType(), expectedType.get()));
        }
        return binding;
    }

    /**
     * Checks for {@link JsonbDeserializer} on a property.
     *
     * @param propDescriptor property not null
     * @return components info
     */
    public DeserializerBinding getDeserializerBinding(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        JsonbTypeDeserializer typeDeserializerAnnot = getAnnotationFromProperty(JsonbTypeDeserializer.class, propDescriptor).orElseGet(() -> getAnnotationFromPropertyType(propDescriptor, JsonbTypeDeserializer.class));
        if (null == typeDeserializerAnnot) {
            return null;
        }
        final Class<? extends JsonbDeserializer> deserializerType = typeDeserializerAnnot.value();
        return contextManager.getComponentMatcher().introspectDeserializerBinding(deserializerType, null);
    }

    /**
     * Checks for {@link JsonbDeserializer} on a type.
     *
     * @param classElement type not null
     * @return components info
     */
    public DeserializerBinding getDeserializerBinding(JsonbAnnotatedMember<Class<?>> classElement) {
        Objects.requireNonNull(classElement);
        JsonbTypeDeserializer typeDeserializerAnnot = classElement.getElement().getAnnotation(JsonbTypeDeserializer.class);
        if (null == typeDeserializerAnnot) {
            return null;
        }
        final Class<? extends JsonbDeserializer> deserializerType = typeDeserializerAnnot.value();
        return contextManager.getComponentMatcher().introspectDeserializerBinding(deserializerType, null);
    }

    /**
     * Checks for {@link JsonbSerializer} on a property.
     *
     * @param propDescriptor property not null
     * @return components info
     */
    public SerializerBinding getSerializerBinding(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        JsonbTypeSerializer typeSerializer = getAnnotationFromProperty(JsonbTypeSerializer.class, propDescriptor).orElseGet(() -> getAnnotationFromPropertyType(propDescriptor, JsonbTypeSerializer.class));
        if (null == typeSerializer) {
            return null;
        }
        final Class<? extends JsonbSerializer> serializerImplClass = typeSerializer.value();
        return contextManager.getComponentMatcher().introspectSerializerBinding(serializerImplClass, null);
    }

    /**
     * Checks for {@link JsonbSerializer} on a type.
     *
     * @param classElement type not null
     * @return components info
     */
    public SerializerBinding getSerializerBinding(JsonbAnnotatedMember<Class<?>> classElement) {
        Objects.requireNonNull(classElement);
        JsonbTypeSerializer typeSerializer = classElement.getElement().getAnnotation(JsonbTypeSerializer.class);
        if (null == typeSerializer) {
            return null;
        }
        final Class<? extends JsonbSerializer> serializerImplClass = typeSerializer.value();
        return contextManager.getComponentMatcher().introspectSerializerBinding(serializerImplClass, null);
    }

    private <T extends Annotation> T getAnnotationFromPropertyType(PropertyDescriptor propDescriptor, Class<T> annotationType) {
        final Optional<Class<?>> rawTypeOptional = ReflectionUtils.getOptionalRawType(propDescriptor.getPropertyType());
        if (!rawTypeOptional.isPresent()) {
            //will not work for type variable properties, which are bound to class that is annotated.
            return null;
        }
        return locateAnnotation(gatherAnnotations(rawTypeOptional.get()).getAnnotations(), annotationType);
    }

    /**
     * Checks if property is nillable.
     * Looks for {@link JsonbProperty} nillable attribute only.
     * JsonbNillable is checked only for ClassModels.
     *
     * @param propDescriptor property to search in, not null
     * @return True if property should be serialized when null.
     */
    public Optional<Boolean> isPropertyNillable(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        final Optional<JsonbProperty> jsonbProp = getAnnotationFromProperty(JsonbProperty.class, propDescriptor);
        return jsonbProp.map(JsonbProperty::nillable);
    }

    /**
     * Checks for JsonbNillable annotation on a class, its superclasses and interfaces.
     *
     * @param classAnnotatedMember class to search JsonbNillable in.
     * @return true if found
     */
    public boolean isClassNillable(JsonbAnnotatedMember<Class<?>> classAnnotatedMember) {
        final JsonbNillable nillableAnnotation = locateAnnotation(classAnnotatedMember.getAnnotations(), JsonbNillable.class);
        if (null != nillableAnnotation) {
            return nillableAnnotation.value();
        }
        Class<?> targetClass = classAnnotatedMember.getElement();
        if (Optional.class == targetClass || OptionalDouble.class == targetClass || OptionalInt.class == targetClass || OptionalLong.class == targetClass) {
            return true;
        }
        return contextManager.getConfigProperties().getConfigNullable();
    }

    /**
     * Checks for {@link JsonbPropertyOrder} annotation.
     *
     * @param classAnnotatedMember class to search on
     * @return ordered properties names or null if not found
     */
    public String[] getPropertyOrder(JsonbAnnotatedMember<Class<?>> classAnnotatedMember) {
        final JsonbPropertyOrder propertyOrderAnnotation = classAnnotatedMember.getElement().getAnnotation(JsonbPropertyOrder.class);
        return null != propertyOrderAnnotation ? propertyOrderAnnotation.value() : null;
    }

    /**
     * Checks if property is annotated transient. If JsonbTransient annotation is present on field getter or setter, and other
     * annotation is present
     * on either of it, JsonbException is thrown with message describing collision.
     *
     * @param propDescriptor The property to inspect if there is any {@link JsonbTransient} annotation defined for it
     * @return Set of {@link AnnotationTarget}s specifying in which scope the {@link JsonbTransient} is applied
     */
    public EnumSet<AnnotationTarget> getJsonbTransientCategorized(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        EnumSet<AnnotationTarget> transientTargetsSet = EnumSet.noneOf(AnnotationTarget.class);
        Map<AnnotationTarget, JsonbTransient> transientAnnotationsByTarget = getAnnotationFromPropertyCategorized(JsonbTransient.class, propDescriptor);
        if (0 < transientAnnotationsByTarget.size()) {
            transientTargetsSet.addAll(transientAnnotationsByTarget.keySet());
            return transientTargetsSet;
        }
        return transientTargetsSet;
    }

    /**
     * Search {@link JsonbDateFormat} on property, if not found looks at annotations declared on property type class.
     *
     * @param propDescriptor Property to search on.
     * @return Map of {@link JsonbDateFormatter} instances categorized by their scopes (class, property, getter or setter). If
     * there is no date
     * formatter specified for given property, an empty map would be returned
     */
    public Map<AnnotationTarget, JsonbDateFormatter> getJsonbDateFormatCategorized(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        Map<AnnotationTarget, JsonbDateFormatter> dateFormatterMap = new HashMap<>();
        Map<AnnotationTarget, JsonbDateFormat> transientAnnotationsByTarget = getAnnotationFromPropertyCategorized(JsonbDateFormat.class, propDescriptor);
        if (0 != transientAnnotationsByTarget.size()) {
            transientAnnotationsByTarget.forEach((mapKey, ann) -> dateFormatterMap.put(mapKey, buildJsonbDateFormatter(ann.value(), ann.locale(), propDescriptor)));
        }
        // No date format on property, try class level
        // if property is not TypeVariable and its class is not date skip it
        final Optional<Class<?>> rawTypeOpt = ReflectionUtils.getOptionalRawType(propDescriptor.getPropertyType());
        if (rawTypeOpt.isPresent()) {
            Class<?> elementType = rawTypeOpt.get();
            if (!(Date.class.isAssignableFrom(elementType) || Calendar.class.isAssignableFrom(elementType) || TemporalAccessor.class.isAssignableFrom(elementType))) {
                return new HashMap<>();
            }
        }
        JsonbDateFormat classDateFormat = locateAnnotation(propDescriptor.getDeclaringClassElement().getAnnotations(), JsonbDateFormat.class);
        if (null != classDateFormat) {
            dateFormatterMap.put(AnnotationTarget.CLASS, buildJsonbDateFormatter(classDateFormat.value(), classDateFormat.locale(), propDescriptor));
        }
        return dateFormatterMap;
    }

    /**
     * Search for {@link JsonbDateFormat} annotation on java class and construct {@link JsonbDateFormatter}.
     * If not found looks at annotations declared on property type class.
     *
     * @param classAnnotatedMember class to search not null
     * @return formatter to use
     */
    public JsonbDateFormatter getJsonbDateFormat(JsonbAnnotatedMember<Class<?>> classAnnotatedMember) {
        Objects.requireNonNull(classAnnotatedMember);
        final JsonbDateFormat dateFormat = locateAnnotation(classAnnotatedMember.getAnnotations(), JsonbDateFormat.class);
        if (null == dateFormat) {
            return contextManager.getConfigProperties().getConfigDateFormatter();
        }
        return new JsonbDateFormatter(dateFormat.value(), dateFormat.locale());
    }

    /**
     * Search for {@link JsonbNumberFormat} annotation on java class.
     *
     * @param classAnnotatedMember class to search not null
     * @return formatter to use
     */
    public JsonbNumberFormatter getJsonbNumberFormat(JsonbAnnotatedMember<Class<?>> classAnnotatedMember) {
        final JsonbNumberFormat numberFormat = locateAnnotation(classAnnotatedMember.getAnnotations(), JsonbNumberFormat.class);
        if (null == numberFormat) {
            return null;
        }
        return new JsonbNumberFormatter(numberFormat.value(), numberFormat.locale());
    }

    /**
     * Search {@link JsonbNumberFormat} on property, if not found looks at annotations declared on property type class.
     *
     * @param propDescriptor Property to search on.
     * @return Map of {@link JsonbNumberFormatter} instances categorized by their scopes (class, property, getter or setter).
     * If there is no number
     * formatter specified for given property, an empty map would be returned
     */
    public Map<AnnotationTarget, JsonbNumberFormatter> getJsonNumberFormatter(PropertyDescriptor propDescriptor) {
        Map<AnnotationTarget, JsonbNumberFormatter> dateFormatterMap = new HashMap<>();
        Map<AnnotationTarget, JsonbNumberFormat> transientAnnotationsByTarget = getAnnotationFromPropertyCategorized(JsonbNumberFormat.class, propDescriptor);
        if (0 != transientAnnotationsByTarget.size()) {
            transientAnnotationsByTarget.forEach((mapKey, ann) -> dateFormatterMap.put(mapKey, new JsonbNumberFormatter(ann.value(), ann.locale())));
        } else {
            final Optional<Class<?>> rawTypeOpt = ReflectionUtils.getOptionalRawType(propDescriptor.getPropertyType());
            if (rawTypeOpt.isPresent()) {
                Class<?> elementType = rawTypeOpt.get();
                if (!Number.class.isAssignableFrom(elementType)) {
                    return new HashMap<>();
                }
            }
        }
        JsonbNumberFormat classNumberFormat = locateAnnotation(propDescriptor.getDeclaringClassElement().getAnnotations(), JsonbNumberFormat.class);
        if (null != classNumberFormat) {
            dateFormatterMap.put(AnnotationTarget.CLASS, new JsonbNumberFormatter(classNumberFormat.value(), classNumberFormat.locale()));
        }
        return dateFormatterMap;
    }

    /**
     * Returns {@link JsonbNumberFormatter} instance if {@link JsonbNumberFormat} annotation is present.
     *
     * @param constructorParam annotated method parameter
     * @return formatter instance if {@link JsonbNumberFormat} is present otherwise null
     */
    public JsonbNumberFormatter getConstructorNumberFormatter(JsonbAnnotatedMember<Parameter> constructorParam) {
        JsonbNumberFormat ann = constructorParam.getAnnotation(JsonbNumberFormat.class);
        if (null != ann) {
            return new JsonbNumberFormatter(ann.value(), ann.locale());
        }
        return null;
    }

    /**
     * Returns {@link JsonbDateFormatter} instance if {@link JsonbDateFormat} annotation is present.
     *
     * @param constructorParam annotated method parameter
     * @return formatter instance if {@link JsonbDateFormat} is present otherwise null
     */
    public JsonbDateFormatter getConstructorDateFormatter(JsonbAnnotatedMember<Parameter> constructorParam) {
        JsonbDateFormat ann = constructorParam.getAnnotation(JsonbDateFormat.class);
        if (null != ann) {
            return new JsonbDateFormatter(DateTimeFormatter.ofPattern(ann.value(), Locale.forLanguageTag(ann.locale())), ann.value(), ann.locale());
        }
        return null;
    }

    /**
     * Creates {@link JsonbDateFormatter} caches formatter instance if possible.
     * For DEFAULT_FORMAT appropriate singleton instances from java.time.format.DateTimeFormatter
     * are used in date converters.
     */
    private JsonbDateFormatter buildJsonbDateFormatter(String dateFormat, String languageTag, PropertyDescriptor propDescriptor) {
        if (JsonbDateFormat.TIME_IN_MILLIS.equals(dateFormat) || JsonbDateFormat.DEFAULT_FORMAT.equals(dateFormat)) {
            //for epochMillis formatter is not used, for default format singleton instances of DateTimeFormatter
            //are used in the converters
            return new JsonbDateFormatter(dateFormat, languageTag);
        }
        final Optional<Class<?>> rawTypeOptional = ReflectionUtils.getOptionalRawType(propDescriptor.getPropertyType());
        final Class<?> underlyingClass = rawTypeOptional.orElse(null);
        if (null != underlyingClass && !TemporalAccessor.class.isAssignableFrom(underlyingClass) && !Date.class.isAssignableFrom(underlyingClass) && !Calendar.class.isAssignableFrom(underlyingClass)) {
            throw new IllegalStateException(LocalizedMessages.getMessage(MessageKeyConstants.UNSUPPORTED_DATE_TYPE, underlyingClass));
        }
        DateTimeFormatterBuilder formatterComposer = new DateTimeFormatterBuilder();
        formatterComposer.appendPattern(dateFormat);
        if (contextManager.getConfigProperties().isZeroTimeDefaulting()) {
            formatterComposer.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            formatterComposer.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            formatterComposer.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter resolvedFormatter = formatterComposer.toFormatter(Locale.forLanguageTag(languageTag));
        return new JsonbDateFormatter(resolvedFormatter, dateFormat, languageTag);
    }

    /**
     * Get a @JsonbVisibility annotation from a class or its package.
     *
     * @param targetClass Class to lookup annotation
     * @return Instantiated PropertyVisibilityStrategy if annotation is present
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy(Class<?> targetClass) {
        JsonbVisibility visibilityConfig = locateAnnotation(targetClass.getDeclaredAnnotations(), JsonbVisibility.class);
        if ((null == visibilityConfig) && (null != targetClass.getPackage())) {
            visibilityConfig = locateAnnotation(targetClass.getPackage().getDeclaredAnnotations(), JsonbVisibility.class);
        }
        if (null != visibilityConfig) {
            return ReflectionUtils.createNoArgConstructorInstance(ReflectionUtils.getDefaultConstructor(visibilityConfig.value(), true));
        }
        return contextManager.getConfigProperties().getPropertyVisibilityStrategy();
    }

    /**
     * Gets an annotation from first resolved annotation in a property in this order:
     * <p>1. Field, 2. Getter, 3 Setter.</p>
     * First found overrides other.
     *
     * @param annotationType Annotation class to search for
     * @param propDescriptor        property to search in
     * @param <T>             Annotation type
     * @return Annotation if found, null otherwise
     */
    private <T extends Annotation> Optional<T> getAnnotationFromProperty(Class<T> annotationType, PropertyDescriptor propDescriptor) {
        T jsonbFieldProperty = getFieldAnnotation(annotationType, propDescriptor.getFieldElement());
        if (null != jsonbFieldProperty) {
            return Optional.of(jsonbFieldProperty);
        }
        T getterMeta = getMethodAnnotation(annotationType, propDescriptor.getGetterElement());
        if (null != getterMeta) {
            return Optional.of(getterMeta);
        }
        T setterMeta = getMethodAnnotation(annotationType, propDescriptor.getSetterElement());
        if (null != setterMeta) {
            return Optional.of(setterMeta);
        }
        return Optional.empty();
    }

    /**
     * An override of {@link #getAnnotationFromProperty(Class, PropertyDescriptor)} in which it returns the results as a map so that the
     * caller can decide which
     * one to be used for read/write operation. Some annotations should have different behaviours based on the scope that
     * they're applied on.
     *
     * @param annotationType The annotation class to search
     * @param propDescriptor        The property to search in
     * @param <T>             Annotation type
     * @return A map of all occurrences of requested annotation for given property. Caller can determine based on
     * {@link AnnotationTarget} that given
     * annotation is specified on what level (Class, Property, Getter or Setter). If no annotation found for given property, an
     * empty map would be
     * returned
     */
    private <T extends Annotation> Map<AnnotationTarget, T> getAnnotationFromPropertyCategorized(Class<T> annotationType, PropertyDescriptor propDescriptor) {
        Map<AnnotationTarget, T> dateFormatterMap = new HashMap<>();
        T jsonbFieldProperty = getFieldAnnotation(annotationType, propDescriptor.getFieldElement());
        if (null != jsonbFieldProperty) {
            dateFormatterMap.put(AnnotationTarget.PROPERTY, jsonbFieldProperty);
        }
        T getterMeta = getMethodAnnotation(annotationType, propDescriptor.getGetterElement());
        if (null != getterMeta) {
            dateFormatterMap.put(AnnotationTarget.GETTER, getterMeta);
        }
        T setterMeta = getMethodAnnotation(annotationType, propDescriptor.getSetterElement());
        if (null != setterMeta) {
            dateFormatterMap.put(AnnotationTarget.SETTER, setterMeta);
        }
        return dateFormatterMap;
    }

    private <T extends Annotation> T getFieldAnnotation(Class<T> annotationType, JsonbAnnotatedMember<Field> annotatedField) {
        if (null == annotatedField) {
            return null;
        }
        return locateAnnotation(annotatedField.getAnnotations(), annotationType);
    }

    private <T extends Annotation> T locateAnnotation(Annotation[] annotationsArray, Class<T> annotationType) {
        return AnnotationFinder.findAnnotation(annotationsArray, annotationType, new HashSet<>());
    }

    /**
     * Finds annotations incompatible with {@link JsonbTransient} annotation.
     *
     * @param annotatedMember target to check
     */
    @SuppressWarnings("unchecked")
    public void validateTransientCompatibility(JsonbAnnotatedMember<?> annotatedMember) {
        if (null == annotatedMember) {
            return;
        }
        for (Class<? extends Annotation> annotationType : TRANSIENT_INCOMPATIBLE) {
            Annotation annotationInstance = locateAnnotation(annotatedMember.getAnnotations(), annotationType);
            if (null != annotationInstance) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_TRANSIENT_WITH_OTHER_ANNOTATIONS));
            }
        }
    }

    private <T extends Annotation> T getMethodAnnotation(Class<T> annotationType, JsonbAnnotatedMember<Method> annotatedMethodMember) {
        if (null == annotatedMethodMember) {
            return null;
        }
        return locateAnnotation(annotatedMethodMember.getAnnotations(), annotationType);
    }

    private <T extends Annotation> void collectAnnotationsFromInterfaces(Class<T> annotationType, Class targetClass, Map<Class<?>, T> annotationsByInterface) {
        for (Class<?> ifaceClass : targetClass.getInterfaces()) {
            T ann = locateAnnotation(ifaceClass.getDeclaredAnnotations(), annotationType);
            if (null != ann) {
                annotationsByInterface.put(ifaceClass, ann);
            }
            collectAnnotationsFromInterfaces(annotationType, ifaceClass, annotationsByInterface);
        }
    }

    /**
     * Get class interfaces recursively.
     *
     * @param rootClass Class to process.
     * @return A list of all class interfaces.
     */
    public Set<Class<?>> collectAllInterfaces(Class<?> rootClass) {
        Set<Class<?>> interfacesSet = new LinkedHashSet<>();
        Queue<Class<?>> pendingClasses = new LinkedList<>();
        pendingClasses.addAll(Arrays.asList(rootClass.getInterfaces()));
        Class<?> nextInterface;
        while ((nextInterface = pendingClasses.poll()) != null) {
            interfacesSet.add(nextInterface);
            pendingClasses.addAll(Arrays.asList(nextInterface.getInterfaces()));
        }
        return interfacesSet;
    }

    /**
     * Processes customizations.
     *
     * @param classElement Element to process.
     * @return Populated {@link ClassConfiguration} instance.
     */
    public ClassConfiguration buildClassCustomization(JsonbAnnotatedMember<Class<?>> classElement) {
        final ClassCustomizationBuilder formatterComposer = new ClassCustomizationBuilder();
        formatterComposer.setNillable(isClassNillable(classElement));
        formatterComposer.setDateFormatter(getJsonbDateFormat(classElement));
        formatterComposer.setNumberFormatter(getJsonbNumberFormat(classElement));
        formatterComposer.setCreator(getCreator(classElement.getElement()));
        formatterComposer.setPropertyOrder(getPropertyOrder(classElement));
        formatterComposer.setAdapterInfo(getAdapterBinding(classElement));
        formatterComposer.setSerializerBinding(getSerializerBinding(classElement));
        formatterComposer.setDeserializerBinding(getDeserializerBinding(classElement));
        formatterComposer.setPropertyVisibilityStrategy(getPropertyVisibilityStrategy(classElement.getElement()));
        return formatterComposer.buildClassCustomization();
    }

    /**
     * Returns class if {@link ImplementationClass} annotation is present.
     *
     * @param propDescriptor annotated property
     * @return Class if {@link ImplementationClass} is present otherwise null
     */
    public Class<?> getImplementationClass(PropertyDescriptor propDescriptor) {
        Optional<ImplementationClass> implementationClassOptional = getAnnotationFromProperty(ImplementationClass.class, propDescriptor);
        return implementationClassOptional.<Class<?>>map(ImplementationClass::value).orElse(null);
    }

    /**
     * Collect annotations of given class, its interfaces and the package.
     *
     * @param targetClass Class to process.
     * @return Element with class and annotations.
     */
    public JsonbAnnotatedMember<Class<?>> gatherAnnotations(Class<?> targetClass) {
        JsonbAnnotatedMember<Class<?>> annotatedClassMember = new JsonbAnnotatedMember<>(targetClass);
        if (StandardSerializerRegistry.isKnownType(targetClass)) {
            return annotatedClassMember;
        }
        for (Class<?> interfaceType : collectAllInterfaces(targetClass)) {
            addIfNotPresent(annotatedClassMember, interfaceType.getDeclaredAnnotations());
        }
        if (!targetClass.isPrimitive() && !targetClass.isArray() && (null != targetClass.getPackage())) {
            addIfNotPresent(annotatedClassMember, targetClass.getPackage().getAnnotations());
        }
        return annotatedClassMember;
    }

    private void addIfNotPresent(JsonbAnnotatedMember<?> annotatedMember, Annotation... annotationArray) {
        for (Annotation ann : annotationArray) {
            if (null == annotatedMember.getAnnotation(ann.annotationType())) {
                annotatedMember.addAnnotation(ann);
            }
        }
    }
}

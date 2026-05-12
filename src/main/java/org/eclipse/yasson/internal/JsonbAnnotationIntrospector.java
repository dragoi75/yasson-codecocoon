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

import org.eclipse.yasson.ConcreteImplementation;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.components.JsonbSerializerBinding;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.model.AnnotationTargetKind;
import org.eclipse.yasson.internal.model.CreatorProfile;
import org.eclipse.yasson.internal.model.JsonbAnnotationHolder;
import org.eclipse.yasson.internal.model.JsonbCreatorInvoker;
import org.eclipse.yasson.internal.model.PropertyDescriptor;
import org.eclipse.yasson.internal.model.customization.ClassCustomizationConfigurator;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.serializer.JsonbDateTimeFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumericFormatter;

/**
 * Introspects configuration on classes and their properties by reading annotations.
 */
public class JsonbAnnotationIntrospector {

    private final JsonbRuntimeContext runtimeContext;
    private final ConstructorPropertiesAnnotationInspector constructorPropertiesInspector;

    /**
     * Annotations to report exception when used in combination with {@link JsonbTransient}.
     */
    public static final List<Class<? extends Annotation>> TRANSIENT_INCOMPATIBLE =
            Arrays.asList(JsonbDateFormat.class, JsonbNumberFormat.class, JsonbProperty.class,
                          JsonbTypeAdapter.class, JsonbTypeSerializer.class, JsonbTypeDeserializer.class);

    /**
     * Creates annotation introspecting component passing {@link JsonbRuntimeContext} inside.
     *
     * @param runtimeContext mandatory
     */
    public JsonbAnnotationIntrospector(JsonbRuntimeContext runtimeContext) {
        Objects.requireNonNull(runtimeContext);
        this.runtimeContext = runtimeContext;
        this.constructorPropertiesInspector = ConstructorPropertiesAnnotationInspector.forJsonbContext(runtimeContext);
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

    private String getJsonbPropertyCustomizedName(PropertyDescriptor propDescriptor, JsonbAnnotationHolder<Method> methodHolder) {
        JsonbProperty jsonbPropertyAnnot = getMethodAnnotation(JsonbProperty.class, methodHolder);
        if (jsonbPropertyAnnot != null && !jsonbPropertyAnnot.value().isEmpty()) {
            return jsonbPropertyAnnot.value();
        }
        //in case of property name getter/setter override field value
        JsonbProperty jsonbFieldAnnot = getFieldAnnotation(JsonbProperty.class, propDescriptor.getFieldElement());
        if (jsonbFieldAnnot != null && !jsonbFieldAnnot.value().isEmpty()) {
            return jsonbFieldAnnot.value();
        }

        return null;
    }

    /**
     * Searches for JsonbCreator annotation on constructors and static methods.
     *
     * @param targetClass class to search
     * @return JsonbCreator metadata object
     */
    public JsonbCreatorInvoker getCreator(Class<?> targetClass) {
        JsonbCreatorInvoker creatorInvoker = null;
        Constructor<?>[] constructorsArray =
                AccessController.doPrivileged((PrivilegedAction<Constructor<?>[]>) targetClass::getDeclaredConstructors);

        for (Constructor<?> targetConstructor : constructorsArray) {
            final jakarta.json.bind.annotation.JsonbCreator creatorMarker = locateAnnotation(targetConstructor.getDeclaredAnnotations(),
                                                                                 jakarta.json.bind.annotation.JsonbCreator.class);
            if (creatorMarker != null) {
                creatorInvoker = buildJsonbCreator(targetConstructor, creatorInvoker, targetClass);
            }
        }

        Method[] methodsArray =
                AccessController.doPrivileged((PrivilegedAction<Method[]>) targetClass::getDeclaredMethods);
        for (Method targetMethod : methodsArray) {
            final jakarta.json.bind.annotation.JsonbCreator creatorMarker = locateAnnotation(targetMethod.getDeclaredAnnotations(),
                                                                                 jakarta.json.bind.annotation.JsonbCreator.class);
            if (creatorMarker != null && Modifier.isStatic(targetMethod.getModifiers())) {
                if (!targetClass.equals(targetMethod.getReturnType())) {
                    throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INCOMPATIBLE_FACTORY_CREATOR_RETURN_TYPE,
                            targetMethod,
                            targetClass));
                }
                creatorInvoker = buildJsonbCreator(targetMethod, creatorInvoker, targetClass);
            }
        }
        if (creatorInvoker == null) {
            creatorInvoker = constructorPropertiesInspector.getCreator(constructorsArray);
        }
        return creatorInvoker;
    }

    private JsonbCreatorInvoker buildJsonbCreator(Executable executableElement, JsonbCreatorInvoker currentInvoker, Class<?> targetClass) {
        if (currentInvoker != null) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.MULTIPLE_JSONB_CREATORS, targetClass));
        }

        final Parameter[] paramsArray = executableElement.getParameters();

        CreatorProfile[] creatorProfiles = new CreatorProfile[paramsArray.length];
        for (int idx = 0; idx < paramsArray.length; idx++) {
            final Parameter param = paramsArray[idx];
            final JsonbProperty propertyAnnot = param.getAnnotation(JsonbProperty.class);
            if (propertyAnnot != null && !propertyAnnot.value().isEmpty()) {
                creatorProfiles[idx] = new CreatorProfile(propertyAnnot.value(), param, runtimeContext);
            } else {
                creatorProfiles[idx] = new CreatorProfile(param.getName(), param, runtimeContext);
            }
        }

        return new JsonbCreatorInvoker(executableElement, creatorProfiles);
    }

    /**
     * Checks for {@link JsonbAdapter} on a property.
     *
     * @param propDescriptor property not null
     * @return components info
     */
    public TypeAdapterBinding getAdapterBinding(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        JsonbTypeAdapter adapterAnnot = getAnnotationFromProperty(JsonbTypeAdapter.class, propDescriptor)
                .orElseGet(() -> getAnnotationFromPropertyType(propDescriptor, JsonbTypeAdapter.class));
        if (adapterAnnot == null) {
            return null;
        }

        return getAdapterBindingFromAnnotation(adapterAnnot, ReflectionTypeResolver.getOptionalRawType(propDescriptor.getPropertyType()));
    }

    /**
     * Checks for {@link JsonbAdapter} on a type.
     *
     * @param classHolder type not null
     * @return components info
     */
    public TypeAdapterBinding getAdapterBinding(JsonbAnnotationHolder<Class<?>> classHolder) {
        Objects.requireNonNull(classHolder);

        JsonbTypeAdapter adapterAnnot = classHolder.getElement().getAnnotation(JsonbTypeAdapter.class);
        if (adapterAnnot == null) {
            return null;
        }

        return getAdapterBindingFromAnnotation(adapterAnnot, Optional.ofNullable(classHolder.getElement()));
    }

    private TypeAdapterBinding getAdapterBindingFromAnnotation(JsonbTypeAdapter adapterAnnot, Optional<Class<?>> expectedType) {
        final Class<? extends JsonbAdapter> adapterImplClass = adapterAnnot.value();
        final TypeAdapterBinding typeAdapterBinding = runtimeContext.getComponentMatcher().inspectAdapterBinding(adapterImplClass, null);

        if (expectedType.isPresent() && !(
                ReflectionTypeResolver.getRawType(typeAdapterBinding.getBindingType()).isAssignableFrom(expectedType.get()))) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.ADAPTER_INCOMPATIBLE,
                                                         typeAdapterBinding.getBindingType(),
                                                         expectedType.get()));
        }
        return typeAdapterBinding;
    }

    /**
     * Checks for {@link JsonbDeserializer} on a property.
     *
     * @param propDescriptor property not null
     * @return components info
     */
    public JsonbDeserializerBinding getDeserializerBinding(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        JsonbTypeDeserializer deserializerAnnot = getAnnotationFromProperty(JsonbTypeDeserializer.class, propDescriptor)
                .orElseGet(() -> getAnnotationFromPropertyType(propDescriptor, JsonbTypeDeserializer.class));
        if (deserializerAnnot == null) {
            return null;
        }

        final Class<? extends JsonbDeserializer> deserializerImplClass = deserializerAnnot.value();
        return runtimeContext.getComponentMatcher().inspectDeserializerBinding(deserializerImplClass, null);
    }

    /**
     * Checks for {@link JsonbDeserializer} on a type.
     *
     * @param classHolder type not null
     * @return components info
     */
    public JsonbDeserializerBinding getDeserializerBinding(JsonbAnnotationHolder<Class<?>> classHolder) {
        Objects.requireNonNull(classHolder);
        JsonbTypeDeserializer deserializerAnnot = classHolder.getElement().getAnnotation(JsonbTypeDeserializer.class);
        if (deserializerAnnot == null) {
            return null;
        }

        final Class<? extends JsonbDeserializer> deserializerImplClass = deserializerAnnot.value();
        return runtimeContext.getComponentMatcher().inspectDeserializerBinding(deserializerImplClass, null);
    }

    /**
     * Checks for {@link JsonbSerializer} on a property.
     *
     * @param propDescriptor property not null
     * @return components info
     */
    public JsonbSerializerBinding getSerializerBinding(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        JsonbTypeSerializer typeSerializer = getAnnotationFromProperty(JsonbTypeSerializer.class, propDescriptor)
                .orElseGet(() -> getAnnotationFromPropertyType(propDescriptor, JsonbTypeSerializer.class));
        if (typeSerializer == null) {
            return null;
        }

        final Class<? extends JsonbSerializer> serializerImplementation = typeSerializer.value();
        return runtimeContext.getComponentMatcher().inspectSerializerBinding(serializerImplementation, null);

    }

    /**
     * Checks for {@link JsonbSerializer} on a type.
     *
     * @param classHolder type not null
     * @return components info
     */
    public JsonbSerializerBinding getSerializerBinding(JsonbAnnotationHolder<Class<?>> classHolder) {
        Objects.requireNonNull(classHolder);
        JsonbTypeSerializer typeSerializer = classHolder.getElement().getAnnotation(JsonbTypeSerializer.class);
        if (typeSerializer == null) {
            return null;
        }

        final Class<? extends JsonbSerializer> serializerImplementation = typeSerializer.value();
        return runtimeContext.getComponentMatcher().inspectSerializerBinding(serializerImplementation, null);
    }

    private <T extends Annotation> T getAnnotationFromPropertyType(PropertyDescriptor propDescriptor, Class<T> annotationType) {
        final Optional<Class<?>> optionalRawClass = ReflectionTypeResolver.getOptionalRawType(propDescriptor.getPropertyType());
        if (!optionalRawClass.isPresent()) {
            //will not work for type variable properties, which are bound to class that is annotated.
            return null;
        }
        return locateAnnotation(gatherAnnotations(optionalRawClass.get()).getAnnotations(), annotationType);
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

        final Optional<JsonbProperty> jsonbPropertyOpt = getAnnotationFromProperty(JsonbProperty.class, propDescriptor);
        return jsonbPropertyOpt.map(JsonbProperty::nillable);

    }

    /**
     * Checks for JsonbNillable annotation on a class, its superclasses and interfaces.
     *
     * @param classHolder class to search JsonbNillable in.
     * @return true if found
     */
    public boolean isClassNillable(JsonbAnnotationHolder<Class<?>> classHolder) {
        final JsonbNillable nillableAnnotation = locateAnnotation(classHolder.getAnnotations(), JsonbNillable.class);
        if (nillableAnnotation != null) {
            return nillableAnnotation.value();
        }
        return runtimeContext.getConfigProperties().getConfigNullable();
    }

    /**
     * Checks for {@link JsonbPropertyOrder} annotation.
     *
     * @param classHolder class to search on
     * @return ordered properties names or null if not found
     */
    public String[] getPropertyOrder(JsonbAnnotationHolder<Class<?>> classHolder) {
        final JsonbPropertyOrder propertyOrderAnnotation = classHolder.getElement().getAnnotation(JsonbPropertyOrder.class);
        return propertyOrderAnnotation != null ? propertyOrderAnnotation.value() : null;
    }

    /**
     * Checks if property is annotated transient. If JsonbTransient annotation is present on field getter or setter, and other
     * annotation is present
     * on either of it, JsonbException is thrown with message describing collision.
     *
     * @param propDescriptor The property to inspect if there is any {@link JsonbTransient} annotation defined for it
     * @return Set of {@link AnnotationTargetKind}s specifying in which scope the {@link JsonbTransient} is applied
     */
    public EnumSet<AnnotationTargetKind> getJsonbTransientCategorized(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);
        EnumSet<AnnotationTargetKind> transientTargetsSet = EnumSet.noneOf(AnnotationTargetKind.class);
        Map<AnnotationTargetKind, JsonbTransient> transientAnnotationsByTarget = getAnnotationFromPropertyCategorized(
                JsonbTransient.class,
                propDescriptor);
        if (transientAnnotationsByTarget.size() > 0) {
            transientTargetsSet.addAll(transientAnnotationsByTarget.keySet());
            return transientTargetsSet;
        }

        return transientTargetsSet;
    }

    /**
     * Search {@link JsonbDateFormat} on property, if not found looks at annotations declared on property type class.
     *
     * @param propDescriptor Property to search on.
     * @return Map of {@link JsonbDateTimeFormatter} instances categorized by their scopes (class, property, getter or setter). If
     * there is no date
     * formatter specified for given property, an empty map would be returned
     */
    public Map<AnnotationTargetKind, JsonbDateTimeFormatter> getJsonbDateFormatCategorized(PropertyDescriptor propDescriptor) {
        Objects.requireNonNull(propDescriptor);

        Map<AnnotationTargetKind, JsonbDateTimeFormatter> dateTimeFormatterMap = new HashMap<>();
        Map<AnnotationTargetKind, JsonbDateFormat> transientAnnotationsByTarget = getAnnotationFromPropertyCategorized(
                JsonbDateFormat.class,
                propDescriptor);
        if (transientAnnotationsByTarget.size() != 0) {
            transientAnnotationsByTarget.forEach((targetKind, dateFormatAnn) -> dateTimeFormatterMap
                    .put(targetKind, buildJsonbDateFormatter(dateFormatAnn.value(), dateFormatAnn.locale(), propDescriptor)));
        }

        // No date format on property, try class level
        // if property is not TypeVariable and its class is not date skip it
        final Optional<Class<?>> propertyTypeOptional = ReflectionTypeResolver.getOptionalRawType(propDescriptor.getPropertyType());
        if (propertyTypeOptional.isPresent()) {
            Class<?> actualRawType = propertyTypeOptional.get();
            if (!(
                    Date.class.isAssignableFrom(actualRawType) || Calendar.class.isAssignableFrom(actualRawType)
                            || TemporalAccessor.class.isAssignableFrom(actualRawType))) {
                return new HashMap<>();
            }
        }

        JsonbDateFormat classDateFormat = locateAnnotation(propDescriptor.getDeclaringClassElement().getAnnotations(),
                                                                 JsonbDateFormat.class);
        if (classDateFormat != null) {
            dateTimeFormatterMap.put(AnnotationTargetKind.CLASS,
                       buildJsonbDateFormatter(classDateFormat.value(), classDateFormat.locale(), propDescriptor));
        }

        return dateTimeFormatterMap;
    }

    /**
     * Search for {@link JsonbDateFormat} annotation on java class and construct {@link JsonbDateTimeFormatter}.
     * If not found looks at annotations declared on property type class.
     *
     * @param classHolder class to search not null
     * @return formatter to use
     */
    public JsonbDateTimeFormatter getJsonbDateFormat(JsonbAnnotationHolder<Class<?>> classHolder) {
        Objects.requireNonNull(classHolder);
        final JsonbDateFormat dateFormatAnnotation = locateAnnotation(classHolder.getAnnotations(), JsonbDateFormat.class);
        if (dateFormatAnnotation == null) {
            return runtimeContext.getConfigProperties().getConfigDateFormatter();
        }
        return new JsonbDateTimeFormatter(dateFormatAnnotation.value(), dateFormatAnnotation.locale());
    }

    /**
     * Search for {@link JsonbNumberFormat} annotation on java class.
     *
     * @param classHolder class to search not null
     * @return formatter to use
     */
    public JsonbNumericFormatter getJsonbNumberFormat(JsonbAnnotationHolder<Class<?>> classHolder) {
        final JsonbNumberFormat numberFormatAnnotation = locateAnnotation(classHolder.getAnnotations(), JsonbNumberFormat.class);
        if (numberFormatAnnotation == null) {
            return null;
        }
        return new JsonbNumericFormatter(numberFormatAnnotation.value(), numberFormatAnnotation.locale());
    }

    /**
     * Search {@link JsonbNumberFormat} on property, if not found looks at annotations declared on property type class.
     *
     * @param propDescriptor Property to search on.
     * @return Map of {@link JsonbNumericFormatter} instances categorized by their scopes (class, property, getter or setter).
     * If there is no number
     * formatter specified for given property, an empty map would be returned
     */
    public Map<AnnotationTargetKind, JsonbNumericFormatter> getJsonNumberFormatter(PropertyDescriptor propDescriptor) {
        Map<AnnotationTargetKind, JsonbNumericFormatter> dateTimeFormatterMap = new HashMap<>();
        Map<AnnotationTargetKind, JsonbNumberFormat> transientAnnotationsByTarget = getAnnotationFromPropertyCategorized(
                JsonbNumberFormat.class,
                propDescriptor);
        if (transientAnnotationsByTarget.size() == 0) {
            final Optional<Class<?>> propertyTypeOptional = ReflectionTypeResolver.getOptionalRawType(propDescriptor.getPropertyType());
            if (propertyTypeOptional.isPresent()) {
                Class<?> actualRawType = propertyTypeOptional.get();
                if (!Number.class.isAssignableFrom(actualRawType)) {
                    return new HashMap<>();
                }
            }
        } else {
            transientAnnotationsByTarget.forEach((targetKind, dateFormatAnn) -> dateTimeFormatterMap
                    .put(targetKind, new JsonbNumericFormatter(dateFormatAnn.value(), dateFormatAnn.locale())));
        }

        JsonbNumberFormat classNumberFormat = locateAnnotation(propDescriptor.getDeclaringClassElement().getAnnotations(),
                                                                     JsonbNumberFormat.class);
        if (classNumberFormat != null) {
            dateTimeFormatterMap.put(AnnotationTargetKind.CLASS,
                       new JsonbNumericFormatter(classNumberFormat.value(), classNumberFormat.locale()));
        }

        return dateTimeFormatterMap;
    }

    /**
     * Returns {@link JsonbNumericFormatter} instance if {@link JsonbNumberFormat} annotation is present.
     *
     * @param parameterHolder annotated method parameter
     * @return formatter instance if {@link JsonbNumberFormat} is present otherwise null
     */
    public JsonbNumericFormatter getConstructorNumberFormatter(JsonbAnnotationHolder<Parameter> parameterHolder) {
        JsonbNumberFormat dateFormatAnn = parameterHolder.getAnnotation(JsonbNumberFormat.class);
        if (dateFormatAnn != null) {
            return new JsonbNumericFormatter(dateFormatAnn.value(), dateFormatAnn.locale());
        }
        return null;
    }

    /**
     * Returns {@link JsonbDateTimeFormatter} instance if {@link JsonbDateFormat} annotation is present.
     *
     * @param parameterHolder annotated method parameter
     * @return formatter instance if {@link JsonbDateFormat} is present otherwise null
     */
    public JsonbDateTimeFormatter getConstructorDateFormatter(JsonbAnnotationHolder<Parameter> parameterHolder) {
        JsonbDateFormat dateFormatAnn = parameterHolder.getAnnotation(JsonbDateFormat.class);
        if (dateFormatAnn != null) {
            return new JsonbDateTimeFormatter(DateTimeFormatter
                                                  .ofPattern(dateFormatAnn.value(), Locale.forLanguageTag(dateFormatAnn.locale())),
                                          dateFormatAnn.value(), dateFormatAnn.locale());
        }
        return null;
    }

    /**
     * Creates {@link JsonbDateTimeFormatter} caches formatter instance if possible.
     * For DEFAULT_FORMAT appropriate singleton instances from java.time.format.DateTimeFormatter
     * are used in date converters.
     */
    private JsonbDateTimeFormatter buildJsonbDateFormatter(String dateFormatAnnotation, String languageTag, PropertyDescriptor propDescriptor) {
        if (JsonbDateFormat.TIME_IN_MILLIS.equals(dateFormatAnnotation) || JsonbDateFormat.DEFAULT_FORMAT.equals(dateFormatAnnotation)) {
            //for epochMillis formatter is not used, for default format singleton instances of DateTimeFormatter
            //are used in the converters
            return new JsonbDateTimeFormatter(dateFormatAnnotation, languageTag);
        }

        final Optional<Class<?>> optionalRawClass = ReflectionTypeResolver.getOptionalRawType(propDescriptor.getPropertyType());
        final Class<?> valueClass = optionalRawClass.orElse(null);

        if (valueClass != null
                && !TemporalAccessor.class.isAssignableFrom(valueClass)
                && !Date.class.isAssignableFrom(valueClass)
                && !Calendar.class.isAssignableFrom(valueClass)) {
            throw new IllegalStateException(MessageBundle.getMessage(MessageKeyConstants.UNSUPPORTED_DATE_TYPE, valueClass));
        }

        DateTimeFormatterBuilder formatterFactory = new DateTimeFormatterBuilder();
        formatterFactory.appendPattern(dateFormatAnnotation);
        if (runtimeContext.getConfigProperties().isZeroTimeDefaulting()) {
            formatterFactory.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            formatterFactory.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            formatterFactory.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter formatter = formatterFactory.toFormatter(Locale.forLanguageTag(languageTag));
        return new JsonbDateTimeFormatter(formatter, dateFormatAnnotation, languageTag);
    }

    /**
     * Get a @JsonbVisibility annotation from a class or its package.
     *
     * @param targetClass Class to lookup annotation
     * @return Instantiated PropertyVisibilityStrategy if annotation is present
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy(Class<?> targetClass) {
        JsonbVisibility visibilitySpec = locateAnnotation(targetClass.getDeclaredAnnotations(), JsonbVisibility.class);
        if ((visibilitySpec == null) && (targetClass.getPackage() != null)) {
            visibilitySpec = locateAnnotation(targetClass.getPackage().getDeclaredAnnotations(), JsonbVisibility.class);
        }
        if (visibilitySpec != null) {
            return ReflectionTypeResolver.instantiateNoArgs(
                    ReflectionTypeResolver.getDefaultConstructor(visibilitySpec.value(), true));
        }
        return runtimeContext.getConfigProperties().getPropertyVisibilityStrategy();
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
        T jsonbFieldAnnot = getFieldAnnotation(annotationType, propDescriptor.getFieldElement());
        if (jsonbFieldAnnot != null) {
            return Optional.of(jsonbFieldAnnot);
        }

        T getterMeta = getMethodAnnotation(annotationType, propDescriptor.getGetterElement());
        if (getterMeta != null) {
            return Optional.of(getterMeta);
        }

        T setterMeta = getMethodAnnotation(annotationType, propDescriptor.getSetterElement());
        if (setterMeta != null) {
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
     * {@link AnnotationTargetKind} that given
     * annotation is specified on what level (Class, Property, Getter or Setter). If no annotation found for given property, an
     * empty map would be
     * returned
     */
    private <T extends Annotation> Map<AnnotationTargetKind, T> getAnnotationFromPropertyCategorized(Class<T> annotationType,
                                                                                                     PropertyDescriptor propDescriptor) {
        Map<AnnotationTargetKind, T> dateTimeFormatterMap = new HashMap<>();
        T jsonbFieldAnnot = getFieldAnnotation(annotationType, propDescriptor.getFieldElement());
        if (jsonbFieldAnnot != null) {
            dateTimeFormatterMap.put(AnnotationTargetKind.PROPERTY, jsonbFieldAnnot);
        }

        T getterMeta = getMethodAnnotation(annotationType, propDescriptor.getGetterElement());
        if (getterMeta != null) {
            dateTimeFormatterMap.put(AnnotationTargetKind.GETTER, getterMeta);
        }

        T setterMeta = getMethodAnnotation(annotationType, propDescriptor.getSetterElement());
        if (setterMeta != null) {
            dateTimeFormatterMap.put(AnnotationTargetKind.SETTER, setterMeta);
        }

        return dateTimeFormatterMap;
    }

    private <T extends Annotation> T getFieldAnnotation(Class<T> annotationType, JsonbAnnotationHolder<Field> fieldHolder) {
        if (fieldHolder == null) {
            return null;
        }
        return locateAnnotation(fieldHolder.getAnnotations(), annotationType);
    }

    private <T extends Annotation> T locateAnnotation(Annotation[] annotationsArray, Class<T> annotationType) {
        return AnnotationLocator.locateAnnotation(annotationsArray, annotationType, new HashSet<>());
    }

    /**
     * Finds annotations incompatible with {@link JsonbTransient} annotation.
     *
     * @param holder target to check
     */
    @SuppressWarnings("unchecked")
    public void verifyTransientIncompatibility(JsonbAnnotationHolder<?> holder) {
        if (holder == null) {
            return;
        }

        for (Class<? extends Annotation> annotationType : TRANSIENT_INCOMPATIBLE) {
            Annotation dateFormatAnn = locateAnnotation(holder.getAnnotations(), annotationType);
            if (dateFormatAnn != null) {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.JSONB_TRANSIENT_WITH_OTHER_ANNOTATIONS));
            }
        }
    }

    private <T extends Annotation> T getMethodAnnotation(Class<T> annotationType, JsonbAnnotationHolder<Method> methodHolder) {
        if (methodHolder == null) {
            return null;
        }
        return locateAnnotation(methodHolder.getAnnotations(), annotationType);
    }

    private <T extends Annotation> void gatherFromInterfaces(Class<T> annotationType,
                                                             Class targetClass,
                                                             Map<Class<?>, T> annotationsMap) {

        for (Class<?> iface : targetClass.getInterfaces()) {
            T dateFormatAnn = locateAnnotation(iface.getDeclaredAnnotations(), annotationType);
            if (dateFormatAnn != null) {
                annotationsMap.put(iface, dateFormatAnn);
            }
            gatherFromInterfaces(annotationType, iface, annotationsMap);
        }
    }

    /**
     * Get class interfaces recursively.
     *
     * @param type Class to process.
     * @return A list of all class interfaces.
     */
    public Set<Class<?>> collectAllInterfaces(Class<?> type) {
        Set<Class<?>> orderedInterfaces = new LinkedHashSet<>();
        Queue<Class<?>> scanQueue = new LinkedList<>();
        scanQueue.addAll(Arrays.asList(type.getInterfaces()));
        Class<?> nextInterface;
        while ((nextInterface = scanQueue.poll()) != null) {
            orderedInterfaces.add(nextInterface);
            scanQueue.addAll(Arrays.asList(nextInterface.getInterfaces()));
        }
        return orderedInterfaces;
    }

    /**
     * Processes customizations.
     *
     * @param classHolder Element to process.
     * @return Populated {@link ClassSerializationConfig} instance.
     */
    public ClassSerializationConfig analyzeCustomization(JsonbAnnotationHolder<Class<?>> classHolder) {
        final ClassCustomizationConfigurator formatterFactory = new ClassCustomizationConfigurator();
        formatterFactory.setNillable(isClassNillable(classHolder));
        formatterFactory.setDateFormatter(getJsonbDateFormat(classHolder));
        formatterFactory.setNumberFormatter(getJsonbNumberFormat(classHolder));
        formatterFactory.setCreator(getCreator(classHolder.getElement()));
        formatterFactory.setPropertyOrder(getPropertyOrder(classHolder));
        formatterFactory.setAdapterInfo(getAdapterBinding(classHolder));
        formatterFactory.setSerializerBinding(getSerializerBinding(classHolder));
        formatterFactory.setDeserializerBinding(getDeserializerBinding(classHolder));
        formatterFactory.setPropertyVisibilityStrategy(getPropertyVisibilityStrategy(classHolder.getElement()));
        return formatterFactory.buildClassSerializationConfig();
    }

    /**
     * Returns class if {@link ConcreteImplementation} annotation is present.
     *
     * @param propDescriptor annotated property
     * @return Class if {@link ConcreteImplementation} is present otherwise null
     */
    public Class<?> getImplementationClass(PropertyDescriptor propDescriptor) {
        Optional<ConcreteImplementation> concreteImplementationOpt = getAnnotationFromProperty(ConcreteImplementation.class, propDescriptor);
        return concreteImplementationOpt.<Class<?>>map(ConcreteImplementation::getValue).orElse(null);
    }

    /**
     * Collect annotations of given class, its interfaces and the package.
     *
     * @param targetClass Class to process.
     * @return Element with class and annotations.
     */
    public JsonbAnnotationHolder<Class<?>> gatherAnnotations(Class<?> targetClass) {
        JsonbAnnotationHolder<Class<?>> classAnnotationHolder = new JsonbAnnotationHolder<>(targetClass);

        for (Class<?> interfaceClass : collectAllInterfaces(targetClass)) {
            addIfNotPresent(classAnnotationHolder, interfaceClass.getDeclaredAnnotations());
        }

        if (!targetClass.isPrimitive() && !targetClass.isArray() && (targetClass.getPackage() != null)) {
            addIfNotPresent(classAnnotationHolder, targetClass.getPackage().getAnnotations());
        }
        return classAnnotationHolder;
    }

    private void addIfNotPresent(JsonbAnnotationHolder<?> annotationHolder, Annotation... annArray) {
        for (Annotation dateFormatAnn : annArray) {
            if (annotationHolder.getAnnotation(dateFormatAnn.annotationType()) == null) {
                annotationHolder.addAnnotation(dateFormatAnn);
            }
        }
    }
}

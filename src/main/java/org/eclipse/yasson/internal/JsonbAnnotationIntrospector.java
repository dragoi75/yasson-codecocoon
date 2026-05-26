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
import org.eclipse.yasson.internal.model.JsonbAnnotationHolder;
import org.eclipse.yasson.internal.model.JsonbInstantiator;
import org.eclipse.yasson.internal.model.PropertyDescriptor;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.model.customization.ClassCustomizationBuilder;
import org.eclipse.yasson.internal.properties.ErrorMessageKeys;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.serializer.DefaultSerializers;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * Introspects configuration on classes and their properties by reading annotations.
 */
public class JsonbAnnotationIntrospector {

    private final JsonbRuntimeContext runtimeContext;

    private final ConstructorPropertiesAnnotationIntrospector propsIntrospector;

    /**
     * Annotations to report exception when used in combination with {@link JsonbTransient}.
     */
    public static final List<Class<? extends Annotation>> TRANSIENT_INCOMPATIBLE = Arrays.asList(JsonbDateFormat.class, JsonbNumberFormat.class, JsonbProperty.class, JsonbTypeAdapter.class, JsonbTypeSerializer.class, JsonbTypeDeserializer.class);

    /**
     * Returns {@link JsonbDateFormatter} instance if {@link JsonbDateFormat} annotation is present.
     *
     * @param parameterHolder annotated method parameter
     * @return formatter instance if {@link JsonbDateFormat} is present otherwise null
     */
    public JsonbDateFormatter getConstructorDateFormatter(JsonbAnnotationHolder<Parameter> parameterHolder) {
        JsonbDateFormat anno = parameterHolder.getAnnotation(JsonbDateFormat.class);
        if (null != anno) {
            return new JsonbDateFormatter(DateTimeFormatter.ofPattern(anno.value(), Locale.forLanguageTag(anno.locale())), anno.value(), anno.locale());
        }
        return null;
    }

    /**
     * Get class interfaces recursively.
     *
     * @param startClass Class to process.
     * @return A list of all class interfaces.
     */
    public Set<Class<?>> gatherInterfaces(Class<?> startClass) {
        Set<Class<?>> interfacesSet = new LinkedHashSet<>();
        Queue<Class<?>> scanQueue = new LinkedList<>();
        scanQueue.addAll(Arrays.asList(startClass.getInterfaces()));
        Class<?> nextInterface;
        while ((nextInterface = scanQueue.poll()) != null) {
            interfacesSet.add(nextInterface);
            scanQueue.addAll(Arrays.asList(nextInterface.getInterfaces()));
        }
        return interfacesSet;
    }

    /**
     * Gets an annotation from first resolved annotation in a property in this order:
     * <p>1. Field, 2. Getter, 3 Setter.</p>
     * First found overrides other.
     *
     * @param annoType Annotation class to search for
     * @param propDesc        property to search in
     * @param <T>             Annotation type
     * @return Annotation if found, null otherwise
     */
    private <T extends Annotation> Optional<T> getAnnotationFromProperty(Class<T> annoType, PropertyDescriptor propDesc) {
        T propertyAnnotation = getFieldAnnotation(annoType, propDesc.getFieldElement());
        if (null != propertyAnnotation) {
            return Optional.of(propertyAnnotation);
        }
        T getterAnn = getMethodAnnotation(annoType, propDesc.getGetterElement());
        if (null != getterAnn) {
            return Optional.of(getterAnn);
        }
        T setterAnn = getMethodAnnotation(annoType, propDesc.getSetterElement());
        if (null != setterAnn) {
            return Optional.of(setterAnn);
        }
        return Optional.empty();
    }

    /**
     * Finds annotations incompatible with {@link JsonbTransient} annotation.
     *
     * @param elementHolder target to check
     */
    @SuppressWarnings("unchecked")
    public void validateTransientCompatibility(JsonbAnnotationHolder<?> elementHolder) {
        if (null == elementHolder) {
            return;
        }
        for (Class<? extends Annotation> annotationType : TRANSIENT_INCOMPATIBLE) {
            Annotation anno = locateAnnotation(elementHolder.getAnnotations(), annotationType);
            if (null != anno) {
                throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.JSONB_TRANSIENT_WITH_OTHER_ANNOTATIONS));
            }
        }
    }

    /**
     * Checks for {@link JsonbDeserializer} on a type.
     *
     * @param classHolder type not null
     * @return components info
     */
    public DeserializerBinding getDeserializerBinding(JsonbAnnotationHolder<Class<?>> classHolder) {
        Objects.requireNonNull(classHolder);
        JsonbTypeDeserializer deserializerAnnot = classHolder.getElement().getAnnotation(JsonbTypeDeserializer.class);
        if (null == deserializerAnnot) {
            return null;
        }
        final Class<? extends JsonbDeserializer> deserializerType = deserializerAnnot.value();
        return runtimeContext.getComponentMatcher().introspectDeserializerBinding(deserializerType, null);
    }

    private <T extends Annotation> void collectAnnotationsFromInterfaces(Class<T> annoType, Class type, Map<Class<?>, T> collectedByClass) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            T anno = locateAnnotation(interfaceType.getDeclaredAnnotations(), annoType);
            if (null != anno) {
                collectedByClass.put(interfaceType, anno);
            }
            collectAnnotationsFromInterfaces(annoType, interfaceType, collectedByClass);
        }
    }

    private AdapterBinding getAdapterBindingFromAnnotation(JsonbTypeAdapter adapterAnnot, Optional<Class<?>> expectedType) {
        final Class<? extends JsonbAdapter> adapterType = adapterAnnot.value();
        final AdapterBinding resolvedBinding = runtimeContext.getComponentMatcher().introspectAdapterBinding(adapterType, null);
        if (expectedType.isPresent() && !(ReflectionUtils.getRawType(resolvedBinding.getBindingType()).isAssignableFrom(expectedType.get()))) {
            throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.ADAPTER_INCOMPATIBLE, resolvedBinding.getBindingType(), expectedType.get()));
        }
        return resolvedBinding;
    }

    /**
     * Checks for {@link JsonbPropertyOrder} annotation.
     *
     * @param classAnnotationHolder class to search on
     * @return ordered properties names or null if not found
     */
    public String[] getPropertyOrder(JsonbAnnotationHolder<Class<?>> classAnnotationHolder) {
        final JsonbPropertyOrder propertyOrderAnnotation = classAnnotationHolder.getElement().getAnnotation(JsonbPropertyOrder.class);
        return null != propertyOrderAnnotation ? propertyOrderAnnotation.value() : null;
    }

    /**
     * Returns class if {@link ImplementationClass} annotation is present.
     *
     * @param propDesc annotated property
     * @return Class if {@link ImplementationClass} is present otherwise null
     */
    public Class<?> getImplementationClass(PropertyDescriptor propDesc) {
        Optional<ImplementationClass> implementationClassOptional = getAnnotationFromProperty(ImplementationClass.class, propDesc);
        return implementationClassOptional.<Class<?>>map(ImplementationClass::value).orElse(null);
    }

    /**
     * Checks for {@link JsonbSerializer} on a property.
     *
     * @param propDesc property not null
     * @return components info
     */
    public SerializerBinding getSerializerBinding(PropertyDescriptor propDesc) {
        Objects.requireNonNull(propDesc);
        JsonbTypeSerializer typeHandlerAnnotation = getAnnotationFromProperty(JsonbTypeSerializer.class, propDesc).orElseGet(() -> getAnnotationFromPropertyType(propDesc, JsonbTypeSerializer.class));
        if (null == typeHandlerAnnotation) {
            return null;
        }
        final Class<? extends JsonbSerializer> handlerClass = typeHandlerAnnotation.value();
        return runtimeContext.getComponentMatcher().introspectSerializerBinding(handlerClass, null);
    }

    /**
     * Get a @JsonbVisibility annotation from a class or its package.
     *
     * @param type Class to lookup annotation
     * @return Instantiated PropertyVisibilityStrategy if annotation is present
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy(Class<?> type) {
        JsonbVisibility visibilitySpec = locateAnnotation(type.getDeclaredAnnotations(), JsonbVisibility.class);
        if ((null == visibilitySpec) && (null != type.getPackage())) {
            visibilitySpec = locateAnnotation(type.getPackage().getDeclaredAnnotations(), JsonbVisibility.class);
        }
        if (null != visibilitySpec) {
            return ReflectionUtils.createNoArgConstructorInstance(ReflectionUtils.getDefaultConstructor(visibilitySpec.value(), true));
        }
        return runtimeContext.getConfigProperties().getPropertyVisibilityStrategy();
    }

    /**
     * Search for {@link JsonbNumberFormat} annotation on java class.
     *
     * @param classAnnotationHolder class to search not null
     * @return formatter to use
     */
    public JsonbNumberFormatter getJsonbNumberFormat(JsonbAnnotationHolder<Class<?>> classAnnotationHolder) {
        final JsonbNumberFormat numberFormatAnno = locateAnnotation(classAnnotationHolder.getAnnotations(), JsonbNumberFormat.class);
        if (null == numberFormatAnno) {
            return null;
        }
        return new JsonbNumberFormatter(numberFormatAnno.value(), numberFormatAnno.locale());
    }

    private void addIfNotPresent(JsonbAnnotationHolder<?> annotationHolder, Annotation... annoArray) {
        for (Annotation anno : annoArray) {
            if (null == annotationHolder.getAnnotation(anno.annotationType())) {
                annotationHolder.addAnnotation(anno);
            }
        }
    }

    /**
     * Checks for {@link JsonbAdapter} on a property.
     *
     * @param propDesc property not null
     * @return components info
     */
    public AdapterBinding getAdapterBinding(PropertyDescriptor propDesc) {
        Objects.requireNonNull(propDesc);
        JsonbTypeAdapter adapterAnnot = getAnnotationFromProperty(JsonbTypeAdapter.class, propDesc).orElseGet(() -> getAnnotationFromPropertyType(propDesc, JsonbTypeAdapter.class));
        if (null == adapterAnnot) {
            return null;
        }
        return getAdapterBindingFromAnnotation(adapterAnnot, ReflectionUtils.getOptionalRawType(propDesc.getPropertyType()));
    }

    /**
     * Checks for {@link JsonbAdapter} on a type.
     *
     * @param classHolder type not null
     * @return components info
     */
    public AdapterBinding getAdapterBinding(JsonbAnnotationHolder<Class<?>> classHolder) {
        Objects.requireNonNull(classHolder);
        JsonbTypeAdapter adapterAnnot = classHolder.getElement().getAnnotation(JsonbTypeAdapter.class);
        if (null == adapterAnnot) {
            return null;
        }
        return getAdapterBindingFromAnnotation(adapterAnnot, Optional.ofNullable(classHolder.getElement()));
    }

    /**
     * Returns {@link JsonbNumberFormatter} instance if {@link JsonbNumberFormat} annotation is present.
     *
     * @param parameterHolder annotated method parameter
     * @return formatter instance if {@link JsonbNumberFormat} is present otherwise null
     */
    public JsonbNumberFormatter getConstructorNumberFormatter(JsonbAnnotationHolder<Parameter> parameterHolder) {
        JsonbNumberFormat anno = parameterHolder.getAnnotation(JsonbNumberFormat.class);
        if (null != anno) {
            return new JsonbNumberFormatter(anno.value(), anno.locale());
        }
        return null;
    }

    /**
     * Checks for JsonbNillable annotation on a class, its superclasses and interfaces.
     *
     * @param classAnnotationHolder class to search JsonbNillable in.
     * @return true if found
     */
    public boolean isClassNillable(JsonbAnnotationHolder<Class<?>> classAnnotationHolder) {
        final JsonbNillable nillableAnnotation = locateAnnotation(classAnnotationHolder.getAnnotations(), JsonbNillable.class);
        if (null != nillableAnnotation) {
            return nillableAnnotation.value();
        }
        Class<?> type = classAnnotationHolder.getElement();
        if (Optional.class == type || OptionalDouble.class == type || OptionalInt.class == type || OptionalLong.class == type) {
            return true;
        }
        return runtimeContext.getConfigProperties().getConfigNullable();
    }

    private <T extends Annotation> T locateAnnotation(Annotation[] annotationsArray, Class<T> annoType) {
        return AnnotationFinder.findAnnotation(annotationsArray, annoType, new HashSet<>());
    }

    /**
     * Gets a name of property for JSON marshalling.
     * Can be different writeName for same property.
     *
     * @param propDesc property representation - field, getter, setter (not null)
     * @return read name
     */
    public String getJsonbPropertyJsonWriteName(PropertyDescriptor propDesc) {
        Objects.requireNonNull(propDesc);
        return getJsonbPropertyCustomizedName(propDesc, propDesc.getGetterElement());
    }

    /**
     * Creates {@link JsonbDateFormatter} caches formatter instance if possible.
     * For DEFAULT_FORMAT appropriate singleton instances from java.time.format.DateTimeFormatter
     * are used in date converters.
     */
    private JsonbDateFormatter buildJsonbDateFormatter(String dateFormatAnno, String languageTag, PropertyDescriptor propDesc) {
        if (JsonbDateFormat.TIME_IN_MILLIS.equals(dateFormatAnno) || JsonbDateFormat.DEFAULT_FORMAT.equals(dateFormatAnno)) {
            //for epochMillis formatter is not used, for default format singleton instances of DateTimeFormatter
            //are used in the converters
            return new JsonbDateFormatter(dateFormatAnno, languageTag);
        }
        final Optional<Class<?>> maybeRawType = ReflectionUtils.getOptionalRawType(propDesc.getPropertyType());
        final Class<?> rawClass = maybeRawType.orElse(null);
        if (null != rawClass && !TemporalAccessor.class.isAssignableFrom(rawClass) && !Date.class.isAssignableFrom(rawClass) && !Calendar.class.isAssignableFrom(rawClass)) {
            throw new IllegalStateException(MessageBundle.getMessage(ErrorMessageKeys.UNSUPPORTED_DATE_TYPE, rawClass));
        }
        DateTimeFormatterBuilder formatterComposer = new DateTimeFormatterBuilder();
        formatterComposer.appendPattern(dateFormatAnno);
        if (runtimeContext.getConfigProperties().isZeroTimeDefaulting()) {
            formatterComposer.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            formatterComposer.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            formatterComposer.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter dateFormatter = formatterComposer.toFormatter(Locale.forLanguageTag(languageTag));
        return new JsonbDateFormatter(dateFormatter, dateFormatAnno, languageTag);
    }

    private <T extends Annotation> T getMethodAnnotation(Class<T> annoType, JsonbAnnotationHolder<Method> annotationHolder) {
        if (null == annotationHolder) {
            return null;
        }
        return locateAnnotation(annotationHolder.getAnnotations(), annoType);
    }

    /**
     * Search {@link JsonbNumberFormat} on property, if not found looks at annotations declared on property type class.
     *
     * @param propDesc Property to search on.
     * @return Map of {@link JsonbNumberFormatter} instances categorized by their scopes (class, property, getter or setter).
     * If there is no number
     * formatter specified for given property, an empty map would be returned
     */
    public Map<AnnotationTarget, JsonbNumberFormatter> getJsonNumberFormatter(PropertyDescriptor propDesc) {
        Map<AnnotationTarget, JsonbNumberFormatter> dateFormatterMap = new HashMap<>();
        Map<AnnotationTarget, JsonbNumberFormat> transientAnnotationsByTarget = getAnnotationFromPropertyCategorized(JsonbNumberFormat.class, propDesc);
        if (0 != transientAnnotationsByTarget.size()) {
            transientAnnotationsByTarget.forEach((mapKey, anno) -> dateFormatterMap.put(mapKey, new JsonbNumberFormatter(anno.value(), anno.locale())));
        } else {
            final Optional<Class<?>> rawTypeOption = ReflectionUtils.getOptionalRawType(propDesc.getPropertyType());
            if (rawTypeOption.isPresent()) {
                Class<?> resolvedType = rawTypeOption.get();
                if (!Number.class.isAssignableFrom(resolvedType)) {
                    return new HashMap<>();
                }
            }
        }
        JsonbNumberFormat numberFormatterAtClass = locateAnnotation(propDesc.getDeclaringClassElement().getAnnotations(), JsonbNumberFormat.class);
        if (null != numberFormatterAtClass) {
            dateFormatterMap.put(AnnotationTarget.CLASS, new JsonbNumberFormatter(numberFormatterAtClass.value(), numberFormatterAtClass.locale()));
        }
        return dateFormatterMap;
    }

    /**
     * Processes customizations.
     *
     * @param classHolder Element to process.
     * @return Populated {@link ClassSerializationConfig} instance.
     */
    public ClassSerializationConfig inspectCustomization(JsonbAnnotationHolder<Class<?>> classHolder) {
        final ClassCustomizationBuilder formatterComposer = new ClassCustomizationBuilder();
        formatterComposer.setNillable(isClassNillable(classHolder));
        formatterComposer.setDateFormatter(getJsonbDateFormat(classHolder));
        formatterComposer.setNumberFormatter(getJsonbNumberFormat(classHolder));
        formatterComposer.setCreator(getCreator(classHolder.getElement()));
        formatterComposer.setPropertyOrder(getPropertyOrder(classHolder));
        formatterComposer.setAdapterInfo(getAdapterBinding(classHolder));
        formatterComposer.setSerializerBinding(getSerializerBinding(classHolder));
        formatterComposer.setDeserializerBinding(getDeserializerBinding(classHolder));
        formatterComposer.setPropertyVisibilityStrategy(getPropertyVisibilityStrategy(classHolder.getElement()));
        return formatterComposer.buildClassCustomization();
    }

    private <T extends Annotation> T getAnnotationFromPropertyType(PropertyDescriptor propDesc, Class<T> annoType) {
        final Optional<Class<?>> maybeRawType = ReflectionUtils.getOptionalRawType(propDesc.getPropertyType());
        if (!maybeRawType.isPresent()) {
            //will not work for type variable properties, which are bound to class that is annotated.
            return null;
        }
        return locateAnnotation(gatherAnnotations(maybeRawType.get()).getAnnotations(), annoType);
    }

    /**
     * Checks if property is nillable.
     * Looks for {@link JsonbProperty} nillable attribute only.
     * JsonbNillable is checked only for ClassModels.
     *
     * @param propDesc property to search in, not null
     * @return True if property should be serialized when null.
     */
    public Optional<Boolean> isPropertyNillable(PropertyDescriptor propDesc) {
        Objects.requireNonNull(propDesc);
        final Optional<JsonbProperty> propAnnotationOpt = getAnnotationFromProperty(JsonbProperty.class, propDesc);
        return propAnnotationOpt.map(JsonbProperty::nillable);
    }

    /**
     * Gets a name of property for JSON unmarshalling.
     * Can be different from writeName for same property.
     *
     * @param propDesc property representation - field, getter, setter (not null)
     * @return write name
     */
    public String getJsonbPropertyJsonReadName(PropertyDescriptor propDesc) {
        Objects.requireNonNull(propDesc);
        return getJsonbPropertyCustomizedName(propDesc, propDesc.getSetterElement());
    }

    /**
     * An override of {@link #getAnnotationFromProperty(Class, PropertyDescriptor)} in which it returns the results as a map so that the
     * caller can decide which
     * one to be used for read/write operation. Some annotations should have different behaviours based on the scope that
     * they're applied on.
     *
     * @param annoType The annotation class to search
     * @param propDesc        The property to search in
     * @param <T>             Annotation type
     * @return A map of all occurrences of requested annotation for given property. Caller can determine based on
     * {@link AnnotationTarget} that given
     * annotation is specified on what level (Class, Property, Getter or Setter). If no annotation found for given property, an
     * empty map would be
     * returned
     */
    private <T extends Annotation> Map<AnnotationTarget, T> getAnnotationFromPropertyCategorized(Class<T> annoType, PropertyDescriptor propDesc) {
        Map<AnnotationTarget, T> dateFormatterMap = new HashMap<>();
        T propertyAnnotation = getFieldAnnotation(annoType, propDesc.getFieldElement());
        if (null != propertyAnnotation) {
            dateFormatterMap.put(AnnotationTarget.PROPERTY, propertyAnnotation);
        }
        T getterAnn = getMethodAnnotation(annoType, propDesc.getGetterElement());
        if (null != getterAnn) {
            dateFormatterMap.put(AnnotationTarget.GETTER, getterAnn);
        }
        T setterAnn = getMethodAnnotation(annoType, propDesc.getSetterElement());
        if (null != setterAnn) {
            dateFormatterMap.put(AnnotationTarget.SETTER, setterAnn);
        }
        return dateFormatterMap;
    }

    private String getJsonbPropertyCustomizedName(PropertyDescriptor propDesc, JsonbAnnotationHolder<Method> annotationHolder) {
        JsonbProperty propertyAnnotation = getMethodAnnotation(JsonbProperty.class, annotationHolder);
        if (null != propertyAnnotation && !propertyAnnotation.value().isEmpty()) {
            return propertyAnnotation.value();
        }
        //in case of property name getter/setter override field value
        JsonbProperty memberAnnotation = getFieldAnnotation(JsonbProperty.class, propDesc.getFieldElement());
        if (null != memberAnnotation && !memberAnnotation.value().isEmpty()) {
            return memberAnnotation.value();
        }
        return null;
    }

    /**
     * Collect annotations of given class, its interfaces and the package.
     *
     * @param type Class to process.
     * @return Element with class and annotations.
     */
    public JsonbAnnotationHolder<Class<?>> gatherAnnotations(Class<?> type) {
        JsonbAnnotationHolder<Class<?>> classAnnotationHolder = new JsonbAnnotationHolder<>(type);
        if (DefaultSerializers.isKnownType(type)) {
            return classAnnotationHolder;
        }
        for (Class<?> interfaceType : gatherInterfaces(type)) {
            addIfNotPresent(classAnnotationHolder, interfaceType.getDeclaredAnnotations());
        }
        if (!type.isPrimitive() && !type.isArray() && (null != type.getPackage())) {
            addIfNotPresent(classAnnotationHolder, type.getPackage().getAnnotations());
        }
        return classAnnotationHolder;
    }

    /**
     * Checks if property is annotated transient. If JsonbTransient annotation is present on field getter or setter, and other
     * annotation is present
     * on either of it, JsonbException is thrown with message describing collision.
     *
     * @param propDesc The property to inspect if there is any {@link JsonbTransient} annotation defined for it
     * @return Set of {@link AnnotationTarget}s specifying in which scope the {@link JsonbTransient} is applied
     */
    public EnumSet<AnnotationTarget> getJsonbTransientCategorized(PropertyDescriptor propDesc) {
        Objects.requireNonNull(propDesc);
        EnumSet<AnnotationTarget> ignoredTargets = EnumSet.noneOf(AnnotationTarget.class);
        Map<AnnotationTarget, JsonbTransient> transientAnnotationsByTarget = getAnnotationFromPropertyCategorized(JsonbTransient.class, propDesc);
        if (0 < transientAnnotationsByTarget.size()) {
            ignoredTargets.addAll(transientAnnotationsByTarget.keySet());
            return ignoredTargets;
        }
        return ignoredTargets;
    }

    private JsonbInstantiator buildJsonbCreator(Executable executableElement, JsonbInstantiator previousInstantiator, Class<?> type) {
        if (null != previousInstantiator) {
            throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.MULTIPLE_JSONB_CREATORS, type));
        }
        final Parameter[] params = executableElement.getParameters();
        CreatorProfile[] creatorProfiles = new CreatorProfile[params.length];
        int index = 0;
        while (params.length > index) {
            final Parameter param = params[index];
            final JsonbProperty propertyAnnotation = param.getAnnotation(JsonbProperty.class);
            if (null == propertyAnnotation || propertyAnnotation.value().isEmpty()) {
                creatorProfiles[index] = new CreatorProfile(param.getName(), param, runtimeContext);
            } else {
                creatorProfiles[index] = new CreatorProfile(propertyAnnotation.value(), param, runtimeContext);
            }
            index += 1;
        }
        return new JsonbInstantiator(executableElement, creatorProfiles);
    }

    /**
     * Search {@link JsonbDateFormat} on property, if not found looks at annotations declared on property type class.
     *
     * @param propDesc Property to search on.
     * @return Map of {@link JsonbDateFormatter} instances categorized by their scopes (class, property, getter or setter). If
     * there is no date
     * formatter specified for given property, an empty map would be returned
     */
    public Map<AnnotationTarget, JsonbDateFormatter> getJsonbDateFormatCategorized(PropertyDescriptor propDesc) {
        Objects.requireNonNull(propDesc);
        Map<AnnotationTarget, JsonbDateFormatter> dateFormatterMap = new HashMap<>();
        Map<AnnotationTarget, JsonbDateFormat> transientAnnotationsByTarget = getAnnotationFromPropertyCategorized(JsonbDateFormat.class, propDesc);
        if (0 != transientAnnotationsByTarget.size()) {
            transientAnnotationsByTarget.forEach((mapKey, anno) -> dateFormatterMap.put(mapKey, buildJsonbDateFormatter(anno.value(), anno.locale(), propDesc)));
        }
        // No date format on property, try class level
        // if property is not TypeVariable and its class is not date skip it
        final Optional<Class<?>> rawTypeOption = ReflectionUtils.getOptionalRawType(propDesc.getPropertyType());
        if (rawTypeOption.isPresent()) {
            Class<?> resolvedType = rawTypeOption.get();
            if (!(Date.class.isAssignableFrom(resolvedType) || Calendar.class.isAssignableFrom(resolvedType) || TemporalAccessor.class.isAssignableFrom(resolvedType))) {
                return new HashMap<>();
            }
        }
        JsonbDateFormat classDateFormatter = locateAnnotation(propDesc.getDeclaringClassElement().getAnnotations(), JsonbDateFormat.class);
        if (null != classDateFormatter) {
            dateFormatterMap.put(AnnotationTarget.CLASS, buildJsonbDateFormatter(classDateFormatter.value(), classDateFormatter.locale(), propDesc));
        }
        return dateFormatterMap;
    }

    private <T extends Annotation> T getFieldAnnotation(Class<T> annoType, JsonbAnnotationHolder<Field> fieldHolder) {
        if (null == fieldHolder) {
            return null;
        }
        return locateAnnotation(fieldHolder.getAnnotations(), annoType);
    }

    /**
     * Searches for JsonbCreator annotation on constructors and static methods.
     *
     * @param type class to search
     * @return JsonbCreator metadata object
     */
    public JsonbInstantiator getCreator(Class<?> type) {
        JsonbInstantiator instantiator = null;
        Constructor<?>[] constructorsArray = AccessController.doPrivileged((PrivilegedAction<Constructor<?>[]>) type::getDeclaredConstructors);
        for (Constructor<?> candidateConstructor : constructorsArray) {
            final jakarta.json.bind.annotation.JsonbCreator creatorMeta = locateAnnotation(candidateConstructor.getDeclaredAnnotations(), jakarta.json.bind.annotation.JsonbCreator.class);
            if (null != creatorMeta) {
                instantiator = buildJsonbCreator(candidateConstructor, instantiator, type);
            }
        }
        Method[] methodsArray = AccessController.doPrivileged((PrivilegedAction<Method[]>) type::getDeclaredMethods);
        for (Method candidateMethod : methodsArray) {
            final jakarta.json.bind.annotation.JsonbCreator creatorMeta = locateAnnotation(candidateMethod.getDeclaredAnnotations(), jakarta.json.bind.annotation.JsonbCreator.class);
            if (null != creatorMeta && Modifier.isStatic(candidateMethod.getModifiers())) {
                if (!type.equals(candidateMethod.getReturnType())) {
                    throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.INCOMPATIBLE_FACTORY_CREATOR_RETURN_TYPE, candidateMethod, type));
                }
                instantiator = buildJsonbCreator(candidateMethod, instantiator, type);
            }
        }
        if (null == instantiator) {
            instantiator = propsIntrospector.getCreator(constructorsArray);
        }
        return instantiator;
    }

    /**
     * Checks for {@link JsonbDeserializer} on a property.
     *
     * @param propDesc property not null
     * @return components info
     */
    public DeserializerBinding getDeserializerBinding(PropertyDescriptor propDesc) {
        Objects.requireNonNull(propDesc);
        JsonbTypeDeserializer deserializerAnnot = getAnnotationFromProperty(JsonbTypeDeserializer.class, propDesc).orElseGet(() -> getAnnotationFromPropertyType(propDesc, JsonbTypeDeserializer.class));
        if (null == deserializerAnnot) {
            return null;
        }
        final Class<? extends JsonbDeserializer> deserializerType = deserializerAnnot.value();
        return runtimeContext.getComponentMatcher().introspectDeserializerBinding(deserializerType, null);
    }

    /**
     * Creates annotation introspecting component passing {@link JsonbRuntimeContext} inside.
     *
     * @param runtimeContext mandatory
     */
    public JsonbAnnotationIntrospector(JsonbRuntimeContext runtimeContext) {
        Objects.requireNonNull(runtimeContext);
        this.runtimeContext = runtimeContext;
        this.propsIntrospector = ConstructorPropertiesAnnotationIntrospector.forContext(runtimeContext);
    }

    /**
     * Checks for {@link JsonbSerializer} on a type.
     *
     * @param classHolder type not null
     * @return components info
     */
    public SerializerBinding getSerializerBinding(JsonbAnnotationHolder<Class<?>> classHolder) {
        Objects.requireNonNull(classHolder);
        JsonbTypeSerializer typeHandlerAnnotation = classHolder.getElement().getAnnotation(JsonbTypeSerializer.class);
        if (null == typeHandlerAnnotation) {
            return null;
        }
        final Class<? extends JsonbSerializer> handlerClass = typeHandlerAnnotation.value();
        return runtimeContext.getComponentMatcher().introspectSerializerBinding(handlerClass, null);
    }

    /**
     * Search for {@link JsonbDateFormat} annotation on java class and construct {@link JsonbDateFormatter}.
     * If not found looks at annotations declared on property type class.
     *
     * @param classAnnotationHolder class to search not null
     * @return formatter to use
     */
    public JsonbDateFormatter getJsonbDateFormat(JsonbAnnotationHolder<Class<?>> classAnnotationHolder) {
        Objects.requireNonNull(classAnnotationHolder);
        final JsonbDateFormat dateFormatAnno = locateAnnotation(classAnnotationHolder.getAnnotations(), JsonbDateFormat.class);
        if (null == dateFormatAnno) {
            return runtimeContext.getConfigProperties().getConfigDateFormatter();
        }
        return new JsonbDateFormatter(dateFormatAnno.value(), dateFormatAnno.locale());
    }

}

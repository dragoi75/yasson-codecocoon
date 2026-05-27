/*
 * Copyright (c) 2016, 2023 Oracle and/or its affiliates. All rights reserved.
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
import java.util.ListIterator;
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
import jakarta.json.bind.annotation.JsonbSubtype;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.annotation.JsonbTypeInfo;
import jakarta.json.bind.annotation.JsonbTypeSerializer;
import jakarta.json.bind.annotation.JsonbVisibility;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import org.eclipse.yasson.ImplementationClass;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;
import org.eclipse.yasson.internal.model.AnnotationTarget;
import org.eclipse.yasson.internal.model.CreatorModel;
import org.eclipse.yasson.internal.model.JsonbAnnotationContainer;
import org.eclipse.yasson.internal.model.JsonbAnnotationContainer.AnnotationMetadata;
import org.eclipse.yasson.internal.model.JsonbCreator;
import org.eclipse.yasson.internal.model.Property;
import org.eclipse.yasson.internal.model.customization.ClassCustomization;
import org.eclipse.yasson.internal.model.customization.TypeInheritanceConfiguration;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Introspects configuration on classes and their properties by reading annotations.
 */
public class AnnotationIntrospector {

    //    private static final Set<Class<?>> OPTIONALS = Set.of(Optional.class,
    //                                                          OptionalInt.class,
    //                                                          OptionalLong.class,
    //                                                          OptionalDouble.class);
    private final JsonbContext jsonbContext;

    private final ConstructorPropertiesAnnotationIntrospector constructorPropertiesIntrospector;

    private static final Set<Class<? extends Annotation>> REPEATABLE = Set.of(JsonbTypeInfo.class);

    /**
     * Annotations to report exception when used in combination with {@link JsonbTransient}.
     */
    private static final List<Class<? extends Annotation>> TRANSIENT_INCOMPATIBLE = Arrays.asList(JsonbDateFormat.class, JsonbNumberFormat.class, JsonbProperty.class, JsonbTypeAdapter.class, JsonbTypeSerializer.class, JsonbTypeDeserializer.class);

    private <T extends Annotation> T getMethodAnnotation(Class<T> annotationClass, JsonbAnnotationContainer<Method> methodElement) {
        if (null == methodElement) {
            return null;
        }
        return findAnnotation(methodElement.getAnnotations(), annotationClass);
    }

    /**
     * Checks for {@link JsonbAdapter} on a {@link Parameter}.
     *
     * @param parameter parameter not null
     * @return components info
     */
    public AdapterBinding getAdapterBinding(Parameter parameter) {
        Objects.requireNonNull(parameter);
        JsonbTypeAdapter adapter = Optional.ofNullable(parameter.getDeclaredAnnotation(JsonbTypeAdapter.class)).orElseGet(() -> getAnnotationFromParameterType(parameter, JsonbTypeAdapter.class));
        if (null == adapter) {
            return null;
        }
        return getAdapterBindingFromAnnotation(adapter, ReflectionUtils.getOptionalRawType(parameter.getParameterizedType()));
    }

    /**
     * Search for {@link JsonbNumberFormat} annotation on java class.
     *
     * @param clazzElement class to search not null
     * @return formatter to use
     */
    public JsonbNumberFormatter getJsonbNumberFormat(JsonbAnnotationContainer<Class<?>> clazzElement) {
        final JsonbNumberFormat formatAnnotation = findAnnotation(clazzElement.getAnnotations(), JsonbNumberFormat.class);
        if (null == formatAnnotation) {
            return null;
        }
        return new JsonbNumberFormatter(formatAnnotation.value(), formatAnnotation.locale());
    }

    private <T extends Annotation> T findAnnotation(Annotation[] declaredAnnotations, Class<T> annotationClass) {
        return AnnotationFinder.findAnnotation(declaredAnnotations, annotationClass, new HashSet<>());
    }

    private TypeInheritanceConfiguration getPolymorphismConfig(JsonbAnnotationContainer<Class<?>> clsElement, ClassCustomization parentCustomization) {
        TypeInheritanceConfiguration parentPolyConfig = parentCustomization.getPolymorphismConfig();
        LinkedList<AnnotationMetadata<?>> annotations = clsElement.getAnnotations(JsonbTypeInfo.class);
        if (null != parentPolyConfig) {
            if (1 != annotations.size() || !annotations.getFirst().isInherited()) {
                if (1 >= annotations.size()) {
                    if (annotations.isEmpty()) {
                        return TypeInheritanceConfiguration.builder().of(parentPolyConfig).inherited(true).build();
                    }
                } else {
                    throw new JsonbException("Cannot process type information from multiple sources! Sources: " + annotations);
                }
            } else {
                throw new JsonbException("Cannot process type information from multiple sources! Sources: " + parentPolyConfig.getDefinedType().getName() + " and " + annotations.getFirst());
            }
        }
        ListIterator<AnnotationMetadata<?>> listIterator = annotations.listIterator(annotations.size());
        while (listIterator.hasPrevious()) {
            AnnotationMetadata<?> annotationWrapper = listIterator.previous();
            JsonbTypeInfo annotation = (JsonbTypeInfo) annotationWrapper.getAnnotation();
            TypeInheritanceConfiguration.Builder builder = TypeInheritanceConfiguration.builder();
            builder.fieldName(annotation.key()).inherited(annotationWrapper.isInherited()).parentConfig(parentPolyConfig).definedType(annotationWrapper.getDefinedType());
            for (JsonbSubtype subType : annotation.value()) {
                if (!annotationWrapper.getDefinedType().isAssignableFrom(subType.type())) {
                    throw new JsonbException("Defined alias type has to be child of the current type. JsonbSubType on the " + annotationWrapper.getDefinedType().getName() + " defines incorrect alias " + subType);
                }
                builder.alias(subType.type(), subType.alias());
            }
            parentPolyConfig = builder.build();
        }
        checkDuplicityPolymorphicPropertyNames(parentPolyConfig);
        return parentPolyConfig;
    }

    /**
     * Get class interfaces recursively.
     *
     * @param cls Class to process.
     * @return A list of all class interfaces.
     */
    public Set<Class<?>> collectInterfaces(Class<?> cls) {
        Set<Class<?>> collected = new LinkedHashSet<>();
        Queue<Class<?>> toScan = new LinkedList<>(Arrays.asList(cls.getInterfaces()));
        Class<?> nextIfc;
        while ((nextIfc = toScan.poll()) != null) {
            collected.add(nextIfc);
            toScan.addAll(Arrays.asList(nextIfc.getInterfaces()));
        }
        return collected;
    }

    /**
     * Creates annotation introspecting component passing {@link JsonbContext} inside.
     *
     * @param jsonbContext mandatory
     */
    public AnnotationIntrospector(JsonbContext jsonbContext) {
        Objects.requireNonNull(jsonbContext);
        this.jsonbContext = jsonbContext;
        this.constructorPropertiesIntrospector = ConstructorPropertiesAnnotationIntrospector.forContext(jsonbContext);
    }

    //    private void collectParentInterfaceAnnotations(Class<?> currentInterf,
    //                                                   Map<Class<? extends Annotation>, LinkedList<Annotation>> overall) {
    //        Map<Class<? extends Annotation>, LinkedList<Annotation>> parents = new HashMap<>();
    //        for (Class<?> parentInterf : currentInterf.getInterfaces()) {
    //            collectParentInterfaceAnnotations(parentInterf, );
    //            current.entrySet().stream()
    //                    .filter(entry -> parents.containsKey(entry.getKey()) || REPEATABLE.contains(entry.getKey()))
    //                    .peek(entry -> {
    //                        if (parents.containsKey(entry.getKey())) {
    //                            throw new JsonbException("CHANGE THIS EXCEPTION");
    //                        }
    //                    })
    //                    .forEach(entry -> {
    //                        parents.computeIfAbsent(entry.getKey(), aClass -> new LinkedList<>()).addAll(entry.getValue());
    //                        map.computeIfAbsent(entry.getKey(), aClass -> new LinkedList<>()).addAll(entry.getValue());
    //                    });
    //        }
    //        if (currentInterf.isInterface()) {
    //            for (Annotation annotation : currentInterf.getDeclaredAnnotations()) {
    //                map.computeIfAbsent(annotation.annotationType(), aClass -> new LinkedList<>()).add(annotation);
    //            }
    //        }
    //        return map;
    //    }
    private void addIfNotPresent(JsonbAnnotationContainer<?> element, Class<?> definedType, Annotation... annotations) {
        for (Annotation annotation : annotations) {
            if (element.getAnnotation(annotation.annotationType()).isEmpty() || REPEATABLE.contains(annotation.annotationType())) {
                element.addAnnotation(annotation, true, definedType);
            }
        }
    }

    /**
     * Checks for {@link JsonbAdapter} on a type.
     *
     * @param clsElement type not null
     * @return components info
     */
    public AdapterBinding getAdapterBinding(JsonbAnnotationContainer<Class<?>> clsElement) {
        Objects.requireNonNull(clsElement);
        JsonbTypeAdapter adapterAnnotation = clsElement.getElement().getAnnotation(JsonbTypeAdapter.class);
        if (null == adapterAnnotation) {
            return null;
        }
        return getAdapterBindingFromAnnotation(adapterAnnotation, Optional.ofNullable(clsElement.getElement()));
    }

    /**
     * Searches for JsonbCreator annotation on constructors and static methods.
     *
     * @param clazz                  class to search
     * @param propertyNamingStrategy The naming strategy to use for the ${@code JsonbConstructor} annotation,
     *                               if set and no {@code JsonbProperty} annotations are present.
     * @return JsonbCreator metadata object
     */
    public JsonbCreator getCreator(Class<?> clazz, PropertyNamingStrategy propertyNamingStrategy) {
        JsonbCreator jsonbCreator = null;
        Constructor<?>[] declaredConstructors = AccessController.doPrivileged((PrivilegedAction<Constructor<?>[]>) clazz::getDeclaredConstructors);
        for (Constructor<?> constructor : declaredConstructors) {
            final jakarta.json.bind.annotation.JsonbCreator annot = findAnnotation(constructor.getDeclaredAnnotations(), jakarta.json.bind.annotation.JsonbCreator.class);
            if (null != annot) {
                jsonbCreator = createJsonbCreator(constructor, jsonbCreator, clazz, propertyNamingStrategy);
            }
        }
        Method[] declaredMethods = AccessController.doPrivileged((PrivilegedAction<Method[]>) clazz::getDeclaredMethods);
        for (Method method : declaredMethods) {
            final jakarta.json.bind.annotation.JsonbCreator annot = findAnnotation(method.getDeclaredAnnotations(), jakarta.json.bind.annotation.JsonbCreator.class);
            if (null != annot && Modifier.isStatic(method.getModifiers())) {
                if (!clazz.equals(method.getReturnType())) {
                    throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INCOMPATIBLE_FACTORY_CREATOR_RETURN_TYPE, method, clazz));
                }
                jsonbCreator = createJsonbCreator(method, jsonbCreator, clazz, propertyNamingStrategy);
            }
        }
        if (null == jsonbCreator) {
            jsonbCreator = ClassMultiReleaseExtension.findCreator(clazz, declaredConstructors, this, propertyNamingStrategy);
            if (null == jsonbCreator) {
                jsonbCreator = constructorPropertiesIntrospector.getCreator(declaredConstructors);
            }
        }
        return jsonbCreator;
    }

    private Map<Class<? extends Annotation>, LinkedList<AnnotationMetadata<?>>> collectInterfaceAnnotations(Class<?> currentInterf, Class<?> processed) {
        Map<Class<? extends Annotation>, LinkedList<AnnotationMetadata<?>>> map = new HashMap<>();
        if (!currentInterf.equals(processed)) {
            for (Annotation annotation : currentInterf.getDeclaredAnnotations()) {
                map.computeIfAbsent(annotation.annotationType(), aClass -> new LinkedList<>()).add(new AnnotationMetadata<>(annotation, true, currentInterf));
            }
        }
        Map<Class<? extends Annotation>, LinkedList<AnnotationMetadata<?>>> parents = new HashMap<>();
        for (Class<?> parentInterf : currentInterf.getInterfaces()) {
            Map<Class<? extends Annotation>, LinkedList<AnnotationMetadata<?>>> current = collectInterfaceAnnotations(parentInterf, processed);
            current.entrySet().stream().filter(entry -> !parents.containsKey(entry.getKey()) || REPEATABLE.contains(entry.getKey())).peek(entry -> {
                if (parents.containsKey(entry.getKey())) {
                    throw new JsonbException("Cannot process annotation " + entry.getKey().getName() + " from multiple " + "parallel sources");
                }
            }).forEach(entry -> {
                parents.computeIfAbsent(entry.getKey(), aClass -> new LinkedList<>()).addAll(entry.getValue());
                map.computeIfAbsent(entry.getKey(), aClass -> new LinkedList<>()).addAll(entry.getValue());
            });
        }
        return map;
    }

    /**
     * Returns {@link JsonbDateFormatter} instance if {@link JsonbDateFormat} annotation is present.
     *
     * @param param annotated method parameter
     * @return formatter instance if {@link JsonbDateFormat} is present otherwise null
     */
    public JsonbDateFormatter getConstructorDateFormatter(JsonbAnnotationContainer<Parameter> param) {
        return param.getAnnotation(JsonbDateFormat.class).map(annotation -> new JsonbDateFormatter(DateTimeFormatter.ofPattern(annotation.value(), Locale.forLanguageTag(annotation.locale())), annotation.value(), annotation.locale())).orElse(null);
    }

    /**
     * Gets an annotation from first resolved annotation in a property in this order:
     * <p>1. Field, 2. Getter, 3 Setter.</p>
     * First found overrides other.
     *
     * @param annotationClass Annotation class to search for
     * @param property        property to search in
     * @param <T>             Annotation type
     * @return Annotation if found, null otherwise
     */
    private <T extends Annotation> Optional<T> getAnnotationFromProperty(Class<T> annotationClass, Property property) {
        T fieldAnnotation = getFieldAnnotation(annotationClass, property.getFieldElement());
        if (null != fieldAnnotation) {
            return Optional.of(fieldAnnotation);
        }
        T getterAnnotation = getMethodAnnotation(annotationClass, property.getGetterElement());
        if (null != getterAnnotation) {
            return Optional.of(getterAnnotation);
        }
        T setterAnnotation = getMethodAnnotation(annotationClass, property.getSetterElement());
        if (null != setterAnnotation) {
            return Optional.of(setterAnnotation);
        }
        return Optional.empty();
    }

    /**
     * Processes customizations.
     *
     * @param clsElement             Element to process.
     * @param propertyNamingStrategy The naming strategy to use for the ${@code JsonbConstructor} annotation,
     *                               if set and no {@code JsonbProperty} annotations are present.
     * @return Populated {@link ClassCustomization} instance.
     */
    public ClassCustomization introspectCustomization(JsonbAnnotationContainer<Class<?>> clsElement, ClassCustomization parentCustomization, PropertyNamingStrategy propertyNamingStrategy) {
        return ClassCustomization.builder().nillable(isClassNillable(clsElement)).dateTimeFormatter(getJsonbDateFormat(clsElement)).numberFormatter(getJsonbNumberFormat(clsElement)).creator(getCreator(clsElement.getElement(), propertyNamingStrategy)).propertyOrder(getPropertyOrder(clsElement)).adapterBinding(getAdapterBinding(clsElement)).serializerBinding(getSerializerBinding(clsElement)).deserializerBinding(getDeserializerBinding(clsElement)).propertyVisibilityStrategy(getPropertyVisibilityStrategy(clsElement.getElement())).polymorphismConfig(getPolymorphismConfig(clsElement, parentCustomization)).build();
    }

    /**
     * Search {@link JsonbDateFormat} on property, if not found looks at annotations declared on property type class.
     *
     * @param property Property to search on.
     * @return Map of {@link JsonbDateFormatter} instances categorized by their scopes (class, property, getter or setter). If
     * there is no date
     * formatter specified for given property, an empty map would be returned
     */
    public Map<AnnotationTarget, JsonbDateFormatter> getJsonbDateFormatCategorized(Property property) {
        Objects.requireNonNull(property);
        Map<AnnotationTarget, JsonbDateFormatter> result = new HashMap<>();
        Map<AnnotationTarget, JsonbDateFormat> annotationFromPropertyCategorized = getAnnotationFromPropertyCategorized(JsonbDateFormat.class, property);
        if (0 != annotationFromPropertyCategorized.size()) {
            annotationFromPropertyCategorized.forEach((key, annotation) -> result.put(key, createJsonbDateFormatter(annotation.value(), annotation.locale(), property)));
        }
        // No date format on property, try class level
        // if property is not TypeVariable and its class is not date skip it
        final Optional<Class<?>> propertyRawTypeOptional = ReflectionUtils.getOptionalRawType(property.getPropertyType());
        if (propertyRawTypeOptional.isPresent()) {
            Class<?> rawType = propertyRawTypeOptional.get();
            if (!(Date.class.isAssignableFrom(rawType) || Calendar.class.isAssignableFrom(rawType) || TemporalAccessor.class.isAssignableFrom(rawType))) {
                return new HashMap<>();
            }
        }
        JsonbDateFormat classLevelDateFormatter = findAnnotation(property.getDeclaringClassElement().getAnnotations(), JsonbDateFormat.class);
        if (null != classLevelDateFormatter) {
            result.put(AnnotationTarget.CLASS, createJsonbDateFormatter(classLevelDateFormatter.value(), classLevelDateFormatter.locale(), property));
        }
        return result;
    }

    private <T extends Annotation> void collectFromInterfaces(Class<T> annotationClass, Class<?> clazz, Map<Class<?>, T> collectedAnnotations) {
        for (Class<?> interfaceClass : clazz.getInterfaces()) {
            T annotation = findAnnotation(interfaceClass.getDeclaredAnnotations(), annotationClass);
            if (null != annotation) {
                collectedAnnotations.put(interfaceClass, annotation);
            }
            collectFromInterfaces(annotationClass, interfaceClass, collectedAnnotations);
        }
    }

    /**
     * Finds annotations incompatible with {@link JsonbTransient} annotation.
     *
     * @param target target to check
     */
    public void checkTransientIncompatible(JsonbAnnotationContainer<?> target) {
        if (null == target) {
            return;
        }
        for (Class<? extends Annotation> ann : TRANSIENT_INCOMPATIBLE) {
            Annotation annotation = findAnnotation(target.getAnnotations(), ann);
            if (null != annotation) {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.JSONB_TRANSIENT_WITH_OTHER_ANNOTATIONS));
            }
        }
    }

    /**
     * Search {@link JsonbNumberFormat} on property, if not found looks at annotations declared on property type class.
     *
     * @param property Property to search on.
     * @return Map of {@link JsonbNumberFormatter} instances categorized by their scopes (class, property, getter or setter).
     * If there is no number
     * formatter specified for given property, an empty map would be returned
     */
    public Map<AnnotationTarget, JsonbNumberFormatter> getJsonNumberFormatter(Property property) {
        Map<AnnotationTarget, JsonbNumberFormatter> result = new HashMap<>();
        Map<AnnotationTarget, JsonbNumberFormat> annotationFromPropertyCategorized = getAnnotationFromPropertyCategorized(JsonbNumberFormat.class, property);
        //        if (annotationFromPropertyCategorized.size() == 0) {
        //            final Optional<Class<?>> propertyRawTypeOptional = ReflectionUtils.getOptionalRawType(property
        //            .getPropertyType());
        //            if (propertyRawTypeOptional.isPresent()) {
        //                Class<?> rawType = propertyRawTypeOptional.get();
        //                if (!Number.class.isAssignableFrom(rawType)) {
        //                    return new HashMap<>();
        //                }
        //            }
        //        } else {
        //            annotationFromPropertyCategorized.forEach((key, annotation) -> result
        //                    .put(key, new JsonbNumberFormatter(annotation.value(), annotation.locale())));
        //        }
        annotationFromPropertyCategorized.forEach((key, annotation) -> result.put(key, new JsonbNumberFormatter(annotation.value(), annotation.locale())));
        JsonbNumberFormat classLevelNumberFormatter = findAnnotation(property.getDeclaringClassElement().getAnnotations(), JsonbNumberFormat.class);
        if (null != classLevelNumberFormatter) {
            result.put(AnnotationTarget.CLASS, new JsonbNumberFormatter(classLevelNumberFormatter.value(), classLevelNumberFormatter.locale()));
        }
        return result;
    }

    JsonbCreator createJsonbCreator(Executable executable, JsonbCreator existing, Class<?> clazz, PropertyNamingStrategy propertyNamingStrategy) {
        if (null != existing) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.MULTIPLE_JSONB_CREATORS, clazz));
        }
        final Parameter[] parameters = executable.getParameters();
        CreatorModel[] creatorModels = new CreatorModel[parameters.length];
        int i = 0;
        while (parameters.length > i) {
            final Parameter parameter = parameters[i];
            final JsonbProperty jsonbPropertyAnnotation = parameter.getAnnotation(JsonbProperty.class);
            if (null == jsonbPropertyAnnotation || jsonbPropertyAnnotation.value().isEmpty()) {
                final String translatedParameterName = propertyNamingStrategy.translateName(parameter.getName());
                creatorModels[i] = new CreatorModel(translatedParameterName, parameter, executable, jsonbContext);
            } else {
                creatorModels[i] = new CreatorModel(jsonbPropertyAnnotation.value(), parameter, executable, jsonbContext);
            }
            i += 1;
        }
        return new JsonbCreator(executable, creatorModels);
    }

    /**
     * Checks for JsonbNillable annotation on a class, its superclasses and interfaces.
     *
     * @param clazzElement class to search JsonbNillable in.
     * @return true if found
     */
    public boolean isClassNillable(JsonbAnnotationContainer<Class<?>> clazzElement) {
        final JsonbNillable jsonbNillable = findAnnotation(clazzElement.getAnnotations(), JsonbNillable.class);
        if (null != jsonbNillable) {
            return jsonbNillable.value();
        }
        Class<?> clazz = clazzElement.getElement();
        if (Optional.class == clazz || OptionalDouble.class == clazz || OptionalInt.class == clazz || OptionalLong.class == clazz) {
            return true;
        }
        return jsonbContext.getConfigProperties().getConfigNullable();
    }

    public boolean requiredParameters(Executable executable, JsonbAnnotationContainer<Parameter> annotated) {
        return jsonbContext.getConfigProperties().hasRequiredCreatorParameters();
        //        if (OPTIONALS.contains(annotated.getElement().getType())) {
        //            return false;
        //        }
        //        return annotated.getAnnotation(JsonbRequired.class)
        //                .or(() -> Optional.ofNullable(executable.getAnnotation(JsonbRequired.class)))
        //                .map(JsonbRequired::value)
        //                .orElseGet(() -> jsonbContext.getConfigProperties().hasRequiredCreatorParameters());
    }

    /**
     * Checks for {@link JsonbDeserializer} on a property.
     *
     * @param property property not null
     * @return components info
     */
    public DeserializerBinding getDeserializerBinding(Property property) {
        Objects.requireNonNull(property);
        JsonbTypeDeserializer deserializerAnnotation = getAnnotationFromProperty(JsonbTypeDeserializer.class, property).orElseGet(() -> getAnnotationFromPropertyType(property, JsonbTypeDeserializer.class));
        if (null == deserializerAnnotation) {
            return null;
        }
        final Class<? extends JsonbDeserializer> deserializerClass = deserializerAnnotation.value();
        return jsonbContext.getComponentMatcher().introspectDeserializerBinding(deserializerClass, null);
    }

    /**
     * Checks for {@link JsonbDeserializer} on a type.
     *
     * @param clsElement type not null
     * @return components info
     */
    public DeserializerBinding getDeserializerBinding(JsonbAnnotationContainer<Class<?>> clsElement) {
        Objects.requireNonNull(clsElement);
        JsonbTypeDeserializer deserializerAnnotation = clsElement.getElement().getAnnotation(JsonbTypeDeserializer.class);
        if (null == deserializerAnnotation) {
            return null;
        }
        final Class<? extends JsonbDeserializer> deserializerClass = deserializerAnnotation.value();
        return jsonbContext.getComponentMatcher().introspectDeserializerBinding(deserializerClass, null);
    }

    private <T extends Annotation> T getAnnotationFromPropertyType(Property property, Class<T> annotationClass) {
        final Optional<Class<?>> optionalRawType = ReflectionUtils.getOptionalRawType(property.getPropertyType());
        if (!optionalRawType.isPresent()) {
            //will not work for type variable properties, which are bound to class that is annotated.
            return null;
        }
        return findAnnotation(collectAnnotations(optionalRawType.get()).getAnnotations(), annotationClass);
    }

    /**
     * An override of {@link #getAnnotationFromProperty(Class, Property)} in which it returns the results as a map so that the
     * caller can decide which
     * one to be used for read/write operation. Some annotations should have different behaviours based on the scope that
     * they're applied on.
     *
     * @param annotationClass The annotation class to search
     * @param property        The property to search in
     * @param <T>             Annotation type
     * @return A map of all occurrences of requested annotation for given property. Caller can determine based on
     * {@link AnnotationTarget} that given
     * annotation is specified on what level (Class, Property, Getter or Setter). If no annotation found for given property, an
     * empty map would be
     * returned
     */
    private <T extends Annotation> Map<AnnotationTarget, T> getAnnotationFromPropertyCategorized(Class<T> annotationClass, Property property) {
        Map<AnnotationTarget, T> result = new HashMap<>();
        T fieldAnnotation = getFieldAnnotation(annotationClass, property.getFieldElement());
        if (null != fieldAnnotation) {
            result.put(AnnotationTarget.PROPERTY, fieldAnnotation);
        }
        T getterAnnotation = getMethodAnnotation(annotationClass, property.getGetterElement());
        if (null != getterAnnotation) {
            result.put(AnnotationTarget.GETTER, getterAnnotation);
        }
        T setterAnnotation = getMethodAnnotation(annotationClass, property.getSetterElement());
        if (null != setterAnnotation) {
            result.put(AnnotationTarget.SETTER, setterAnnotation);
        }
        return result;
    }

    /**
     * Collect annotations of given class, its interfaces and the package.
     *
     * @param clazz Class to process.
     * @return Element with class and annotations.
     */
    public JsonbAnnotationContainer<Class<?>> collectAnnotations(Class<?> clazz) {
        JsonbAnnotationContainer<Class<?>> classElement = new JsonbAnnotationContainer<>(clazz);
        if (BuiltInTypes.isKnownType(clazz)) {
            return classElement;
        }
        Map<Class<? extends Annotation>, LinkedList<AnnotationMetadata<?>>> interfaceAnnotations = collectInterfaceAnnotations(clazz, clazz);
        for (LinkedList<AnnotationMetadata<?>> wrappers : interfaceAnnotations.values()) {
            for (AnnotationMetadata<?> wrapper : wrappers) {
                if (classElement.getAnnotation(wrapper.getAnnotation().annotationType()).isEmpty() || REPEATABLE.contains(wrapper.getAnnotation().annotationType())) {
                    classElement.addAnnotationWrapper(wrapper);
                }
            }
        }
        if (!clazz.isPrimitive() && !clazz.isArray() && (null != clazz.getPackage())) {
            addIfNotPresent(classElement, null, clazz.getPackage().getAnnotations());
        }
        return classElement;
    }

    /**
     * Checks for {@link JsonbDeserializer} on a {@link Parameter}.
     *
     * @param parameter parameter not null
     * @return components info
     */
    public DeserializerBinding<?> getDeserializerBinding(Parameter parameter) {
        Objects.requireNonNull(parameter);
        JsonbTypeDeserializer deserializerAnnotation = Optional.ofNullable(parameter.getDeclaredAnnotation(JsonbTypeDeserializer.class)).orElseGet(() -> getAnnotationFromParameterType(parameter, JsonbTypeDeserializer.class));
        if (null == deserializerAnnotation) {
            return null;
        }
        final Class<? extends JsonbDeserializer> deserializerClass = deserializerAnnotation.value();
        return jsonbContext.getComponentMatcher().introspectDeserializerBinding(deserializerClass, null);
    }

    /**
     * Checks if property is nillable.
     * Looks for {@link JsonbProperty} nillable attribute only.
     * JsonbNillable is checked only for ClassModels.
     *
     * @param property property to search in, not null
     * @return True if property should be serialized when null.
     */
    public Optional<Boolean> isPropertyNillable(Property property) {
        Objects.requireNonNull(property);
        Optional<JsonbNillable> nillable = getAnnotationFromProperty(JsonbNillable.class, property);
        if (nillable.isPresent()) {
            return nillable.map(JsonbNillable::value);
        }
        final Optional<JsonbProperty> jsonbProperty = getAnnotationFromProperty(JsonbProperty.class, property);
        return jsonbProperty.map(JsonbProperty::nillable);
    }

    /**
     * Gets a name of property for JSON unmarshalling.
     * Can be different from writeName for same property.
     *
     * @param property property representation - field, getter, setter (not null)
     * @return write name
     */
    public String getJsonbPropertyJsonReadName(Property property) {
        Objects.requireNonNull(property);
        return getJsonbPropertyCustomizedName(property, property.getSetterElement());
    }

    /**
     * Returns class if {@link ImplementationClass} annotation is present.
     *
     * @param property annotated property
     * @return Class if {@link ImplementationClass} is present otherwise null
     */
    public Class<?> getImplementationClass(Property property) {
        Optional<ImplementationClass> annotationFromProperty = getAnnotationFromProperty(ImplementationClass.class, property);
        return annotationFromProperty.<Class<?>>map(ImplementationClass::value).orElse(null);
    }

    private <T extends Annotation> T getAnnotationFromParameterType(Parameter parameter, Class<T> annotationClass) {
        final Optional<Class<?>> optionalRawType = ReflectionUtils.getOptionalRawType(parameter.getParameterizedType());
        //will not work for type variable properties, which are bound to class that is annotated.
        return optionalRawType.map(aClass -> findAnnotation(collectAnnotations(aClass).getAnnotations(), annotationClass)).orElse(null);
    }

    /**
     * Checks for {@link JsonbPropertyOrder} annotation.
     *
     * @param clazzElement class to search on
     * @return ordered properties names or null if not found
     */
    public String[] getPropertyOrder(JsonbAnnotationContainer<Class<?>> clazzElement) {
        final JsonbPropertyOrder jsonbPropertyOrder = clazzElement.getElement().getAnnotation(JsonbPropertyOrder.class);
        return null != jsonbPropertyOrder ? jsonbPropertyOrder.value() : null;
    }

    /**
     * Returns {@link JsonbNumberFormatter} instance if {@link JsonbNumberFormat} annotation is present.
     *
     * @param param annotated method parameter
     * @return formatter instance if {@link JsonbNumberFormat} is present otherwise null
     */
    public JsonbNumberFormatter getConstructorNumberFormatter(JsonbAnnotationContainer<Parameter> param) {
        return param.getAnnotation(JsonbNumberFormat.class).map(annotation -> new JsonbNumberFormatter(annotation.value(), annotation.locale())).orElse(null);
    }

    /**
     * Checks for {@link JsonbAdapter} on a property.
     *
     * @param property property not null
     * @return components info
     */
    public AdapterBinding getAdapterBinding(Property property) {
        Objects.requireNonNull(property);
        JsonbTypeAdapter adapterAnnotation = getAnnotationFromProperty(JsonbTypeAdapter.class, property).orElseGet(() -> getAnnotationFromPropertyType(property, JsonbTypeAdapter.class));
        if (null == adapterAnnotation) {
            return null;
        }
        return getAdapterBindingFromAnnotation(adapterAnnotation, ReflectionUtils.getOptionalRawType(property.getPropertyType()));
    }

    /**
     * Gets a name of property for JSON marshalling.
     * Can be different writeName for same property.
     *
     * @param property property representation - field, getter, setter (not null)
     * @return read name
     */
    public String getJsonbPropertyJsonWriteName(Property property) {
        Objects.requireNonNull(property);
        return getJsonbPropertyCustomizedName(property, property.getGetterElement());
    }

    /**
     * Checks for {@link JsonbSerializer} on a type.
     *
     * @param clsElement type not null
     * @return components info
     */
    public SerializerBinding getSerializerBinding(JsonbAnnotationContainer<Class<?>> clsElement) {
        Objects.requireNonNull(clsElement);
        JsonbTypeSerializer serializerAnnotation = clsElement.getElement().getAnnotation(JsonbTypeSerializer.class);
        if (null == serializerAnnotation) {
            return null;
        }
        final Class<? extends JsonbSerializer> serializerClass = serializerAnnotation.value();
        return jsonbContext.getComponentMatcher().introspectSerializerBinding(serializerClass, null);
    }

    /**
     * Checks for {@link JsonbSerializer} on a property.
     *
     * @param property property not null
     * @return components info
     */
    public SerializerBinding getSerializerBinding(Property property) {
        Objects.requireNonNull(property);
        JsonbTypeSerializer serializerAnnotation = getAnnotationFromProperty(JsonbTypeSerializer.class, property).orElseGet(() -> getAnnotationFromPropertyType(property, JsonbTypeSerializer.class));
        if (null == serializerAnnotation) {
            return null;
        }
        final Class<? extends JsonbSerializer> serializerClass = serializerAnnotation.value();
        return jsonbContext.getComponentMatcher().introspectSerializerBinding(serializerClass, null);
    }

    private AdapterBinding getAdapterBindingFromAnnotation(JsonbTypeAdapter adapterAnnotation, Optional<Class<?>> expectedClass) {
        final Class<? extends JsonbAdapter> adapterClass = adapterAnnotation.value();
        final AdapterBinding adapterBinding = jsonbContext.getComponentMatcher().introspectAdapterBinding(adapterClass, null);
        if (expectedClass.isPresent() && !(ReflectionUtils.getRawType(adapterBinding.getBindingType()).isAssignableFrom(expectedClass.get()))) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.ADAPTER_INCOMPATIBLE, adapterBinding.getBindingType(), expectedClass.get()));
        }
        return adapterBinding;
    }

    /**
     * Creates {@link JsonbDateFormatter} caches formatter instance if possible.
     * For DEFAULT_FORMAT appropriate singleton instances from java.time.format.DateTimeFormatter
     * are used in date converters.
     */
    private JsonbDateFormatter createJsonbDateFormatter(String format, String locale, Property property) {
        if (JsonbDateFormat.TIME_IN_MILLIS.equals(format) || JsonbDateFormat.DEFAULT_FORMAT.equals(format)) {
            //for epochMillis formatter is not used, for default format singleton instances of DateTimeFormatter
            //are used in the converters
            return new JsonbDateFormatter(format, locale);
        }
        final Optional<Class<?>> optionalRawType = ReflectionUtils.getOptionalRawType(property.getPropertyType());
        final Class<?> propertyRawType = optionalRawType.orElse(null);
        if (null != propertyRawType && !TemporalAccessor.class.isAssignableFrom(propertyRawType) && !Date.class.isAssignableFrom(propertyRawType) && !Calendar.class.isAssignableFrom(propertyRawType)) {
            throw new IllegalStateException(MessageBundle.getMessage(MessageKeyConstants.UNSUPPORTED_DATE_TYPE, propertyRawType));
        }
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendPattern(format);
        if (jsonbContext.getConfigProperties().isZeroTimeDefaulting()) {
            builder.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            builder.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            builder.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter dateTimeFormatter = builder.toFormatter(Locale.forLanguageTag(locale));
        return new JsonbDateFormatter(dateTimeFormatter, format, locale);
    }

    /**
     * Get a @JsonbVisibility annotation from a class or its package.
     *
     * @param clazz Class to lookup annotation
     * @return Instantiated PropertyVisibilityStrategy if annotation is present
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy(Class<?> clazz) {
        JsonbVisibility visibilityAnnotation = findAnnotation(clazz.getDeclaredAnnotations(), JsonbVisibility.class);
        if ((null == visibilityAnnotation) && (null != clazz.getPackage())) {
            visibilityAnnotation = findAnnotation(clazz.getPackage().getDeclaredAnnotations(), JsonbVisibility.class);
        }
        if (null != visibilityAnnotation) {
            return ReflectionUtils.createNoArgConstructorInstance(ReflectionUtils.getDefaultConstructor(visibilityAnnotation.value(), true));
        }
        return jsonbContext.getConfigProperties().getPropertyVisibilityStrategy();
    }

    /**
     * Checks if property is annotated transient. If JsonbTransient annotation is present on field getter or setter, and other
     * annotation is present
     * on either of it, JsonbException is thrown with message describing collision.
     *
     * @param property The property to inspect if there is any {@link JsonbTransient} annotation defined for it
     * @return Set of {@link AnnotationTarget}s specifying in which scope the {@link JsonbTransient} is applied
     */
    public EnumSet<AnnotationTarget> getJsonbTransientCategorized(Property property) {
        Objects.requireNonNull(property);
        EnumSet<AnnotationTarget> transientTarget = EnumSet.noneOf(AnnotationTarget.class);
        Map<AnnotationTarget, JsonbTransient> annotationFromPropertyCategorized = getAnnotationFromPropertyCategorized(JsonbTransient.class, property);
        if (0 < annotationFromPropertyCategorized.size()) {
            transientTarget.addAll(annotationFromPropertyCategorized.keySet());
            return transientTarget;
        }
        return transientTarget;
    }

    private void checkDuplicityPolymorphicPropertyNames(TypeInheritanceConfiguration typeInheritanceConfiguration) {
        if (null == typeInheritanceConfiguration) {
            return;
        }
        Map<String, TypeInheritanceConfiguration> keyNames = new HashMap<>();
        TypeInheritanceConfiguration current = typeInheritanceConfiguration;
        while (null != current) {
            String fieldName = current.getFieldName();
            if (keyNames.containsKey(fieldName)) {
                TypeInheritanceConfiguration conflicting = keyNames.get(fieldName);
                throw new JsonbException("One polymorphic chain cannot have two conflicting property names. " + "Polymorphic type defined on the type " + conflicting.getDefinedType().getName() + " and " + current.getDefinedType().getName() + " have conflicting property name");
            }
            keyNames.put(fieldName, current);
            current = current.getParentConfig();
        }
    }

    private String getJsonbPropertyCustomizedName(Property property, JsonbAnnotationContainer<Method> methodElement) {
        JsonbProperty methodAnnotation = getMethodAnnotation(JsonbProperty.class, methodElement);
        if (null != methodAnnotation && !methodAnnotation.value().isEmpty()) {
            return methodAnnotation.value();
        }
        //in case of property name getter/setter override field value
        JsonbProperty fieldAnnotation = getFieldAnnotation(JsonbProperty.class, property.getFieldElement());
        if (null != fieldAnnotation && !fieldAnnotation.value().isEmpty()) {
            return fieldAnnotation.value();
        }
        return null;
    }

    private <T extends Annotation> T getFieldAnnotation(Class<T> annotationClass, JsonbAnnotationContainer<Field> fieldElement) {
        if (null == fieldElement) {
            return null;
        }
        return findAnnotation(fieldElement.getAnnotations(), annotationClass);
    }

    /**
     * Search for {@link JsonbDateFormat} annotation on java class and construct {@link JsonbDateFormatter}.
     * If not found looks at annotations declared on property type class.
     *
     * @param clazzElement class to search not null
     * @return formatter to use
     */
    public JsonbDateFormatter getJsonbDateFormat(JsonbAnnotationContainer<Class<?>> clazzElement) {
        Objects.requireNonNull(clazzElement);
        final JsonbDateFormat format = findAnnotation(clazzElement.getAnnotations(), JsonbDateFormat.class);
        if (null == format) {
            return jsonbContext.getConfigProperties().getConfigDateFormatter();
        }
        return new JsonbDateFormatter(format.value(), format.locale());
    }

}

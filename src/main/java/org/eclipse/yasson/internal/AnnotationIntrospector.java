/*
 * Copyright (c) 2016, 2022 Oracle and/or its affiliates. All rights reserved.
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
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;

import org.eclipse.yasson.ImplementationClass;
import org.eclipse.yasson.internal.components.AdapterBindingInfo;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.components.JsonbSerializerBinding;
import org.eclipse.yasson.internal.model.AnnotationTarget;
import org.eclipse.yasson.internal.model.CreatorModel;
import org.eclipse.yasson.internal.model.JsonbAnnotatedElement;
import org.eclipse.yasson.internal.model.JsonbAnnotatedElement.AnnotationWrapper;
import org.eclipse.yasson.internal.model.JsonbCreator;
import org.eclipse.yasson.internal.model.Property;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.model.customization.TypeInheritanceSettings;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * Introspects configuration on classes and their properties by reading annotations.
 */
public class AnnotationIntrospector {

    //    private static final Set<Class<?>> OPTIONALS = Set.of(Optional.class,
    //                                                          OptionalInt.class,
    //                                                          OptionalLong.class,
    //                                                          OptionalDouble.class);

    private final JsonBindingContext jsonbContext;
    private final ConstructorPropertiesAnnotationIntrospector constructorPropertiesIntrospector;

    private static final Set<Class<? extends Annotation>> REPEATABLE = Set.of(JsonbTypeInfo.class);

    /**
     * Annotations to report exception when used in combination with {@link JsonbTransient}.
     */
    private static final List<Class<? extends Annotation>> TRANSIENT_INCOMPATIBLE =
            Arrays.asList(JsonbDateFormat.class, JsonbNumberFormat.class, JsonbProperty.class,
                          JsonbTypeAdapter.class, JsonbTypeSerializer.class, JsonbTypeDeserializer.class);

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
     * Checks for {@link JsonbDeserializer} on a {@link Parameter}.
     *
     * @param parameter parameter not null
     * @return components info
     */
    public DeserializerBinding<?> getDeserializerBinding(Parameter parameter) {
        Objects.requireNonNull(parameter);
        JsonbTypeDeserializer deserializerAnnotation =
                Optional.ofNullable(parameter.getDeclaredAnnotation(JsonbTypeDeserializer.class))
                        .orElseGet(() -> getAnnotationFromParameterType(parameter, JsonbTypeDeserializer.class));
        if (deserializerAnnotation == null) {
            return null;
        }

        final Class<? extends JsonbDeserializer> deserializerClass = deserializerAnnotation.value();
        return jsonbContext.getComponentMatcher().inspectDeserializerBinding(deserializerClass, null);
    }

    /**
     * Collect annotations of given class, its interfaces and the package.
     *
     * @param clazz Class to process.
     * @return Element with class and annotations.
     */
    public JsonbAnnotatedElement<Class<?>> collectAnnotations(Class<?> clazz) {
        JsonbAnnotatedElement<Class<?>> classElement = new JsonbAnnotatedElement<>(clazz);

        if (BuiltInTypes.isKnownType(clazz)) {
            return classElement;
        }

        Map<Class<? extends Annotation>, LinkedList<AnnotationWrapper<?>>> interfaceAnnotations
                = collectInterfaceAnnotations(clazz, clazz);
        for (LinkedList<AnnotationWrapper<?>> wrappers : interfaceAnnotations.values()) {
            for (AnnotationWrapper<?> wrapper : wrappers) {
                if (classElement.getAnnotation(wrapper.getAnnotation().annotationType()).isEmpty()
                        || REPEATABLE.contains(wrapper.getAnnotation().annotationType())) {
                    classElement.putAnnotationWrapper(wrapper);
                }
            }
        }

        if (!clazz.isPrimitive() && !clazz.isArray() && (clazz.getPackage() != null)) {
            addIfNotPresent(classElement, null, clazz.getPackage().getAnnotations());
        }
        return classElement;
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

        final Optional<Class<?>> optionalRawType = ReflectionHelper.getOptionalRawType(property.getPropertyType());
        final Class<?> propertyRawType = optionalRawType.orElse(null);

        if (propertyRawType != null
                && !TemporalAccessor.class.isAssignableFrom(propertyRawType)
                && !Date.class.isAssignableFrom(propertyRawType)
                && !Calendar.class.isAssignableFrom(propertyRawType)) {
            throw new IllegalStateException(MessageProvider.getMessage(MessageConstants.UNSUPPORTED_DATE_TYPE, propertyRawType));
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
     * Checks for {@link JsonbDeserializer} on a property.
     *
     * @param property property not null
     * @return components info
     */
    public DeserializerBinding getDeserializerBinding(Property property) {
        Objects.requireNonNull(property);
        JsonbTypeDeserializer deserializerAnnotation = getAnnotationFromProperty(JsonbTypeDeserializer.class, property)
                .orElseGet(() -> getAnnotationFromPropertyType(property, JsonbTypeDeserializer.class));
        if (deserializerAnnotation == null) {
            return null;
        }

        final Class<? extends JsonbDeserializer> deserializerClass = deserializerAnnotation.value();
        return jsonbContext.getComponentMatcher().inspectDeserializerBinding(deserializerClass, null);
    }

    /**
     * Returns {@link JsonbDateFormatter} instance if {@link JsonbDateFormat} annotation is present.
     *
     * @param param annotated method parameter
     * @return formatter instance if {@link JsonbDateFormat} is present otherwise null
     */
    public JsonbDateFormatter getConstructorDateFormatter(JsonbAnnotatedElement<Parameter> param) {
        return param.getAnnotation(JsonbDateFormat.class)
                .map(annotation -> new JsonbDateFormatter(DateTimeFormatter.ofPattern(annotation.value(),
                                                                                      Locale.forLanguageTag(annotation.locale())),
                                                          annotation.value(), annotation.locale()))
                .orElse(null);
    }

    private Map<Class<? extends Annotation>, LinkedList<AnnotationWrapper<?>>> collectInterfaceAnnotations(Class<?> currentInterf,
                                                                                                           Class<?> processed) {
        Map<Class<? extends Annotation>, LinkedList<AnnotationWrapper<?>>> map = new HashMap<>();
        if (!currentInterf.equals(processed)) {
            for (Annotation annotation : currentInterf.getDeclaredAnnotations()) {
                map.computeIfAbsent(annotation.annotationType(), aClass -> new LinkedList<>())
                        .add(new AnnotationWrapper<>(annotation, true, currentInterf));
            }
        }

        Map<Class<? extends Annotation>, LinkedList<AnnotationWrapper<?>>> parents = new HashMap<>();
        for (Class<?> parentInterf : currentInterf.getInterfaces()) {
            Map<Class<? extends Annotation>, LinkedList<AnnotationWrapper<?>>> current = collectInterfaceAnnotations(parentInterf,
                                                                                                                     processed);
            current.entrySet().stream()
                    .filter(entry -> !parents.containsKey(entry.getKey()) || REPEATABLE.contains(entry.getKey()))
                    .peek(entry -> {
                        if (parents.containsKey(entry.getKey())) {
                            throw new JsonbException("CHANGE THIS EXCEPTION");
                        }
                    })
                    .forEach(entry -> {
                        parents.computeIfAbsent(entry.getKey(), aClass -> new LinkedList<>()).addAll(entry.getValue());
                        map.computeIfAbsent(entry.getKey(), aClass -> new LinkedList<>()).addAll(entry.getValue());
                    });
        }
        return map;
    }

    private <T extends Annotation> T getAnnotationFromPropertyType(Property property, Class<T> annotationClass) {
        final Optional<Class<?>> optionalRawType = ReflectionHelper.getOptionalRawType(property.getPropertyType());
        if (!optionalRawType.isPresent()) {
            //will not work for type variable properties, which are bound to class that is annotated.
            return null;
        }
        return findAnnotation(collectAnnotations(optionalRawType.get()).getAnnotations(), annotationClass);
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
        if (fieldAnnotation != null) {
            return Optional.of(fieldAnnotation);
        }

        T getterAnnotation = getMethodAnnotation(annotationClass, property.getGetterElement());
        if (getterAnnotation != null) {
            return Optional.of(getterAnnotation);
        }

        T setterAnnotation = getMethodAnnotation(annotationClass, property.getSetterElement());
        if (setterAnnotation != null) {
            return Optional.of(setterAnnotation);
        }

        return Optional.empty();
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

    private void checkDuplicityPolymorphicPropertyNames(TypeInheritanceSettings typeInheritanceConfiguration) {
        if (typeInheritanceConfiguration == null) {
            return;
        }
        Map<String, TypeInheritanceSettings> keyNames = new HashMap<>();
        TypeInheritanceSettings current = typeInheritanceConfiguration;
        while (current != null) {
            String fieldName = current.getFieldName();
            if (keyNames.containsKey(fieldName)) {
                TypeInheritanceSettings conflicting = keyNames.get(fieldName);
                throw new JsonbException("One polymorphic chain cannot have two conflicting property names. "
                                                 + "Polymorphic type defined on the type "
                                                 + conflicting.getDefinedType().getName() + " and "
                                                 + current.getDefinedType().getName() + " have conflicting property name");
            }
            keyNames.put(fieldName, current);
            current = current.getParentConfig();
        }
    }

    private <T extends Annotation> T getMethodAnnotation(Class<T> annotationClass, JsonbAnnotatedElement<Method> methodElement) {
        if (methodElement == null) {
            return null;
        }
        return findAnnotation(methodElement.getAnnotations(), annotationClass);
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
     * Checks for {@link JsonbAdapter} on a {@link Parameter}.
     *
     * @param parameter parameter not null
     * @return components info
     */
    public AdapterBindingInfo getAdapterBinding(Parameter parameter) {
        Objects.requireNonNull(parameter);
        JsonbTypeAdapter adapter =
                Optional.ofNullable(parameter.getDeclaredAnnotation(JsonbTypeAdapter.class))
                        .orElseGet(() -> getAnnotationFromParameterType(parameter, JsonbTypeAdapter.class));
        if (adapter == null) {
            return null;
        }

        return getAdapterBindingFromAnnotation(adapter, ReflectionHelper.getOptionalRawType(parameter.getParameterizedType()));
    }

    /**
     * Returns {@link JsonbNumberFormatter} instance if {@link JsonbNumberFormat} annotation is present.
     *
     * @param param annotated method parameter
     * @return formatter instance if {@link JsonbNumberFormat} is present otherwise null
     */
    public JsonbNumberFormatter getConstructorNumberFormatter(JsonbAnnotatedElement<Parameter> param) {
        return param.getAnnotation(JsonbNumberFormat.class)
                .map(annotation -> new JsonbNumberFormatter(annotation.value(), annotation.locale()))
                .orElse(null);
    }

    /**
     * Checks for JsonbNillable annotation on a class, its superclasses and interfaces.
     *
     * @param clazzElement class to search JsonbNillable in.
     * @return true if found
     */
    public boolean isClassNillable(JsonbAnnotatedElement<Class<?>> clazzElement) {
        final JsonbNillable jsonbNillable = findAnnotation(clazzElement.getAnnotations(), JsonbNillable.class);
        if (jsonbNillable != null) {
            return jsonbNillable.value();
        }
        Class<?> clazz = clazzElement.getElement();
        if (clazz == Optional.class
                || clazz == OptionalDouble.class
                || clazz == OptionalInt.class
                || clazz == OptionalLong.class) {
            return true;
        }
        return jsonbContext.getConfigProperties().getConfigNullable();
    }

    JsonbCreator createJsonbCreator(Executable executable, JsonbCreator existing, Class<?> clazz) {
        if (existing != null) {
            throw new JsonbException(MessageProvider.getMessage(MessageConstants.MULTIPLE_JSONB_CREATORS, clazz));
        }

        final Parameter[] parameters = executable.getParameters();

        CreatorModel[] creatorModels = new CreatorModel[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];
            final JsonbProperty jsonbPropertyAnnotation = parameter.getAnnotation(JsonbProperty.class);
            if (jsonbPropertyAnnotation != null && !jsonbPropertyAnnotation.value().isEmpty()) {
                creatorModels[i] = new CreatorModel(jsonbPropertyAnnotation.value(), parameter, executable, jsonbContext);
            } else {
                creatorModels[i] = new CreatorModel(parameter.getName(), parameter, executable, jsonbContext);
            }
        }

        return new JsonbCreator(executable, creatorModels);
    }

    /**
     * Searches for JsonbCreator annotation on constructors and static methods.
     *
     * @param clazz class to search
     * @return JsonbCreator metadata object
     */
    public JsonbCreator getCreator(Class<?> clazz) {
        JsonbCreator jsonbCreator = null;
        Constructor<?>[] declaredConstructors =
                AccessController.doPrivileged((PrivilegedAction<Constructor<?>[]>) clazz::getDeclaredConstructors);

        for (Constructor<?> constructor : declaredConstructors) {
            final jakarta.json.bind.annotation.JsonbCreator annot = findAnnotation(constructor.getDeclaredAnnotations(),
                                                                                   jakarta.json.bind.annotation.JsonbCreator.class);
            if (annot != null) {
                jsonbCreator = createJsonbCreator(constructor, jsonbCreator, clazz);
            }
        }

        Method[] declaredMethods =
                AccessController.doPrivileged((PrivilegedAction<Method[]>) clazz::getDeclaredMethods);
        for (Method method : declaredMethods) {
            final jakarta.json.bind.annotation.JsonbCreator annot = findAnnotation(method.getDeclaredAnnotations(),
                                                                                   jakarta.json.bind.annotation.JsonbCreator.class);
            if (annot != null && Modifier.isStatic(method.getModifiers())) {
                if (!clazz.equals(method.getReturnType())) {
                    throw new JsonbException(MessageProvider.getMessage(MessageConstants.INCOMPATIBLE_FACTORY_CREATOR_RETURN_TYPE,
                                                                 method,
                                                                 clazz));
                }
                jsonbCreator = createJsonbCreator(method, jsonbCreator, clazz);
            }
        }
        if (jsonbCreator == null) {
            jsonbCreator = ClassMultiReleaseExtension.findCreator(clazz, declaredConstructors, this);
            if (jsonbCreator == null) {
                jsonbCreator = constructorPropertiesIntrospector.getCreator(declaredConstructors);
            }
        }
        return jsonbCreator;
    }

    /**
     * Checks for {@link JsonbSerializer} on a property.
     *
     * @param property property not null
     * @return components info
     */
    public JsonbSerializerBinding getSerializerBinding(Property property) {
        Objects.requireNonNull(property);
        JsonbTypeSerializer serializerAnnotation = getAnnotationFromProperty(JsonbTypeSerializer.class, property)
                .orElseGet(() -> getAnnotationFromPropertyType(property, JsonbTypeSerializer.class));
        if (serializerAnnotation == null) {
            return null;
        }

        final Class<? extends JsonbSerializer> serializerClass = serializerAnnotation.value();
        return jsonbContext.getComponentMatcher().inspectSerializerBinding(serializerClass, null);

    }

    /**
     * Search for {@link JsonbDateFormat} annotation on java class and construct {@link JsonbDateFormatter}.
     * If not found looks at annotations declared on property type class.
     *
     * @param clazzElement class to search not null
     * @return formatter to use
     */
    public JsonbDateFormatter getJsonbDateFormat(JsonbAnnotatedElement<Class<?>> clazzElement) {
        Objects.requireNonNull(clazzElement);
        final JsonbDateFormat format = findAnnotation(clazzElement.getAnnotations(), JsonbDateFormat.class);
        if (format == null) {
            return jsonbContext.getConfigProperties().getConfigDateFormatter();
        }
        return new JsonbDateFormatter(format.value(), format.locale());
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
        Map<AnnotationTarget, JsonbDateFormat> annotationFromPropertyCategorized = getAnnotationFromPropertyCategorized(
                JsonbDateFormat.class,
                property);
        if (annotationFromPropertyCategorized.size() != 0) {
            annotationFromPropertyCategorized.forEach((key, annotation) -> result
                    .put(key, createJsonbDateFormatter(annotation.value(), annotation.locale(), property)));
        }

        // No date format on property, try class level
        // if property is not TypeVariable and its class is not date skip it
        final Optional<Class<?>> propertyRawTypeOptional = ReflectionHelper.getOptionalRawType(property.getPropertyType());
        if (propertyRawTypeOptional.isPresent()) {
            Class<?> rawType = propertyRawTypeOptional.get();
            if (!(
                    Date.class.isAssignableFrom(rawType) || Calendar.class.isAssignableFrom(rawType)
                            || TemporalAccessor.class.isAssignableFrom(rawType))) {
                return new HashMap<>();
            }
        }

        JsonbDateFormat classLevelDateFormatter = findAnnotation(property.getDeclaringClassElement().getAnnotations(),
                                                                 JsonbDateFormat.class);
        if (classLevelDateFormatter != null) {
            result.put(AnnotationTarget.CLASS,
                       createJsonbDateFormatter(classLevelDateFormatter.value(), classLevelDateFormatter.locale(), property));
        }

        return result;
    }

    /**
     * Checks for {@link JsonbDeserializer} on a type.
     *
     * @param clsElement type not null
     * @return components info
     */
    public DeserializerBinding getDeserializerBinding(JsonbAnnotatedElement<Class<?>> clsElement) {
        Objects.requireNonNull(clsElement);
        JsonbTypeDeserializer deserializerAnnotation = clsElement.getElement().getAnnotation(JsonbTypeDeserializer.class);
        if (deserializerAnnotation == null) {
            return null;
        }

        final Class<? extends JsonbDeserializer> deserializerClass = deserializerAnnotation.value();
        return jsonbContext.getComponentMatcher().inspectDeserializerBinding(deserializerClass, null);
    }

    private TypeInheritanceSettings getPolymorphismConfig(JsonbAnnotatedElement<Class<?>> clsElement,
                                                          ClassSerializationConfig parentCustomization) {
        TypeInheritanceSettings parentPolyConfig = parentCustomization.getPolymorphismConfig();

        LinkedList<AnnotationWrapper<?>> annotations = clsElement.getAnnotations(JsonbTypeInfo.class);

        if (parentPolyConfig != null) {
            if (annotations.size() == 1 && annotations.getFirst().isInherited()) {
                throw new JsonbException("CHANGE");
            } else if (annotations.size() > 1) {
                throw new JsonbException("CHANGE");
            } else if (annotations.isEmpty()) {
                return TypeInheritanceSettings.newBuilder().from(parentPolyConfig)
                        .setInherited(true)
                        .create();
            }
        }
        ListIterator<AnnotationWrapper<?>> listIterator = annotations.listIterator(annotations.size());
        while (listIterator.hasPrevious()) {
            AnnotationWrapper<?> annotationWrapper = listIterator.previous();
            JsonbTypeInfo annotation = (JsonbTypeInfo) annotationWrapper.getAnnotation();
            TypeInheritanceSettings.FieldConfigBuilder builder = TypeInheritanceSettings.newBuilder();
            builder.setFieldName(annotation.key())
                    .setInherited(annotationWrapper.isInherited())
                    .setParentConfig(parentPolyConfig)
                    .setDefinedType(annotationWrapper.getDefinedType());
            for (JsonbSubtype subType : annotation.value()) {
                if (!annotationWrapper.getDefinedType().isAssignableFrom(subType.type())) {
                    throw new JsonbException("Defined alias type has to be child of the current type. JsonbSubType on the "
                                                     + annotationWrapper.getDefinedType().getName()
                                                     + " defines incorrect alias "
                                                     + subType);
                }
                builder.addAlias(subType.type(), subType.alias());
            }
            parentPolyConfig = builder.create();
        }

        checkDuplicityPolymorphicPropertyNames(parentPolyConfig);

        return parentPolyConfig;
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
        Map<AnnotationTarget, JsonbTransient> annotationFromPropertyCategorized = getAnnotationFromPropertyCategorized(
                JsonbTransient.class,
                property);
        if (annotationFromPropertyCategorized.size() > 0) {
            transientTarget.addAll(annotationFromPropertyCategorized.keySet());
            return transientTarget;
        }

        return transientTarget;
    }

    private AdapterBindingInfo getAdapterBindingFromAnnotation(JsonbTypeAdapter adapterAnnotation, Optional<Class<?>> expectedClass) {
        final Class<? extends JsonbAdapter> adapterClass = adapterAnnotation.value();
        final AdapterBindingInfo adapterBinding = jsonbContext.getComponentMatcher().inspectAdapterBinding(adapterClass, null);

        if (expectedClass.isPresent() && !(
                ReflectionHelper.getRawType(adapterBinding.getBindingType()).isAssignableFrom(expectedClass.get()))) {
            throw new JsonbException(MessageProvider.getMessage(MessageConstants.ADAPTER_INCOMPATIBLE,
                                                         adapterBinding.getBindingType(),
                                                         expectedClass.get()));
        }
        return adapterBinding;
    }

    private String getJsonbPropertyCustomizedName(Property property, JsonbAnnotatedElement<Method> methodElement) {
        JsonbProperty methodAnnotation = getMethodAnnotation(JsonbProperty.class, methodElement);
        if (methodAnnotation != null && !methodAnnotation.value().isEmpty()) {
            return methodAnnotation.value();
        }
        //in case of property name getter/setter override field value
        JsonbProperty fieldAnnotation = getFieldAnnotation(JsonbProperty.class, property.getFieldElement());
        if (fieldAnnotation != null && !fieldAnnotation.value().isEmpty()) {
            return fieldAnnotation.value();
        }

        return null;
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
        Map<AnnotationTarget, JsonbNumberFormat> annotationFromPropertyCategorized = getAnnotationFromPropertyCategorized(
                JsonbNumberFormat.class,
                property);
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
        annotationFromPropertyCategorized.forEach((key, annotation) -> result
                .put(key, new JsonbNumberFormatter(annotation.value(), annotation.locale())));

        JsonbNumberFormat classLevelNumberFormatter = findAnnotation(property.getDeclaringClassElement().getAnnotations(),
                                                                     JsonbNumberFormat.class);
        if (classLevelNumberFormatter != null) {
            result.put(AnnotationTarget.CLASS,
                       new JsonbNumberFormatter(classLevelNumberFormatter.value(), classLevelNumberFormatter.locale()));
        }

        return result;
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
    private <T extends Annotation> Map<AnnotationTarget, T> getAnnotationFromPropertyCategorized(Class<T> annotationClass,
                                                                                                 Property property) {
        Map<AnnotationTarget, T> result = new HashMap<>();
        T fieldAnnotation = getFieldAnnotation(annotationClass, property.getFieldElement());
        if (fieldAnnotation != null) {
            result.put(AnnotationTarget.PROPERTY, fieldAnnotation);
        }

        T getterAnnotation = getMethodAnnotation(annotationClass, property.getGetterElement());
        if (getterAnnotation != null) {
            result.put(AnnotationTarget.GETTER, getterAnnotation);
        }

        T setterAnnotation = getMethodAnnotation(annotationClass, property.getSetterElement());
        if (setterAnnotation != null) {
            result.put(AnnotationTarget.SETTER, setterAnnotation);
        }

        return result;
    }

    private <T extends Annotation> T getFieldAnnotation(Class<T> annotationClass, JsonbAnnotatedElement<Field> fieldElement) {
        if (fieldElement == null) {
            return null;
        }
        return findAnnotation(fieldElement.getAnnotations(), annotationClass);
    }

    private void addIfNotPresent(JsonbAnnotatedElement<?> element, Class<?> definedType, Annotation... annotations) {
        for (Annotation annotation : annotations) {
            if (element.getAnnotation(annotation.annotationType()).isEmpty()
                    || REPEATABLE.contains(annotation.annotationType())) {
                element.putAnnotation(annotation, true, definedType);
            }
        }
    }

    public boolean requiredParameters(Executable executable, JsonbAnnotatedElement<Parameter> annotated) {
        return jsonbContext.getConfigProperties().isRequiredCreatorParametersPresent();
        //        if (OPTIONALS.contains(annotated.getElement().getType())) {
        //            return false;
        //        }
        //        return annotated.getAnnotation(JsonbRequired.class)
        //                .or(() -> Optional.ofNullable(executable.getAnnotation(JsonbRequired.class)))
        //                .map(JsonbRequired::value)
        //                .orElseGet(() -> jsonbContext.getConfigProperties().hasRequiredCreatorParameters());
    }

    private <T extends Annotation> void collectFromInterfaces(Class<T> annotationClass,
                                                              Class<?> clazz,
                                                              Map<Class<?>, T> collectedAnnotations) {

        for (Class<?> interfaceClass : clazz.getInterfaces()) {
            T annotation = findAnnotation(interfaceClass.getDeclaredAnnotations(), annotationClass);
            if (annotation != null) {
                collectedAnnotations.put(interfaceClass, annotation);
            }
            collectFromInterfaces(annotationClass, interfaceClass, collectedAnnotations);
        }
    }

    /**
     * Checks for {@link JsonbAdapter} on a type.
     *
     * @param clsElement type not null
     * @return components info
     */
    public AdapterBindingInfo getAdapterBinding(JsonbAnnotatedElement<Class<?>> clsElement) {
        Objects.requireNonNull(clsElement);

        JsonbTypeAdapter adapterAnnotation = clsElement.getElement().getAnnotation(JsonbTypeAdapter.class);
        if (adapterAnnotation == null) {
            return null;
        }

        return getAdapterBindingFromAnnotation(adapterAnnotation, Optional.ofNullable(clsElement.getElement()));
    }

    /**
     * Finds annotations incompatible with {@link JsonbTransient} annotation.
     *
     * @param target target to check
     */
    public void checkTransientIncompatible(JsonbAnnotatedElement<?> target) {
        if (target == null) {
            return;
        }

        for (Class<? extends Annotation> ann : TRANSIENT_INCOMPATIBLE) {
            Annotation annotation = findAnnotation(target.getAnnotations(), ann);
            if (annotation != null) {
                throw new JsonbException(MessageProvider.getMessage(MessageConstants.JSONB_TRANSIENT_WITH_OTHER_ANNOTATIONS));
            }
        }
    }

    /**
     * Processes customizations.
     *
     * @param clsElement Element to process.
     * @return Populated {@link ClassSerializationConfig} instance.
     */
    public ClassSerializationConfig introspectCustomization(JsonbAnnotatedElement<Class<?>> clsElement,
                                                            ClassSerializationConfig parentCustomization) {
        return ClassSerializationConfig.newBuilder()
                .nillable(isClassNillable(clsElement))
                .withDateTimeFormatter(getJsonbDateFormat(clsElement))
                .withNumberFormatter(getJsonbNumberFormat(clsElement))
                .withCreator(getCreator(clsElement.getElement()))
                .withPropertyOrder(getPropertyOrder(clsElement))
                .adapterBinding(getAdapterBinding(clsElement))
                .serializerBinding(getSerializerBinding(clsElement))
                .deserializerBinding(getDeserializerBinding(clsElement))
                .withPropertyVisibilityStrategy(getPropertyVisibilityStrategy(clsElement.getElement()))
                .withPolymorphismConfig(getPolymorphismConfig(clsElement, parentCustomization))
                .build();
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
     * Search for {@link JsonbNumberFormat} annotation on java class.
     *
     * @param clazzElement class to search not null
     * @return formatter to use
     */
    public JsonbNumberFormatter getJsonbNumberFormat(JsonbAnnotatedElement<Class<?>> clazzElement) {
        final JsonbNumberFormat formatAnnotation = findAnnotation(clazzElement.getAnnotations(), JsonbNumberFormat.class);
        if (formatAnnotation == null) {
            return null;
        }
        return new JsonbNumberFormatter(formatAnnotation.value(), formatAnnotation.locale());
    }

    /**
     * Creates annotation introspecting component passing {@link JsonBindingContext} inside.
     *
     * @param jsonbContext mandatory
     */
    public AnnotationIntrospector(JsonBindingContext jsonbContext) {
        Objects.requireNonNull(jsonbContext);
        this.jsonbContext = jsonbContext;
        this.constructorPropertiesIntrospector = ConstructorPropertiesAnnotationIntrospector.forContext(jsonbContext);
    }

    /**
     * Checks for {@link JsonbPropertyOrder} annotation.
     *
     * @param clazzElement class to search on
     * @return ordered properties names or null if not found
     */
    public String[] getPropertyOrder(JsonbAnnotatedElement<Class<?>> clazzElement) {
        final JsonbPropertyOrder jsonbPropertyOrder = clazzElement.getElement().getAnnotation(JsonbPropertyOrder.class);
        return jsonbPropertyOrder != null ? jsonbPropertyOrder.value() : null;
    }

    /**
     * Checks for {@link JsonbAdapter} on a property.
     *
     * @param property property not null
     * @return components info
     */
    public AdapterBindingInfo getAdapterBinding(Property property) {
        Objects.requireNonNull(property);
        JsonbTypeAdapter adapterAnnotation = getAnnotationFromProperty(JsonbTypeAdapter.class, property)
                .orElseGet(() -> getAnnotationFromPropertyType(property, JsonbTypeAdapter.class));
        if (adapterAnnotation == null) {
            return null;
        }

        return getAdapterBindingFromAnnotation(adapterAnnotation, ReflectionHelper.getOptionalRawType(property.getPropertyType()));
    }

    private <T extends Annotation> T findAnnotation(Annotation[] declaredAnnotations, Class<T> annotationClass) {
        return AnnotationFinder.findAnnotation(declaredAnnotations, annotationClass, new HashSet<>());
    }

    private <T extends Annotation> T getAnnotationFromParameterType(Parameter parameter, Class<T> annotationClass) {
        final Optional<Class<?>> optionalRawType = ReflectionHelper.getOptionalRawType(parameter.getParameterizedType());
        //will not work for type variable properties, which are bound to class that is annotated.
        return optionalRawType.map(aClass -> findAnnotation(collectAnnotations(aClass).getAnnotations(), annotationClass))
                .orElse(null);
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
     * Checks for {@link JsonbSerializer} on a type.
     *
     * @param clsElement type not null
     * @return components info
     */
    public JsonbSerializerBinding getSerializerBinding(JsonbAnnotatedElement<Class<?>> clsElement) {
        Objects.requireNonNull(clsElement);
        JsonbTypeSerializer serializerAnnotation = clsElement.getElement().getAnnotation(JsonbTypeSerializer.class);
        if (serializerAnnotation == null) {
            return null;
        }

        final Class<? extends JsonbSerializer> serializerClass = serializerAnnotation.value();
        return jsonbContext.getComponentMatcher().inspectSerializerBinding(serializerClass, null);
    }

    /**
     * Get a @JsonbVisibility annotation from a class or its package.
     *
     * @param clazz Class to lookup annotation
     * @return Instantiated PropertyVisibilityStrategy if annotation is present
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy(Class<?> clazz) {
        JsonbVisibility visibilityAnnotation = findAnnotation(clazz.getDeclaredAnnotations(), JsonbVisibility.class);
        if ((visibilityAnnotation == null) && (clazz.getPackage() != null)) {
            visibilityAnnotation = findAnnotation(clazz.getPackage().getDeclaredAnnotations(), JsonbVisibility.class);
        }
        if (visibilityAnnotation != null) {
            return ReflectionHelper.instantiateNoArgConstructor(
                    ReflectionHelper.getDefaultConstructor(visibilityAnnotation.value(), true));
        }
        return jsonbContext.getConfigProperties().getPropertyVisibilityStrategy();
    }

}

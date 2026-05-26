/*
 * Copyright (c) 2015, 2022 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.JsonbAnnotatedElement;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;

/**
 * JSONB mappingContext. Created once per {@link jakarta.json.bind.Jsonb} instance. Represents a global scope.
 * Holds internal model.
 *
 * Thread safe.
 */
public class ClassModelContext {
    private final JsonBindingContext bindingContext;

    private final ConcurrentHashMap<Class<?>, ClassDescriptor> classDescriptorMap = new ConcurrentHashMap<>();

    private final ClassParser typeParser;

    //    private static ClassCustomization mergeConfigAndAnnotationPolymorphism(PolymorphismSupport generalPolymorphism,
//                                                                           Optional<Polymorphism> maybeClassPolymorphism,
//                                                                           ClassCustomization customization,
//                                                                           Class<?> aClass) {
//        PolymorphismConfig polymorphismConfig = customization.getPolymorphismConfig();
//        PolymorphismConfig.Builder polyConfigBuilder;
//        if (polymorphismConfig != null) {
//            polyConfigBuilder = PolymorphismConfig.builder().of(polymorphismConfig);
//        } else {
//            polyConfigBuilder = PolymorphismConfig.builder();
//            maybeClassPolymorphism.ifPresent(classPolymorphism -> polyConfigBuilder
//                    .inherited(!classPolymorphism.getBoundClass().equals(aClass)));
//        }
//        generalPolymorphism.getKeyName().filter(s -> !s.isEmpty()).ifPresent(polyConfigBuilder::fieldName);
//        generalPolymorphism.useClassNames().ifPresent(polyConfigBuilder::useClassNames);
//        polyConfigBuilder.whitelistedPackages(generalPolymorphism.getWhitelistedPackages());
//
//        maybeClassPolymorphism.ifPresent(classPolymorphism -> {
//            classPolymorphism.getKeyName().filter(s -> !s.isEmpty()).ifPresent(polyConfigBuilder::fieldName);
//            classPolymorphism.useClassNames().ifPresent(polyConfigBuilder::useClassNames);
//            classPolymorphism.getFormat().ifPresent(polyConfigBuilder::format);
//            classPolymorphism.getAliases().forEach(polyConfigBuilder::alias);
//            polyConfigBuilder.whitelistedPackages(classPolymorphism.getWhitelistedPackages());
//        });
//        PolymorphismConfig polyConfigMerged = polyConfigBuilder.build();
//        if (polyConfigMerged.getFieldName() == null || polyConfigMerged.getFieldName().isEmpty()) {
//            throw new JsonbException("Polymorphism type field name cannot be null or empty: " + aClass);
//        }
//        return ClassCustomization.builder()
//                .of(customization)
//                .polymorphismConfig(polyConfigMerged)
//                .build();
//    }

    private static Function<Class<?>, ClassDescriptor> createClassModelParserFunction(ClassDescriptor parentDescriptor,
                                                                                      ClassParser typeParser,
                                                                                      JsonBindingContext bindingContext) {
        return candidateType -> {
            JsonbAnnotatedElement<Class<?>> annotatedElement = bindingContext.getAnnotationIntrospector().collectAnnotations(candidateType);
            ClassSerializationConfig serializationConfig = bindingContext.getAnnotationIntrospector()
                    .introspectCustomization(annotatedElement,
                                             parentDescriptor == null
                                                     ? ClassSerializationConfig.emptyConfig()
                                                     : parentDescriptor.getClassCustomization());
            //            PolymorphismSupport configPolymorphism = jsonbContext.getConfigProperties().getPolymorphismSupport();
//            if (configPolymorphism != null) {
//                customization = mergeConfigAndAnnotationPolymorphism(configPolymorphism,
//                                                                     configPolymorphism.getClassPolymorphism(aClass),
//                                                                     customization,
//                                                                     aClass);
//            }
            ClassDescriptor newDescriptor = new ClassDescriptor(candidateType,
                    serializationConfig,
                    parentDescriptor,
                                                      bindingContext.getConfigProperties().getPropertyNamingStrategy());
            if (!BuiltInTypes.isKnownType(candidateType)) {
                typeParser.parseProperties(newDescriptor, annotatedElement);
            }
            return newDescriptor;
        };
    }

    /**
     * Search for class model, without parsing if not found.
     *
     * @param targetType Class to search by or parse, not null.
     * @return Model of a class if found.
     */
    public ClassDescriptor getClassModel(Class<?> targetType) {
        return classDescriptorMap.get(targetType);
    }

    /**
     * Create mapping context which is scoped to jsonb runtime.
     *
     * @param bindingContext Context. Required.
     */
    public ClassModelContext(JsonBindingContext bindingContext) {
        Objects.requireNonNull(bindingContext);
        this.bindingContext = bindingContext;
        this.typeParser = new ClassParser(bindingContext);
    }

    /**
     * Searches for class model for given class. Returns the existing instance. Creates a new instance if
     * it doesn't exist.
     *
     * @param typeToken Class to search by or parse, not null.
     * @return {@link ClassDescriptor} for given class.
     */
    public ClassDescriptor getOrCreateClassModel(Class<?> typeToken) {
        ClassDescriptor descriptor = classDescriptorMap.get(typeToken);
        if (descriptor != null) {
            return descriptor;
        }

        Deque<Class<?>> classDeque = new ArrayDeque<>();
        for (Class<?> typeToParse = typeToken; typeToParse != Object.class; typeToParse = typeToParse.getSuperclass()) {
            if (typeToParse == null) {
                break;
            }
            classDeque.push(typeToParse);
        }
        if (typeToken == Object.class) {
            return classDescriptorMap.computeIfAbsent(typeToken, (candidate) -> new ClassDescriptor(candidate, ClassSerializationConfig.emptyConfig(), null, null));
        }

        ClassDescriptor parentDescriptor = null;
        while (!classDeque.isEmpty()) {
            Class<?> targetType = classDeque.pop();
            parentDescriptor = classDescriptorMap
                    .computeIfAbsent(targetType, createClassModelParserFunction(parentDescriptor, typeParser, bindingContext));
        }
        return classDescriptorMap.get(typeToken);
    }

}

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

package org.eclipse.yasson.internal.model;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import jakarta.json.bind.config.PropertyNamingStrategy;

import org.eclipse.yasson.internal.ReflectionHelper;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.model.customization.PropertyNamingStrategyProvider;

/**
 * A model for Java class.
 */
public class ClassDescriptor {

    private final Class<?> targetClass;

    private final ClassSerializationConfig serializationConfig;

    private final ClassDescriptor parentDescriptor;

    private final AtomicBoolean initializedFlag = new AtomicBoolean(false);

    private Constructor<?> noArgConstructor;

    /**
     * A map of all class properties, including properties from superclasses. Used to access by name.
     */
    private Map<String, BeanPropertyDescriptor> propertyMap;

    /**
     * Sorted properties according to sorting strategy. Used for serialization property ordering.
     */
    private BeanPropertyDescriptor[] orderedProperties;

    private final PropertyNamingStrategy namingStrategy;

    /**
     * Class model of parent class if present.
     *
     * @return class model of a parent
     */
    public ClassDescriptor getParentClassModel() {
        return parentDescriptor;
    }

    /**
     * Get sorted class properties copy, combination of field and its getter / setter, javabeans alike.
     *
     * @return sorted class properties.
     */
    public BeanPropertyDescriptor[] getSortedProperties() {
        return orderedProperties;
    }

    /**
     * Gets a property model by default (non customized) name.
     *
     * @param propertyName A name as parsed from field / getter / setter without annotation customizing.
     * @return Property model.
     */
    public BeanPropertyDescriptor getPropertyModel(String propertyName) {
        return propertyMap.get(propertyName);
    }

    /**
     * Sets parsed properties of the class.
     *
     * @param parsedProps class properties
     */
    public void setProperties(List<BeanPropertyDescriptor> parsedProps) {
        orderedProperties = parsedProps.toArray(new BeanPropertyDescriptor[] {});
        this.propertyMap = parsedProps.stream().collect(Collectors.toMap(BeanPropertyDescriptor::getPropertyName, (modifier) -> modifier));
    }

    /**
     * Get class properties copy, combination of field and its getter / setter, javabeans alike.
     *
     * @return class properties.
     */
    public Map<String, BeanPropertyDescriptor> getProperties() {
        return Collections.unmodifiableMap(propertyMap);
    }

    /**
     * Search for field in this class model and superclasses of its class.
     *
     * @param jsonFieldName name as it appears in JSON during reading.
     * @return PropertyModel if found.
     */
    public BeanPropertyDescriptor findPropertyModelByJsonReadName(String jsonFieldName) {
        Objects.requireNonNull(jsonFieldName);
        return findProperty(this, jsonFieldName);
    }

    private BeanPropertyDescriptor findProperty(ClassDescriptor targetDescriptor, String jsonFieldName) {
        //Standard javabean properties without overridden name (most of the cases)
        final BeanPropertyDescriptor foundProperty = targetDescriptor.getPropertyModel(jsonFieldName);
        if (foundProperty != null && foundProperty.getPropertyName().equals(foundProperty.getReadName())) {
            return foundProperty;
        }
        //Search for overridden name on setter with @JsonbProperty annotation
        for (BeanPropertyDescriptor propertyDescriptor : propertyMap.values()) {
            if (matchesReadName(jsonFieldName, propertyDescriptor)) {
                return propertyDescriptor;
            }
        }
        //property not found
        return null;
    }

    /**
     * Default no argument constructor of the class used for deserialization.
     *
     * @return default constructor
     */
    public Constructor<?> getDefaultConstructor() {
        // Lazy-loads the default constructor to avoid Java 9+ "Illegal reflective access" warnings where possible.
        // Example: Deserialization into Map won't use this constructor, and therefore never needs to call this method.
        // Note: Null is a valid result and needs to be cached.
        if (!initializedFlag.get()) {
            noArgConstructor = ReflectionHelper.getDefaultConstructor(targetClass, false);
            initializedFlag.set(true);
        }
        return noArgConstructor;
    }

    /**
     * Create instance of class model.
     *
     * @param targetClass                  Class to model.
     * @param serializationConfig          Customization of the class parsed from annotations.
     * @param parentDescriptor       Class model of parent class.
     * @param namingStrategy Property naming strategy.
     */
    public ClassDescriptor(Class<?> targetClass,
                           ClassSerializationConfig serializationConfig,
                           ClassDescriptor parentDescriptor,
                           PropertyNamingStrategy namingStrategy) {
        this.targetClass = targetClass;
        this.serializationConfig = serializationConfig;
        this.parentDescriptor = parentDescriptor;
        this.namingStrategy = namingStrategy;
        setProperties(new ArrayList<>());
    }

    /**
     * Check if name is equal according to property strategy.
     * In case of {@link PropertyNamingStrategyProvider#CASE_INSENSITIVE_STRATEGY} ignore case.
     * User can provide own strategy implementation, cast to custom interface is not an option.
     *
     * @return True if names are equal.
     */
    private boolean matchesReadName(String candidateJsonName, BeanPropertyDescriptor propertyDescriptor) {
        final String resolvedReadName = propertyDescriptor.getReadName();
        if (namingStrategy == PropertyNamingStrategyProvider.CASE_INSENSITIVE_STRATEGY) {
            return candidateJsonName.equalsIgnoreCase(resolvedReadName);
        }
        return candidateJsonName.equals(resolvedReadName);
    }

    /**
     * Introspected customization for a class.
     *
     * @return Immutable class customization.
     */
    public ClassSerializationConfig getClassCustomization() {
        return serializationConfig;
    }

    /**
     * Gets type.
     *
     * @return Type.
     */
    public Class<?> getType() {
        return targetClass;
    }

}

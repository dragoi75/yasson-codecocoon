/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.ReflectionUtils;
import org.eclipse.yasson.internal.model.customization.ClassConfiguration;
import org.eclipse.yasson.internal.model.customization.StrategiesProvider;

/**
 * A model for Java class.
 */
public class ClassDescriptor {

    private final Class<?> describedClass;

    private final ClassConfiguration classConfig;

    private final ClassDescriptor parentDescriptor;

    private final AtomicBoolean initializedFlag = new AtomicBoolean(false);

    private Constructor<?> noArgConstructor;

    /**
     * A map of all class properties, including properties from superclasses. Used to access by name.
     */
    private Map<String, BeanPropertyModel> propertyMap;

    /**
     * Sorted properties according to sorting strategy. Used for serialization property ordering.
     */
    private BeanPropertyModel[] orderedPropertyArray;

    private final PropertyNamingStrategy namingStrategy;

    /**
     * Gets a property model by default (non customized) name.
     *
     * @param propName A name as parsed from field / getter / setter without annotation customizing.
     * @return Property model.
     */
    public BeanPropertyModel getPropertyModel(String propName) {
        return propertyMap.get(propName);
    }

    /**
     * Create instance of class model.
     *
     * @param describedClass                  Class to model.
     * @param config          Customization of the class parsed from annotations.
     * @param parentDescriptor       Class model of parent class.
     * @param namingStrategy Property naming strategy.
     */
    public ClassDescriptor(Class<?> describedClass,
                           ClassConfiguration config,
                           ClassDescriptor parentDescriptor,
                           PropertyNamingStrategy namingStrategy) {
        this.describedClass = describedClass;
        this.classConfig = config;
        this.parentDescriptor = parentDescriptor;
        this.namingStrategy = namingStrategy;
        setProperties(new ArrayList<>());
    }

    /**
     * Search for field in this class model and superclasses of its class.
     *
     * @param jsonNameKey name as it appears in JSON during reading.
     * @return PropertyModel if found.
     */
    public BeanPropertyModel getPropertyModelByJsonReadName(String jsonNameKey) {
        Objects.requireNonNull(jsonNameKey);
        return findProperty(this, jsonNameKey);
    }

    private BeanPropertyModel findProperty(ClassDescriptor descriptor, String jsonNameKey) {
        //Standard javabean properties without overridden name (most of the cases)
        final BeanPropertyModel foundProperty = descriptor.getPropertyModel(jsonNameKey);
        if (foundProperty != null && foundProperty.getPropertyName().equals(foundProperty.getReadName())) {
            return foundProperty;
        }
        //Search for overridden name on setter with @JsonbProperty annotation
        for (BeanPropertyModel candidateProperty : propertyMap.values()) {
            if (isReadNameEqual(jsonNameKey, candidateProperty)) {
                return candidateProperty;
            }
        }
        //property not found
        return null;
    }

    /**
     * Check if name is equal according to property strategy. In case of {@link CaseInsensitiveStrategy} ignore case.
     * User can provide own strategy implementation, cast to custom interface is not an option.
     *
     * @return True if names are equal.
     */
    private boolean isReadNameEqual(String inputJsonName, BeanPropertyModel candidateProperty) {
        final String propReadName = candidateProperty.getReadName();
        if (namingStrategy == StrategiesProvider.CASE_INSENSITIVE_STRATEGY) {
            return inputJsonName.equalsIgnoreCase(propReadName);
        }
        return inputJsonName.equals(propReadName);
    }

    /**
     * Gets type.
     *
     * @return Type.
     */
    public Class<?> getType() {
        return describedClass;
    }

    /**
     * Introspected customization for a class.
     *
     * @return Immutable class customization.
     */
    public ClassConfiguration getClassCustomization() {
        return classConfig;
    }

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
    public BeanPropertyModel[] getSortedProperties() {
        return orderedPropertyArray;
    }

    /**
     * Sets parsed properties of the class.
     *
     * @param parsedPropertyList class properties
     */
    public void setProperties(List<BeanPropertyModel> parsedPropertyList) {
        orderedPropertyArray = parsedPropertyList.toArray(new BeanPropertyModel[] {});
        this.propertyMap = parsedPropertyList.stream().collect(Collectors.toMap(BeanPropertyModel::getPropertyName, (modelParam) -> modelParam));
    }

    /**
     * Get class properties copy, combination of field and its getter / setter, javabeans alike.
     *
     * @return class properties.
     */
    public Map<String, BeanPropertyModel> getProperties() {
        return Collections.unmodifiableMap(propertyMap);
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
            noArgConstructor = ReflectionUtils.getDefaultConstructor(describedClass, false);
            initializedFlag.set(true);
        }
        return noArgConstructor;
    }
}

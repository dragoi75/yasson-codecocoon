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
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.model.customization.StrategiesProvider;

/**
 * A model for Java class.
 */
public class ClassDescriptor {

    private final Class<?> objectType;

    private final ClassSerializationConfig serializationConfig;

    private final ClassDescriptor parentDescriptor;

    private final AtomicBoolean initializedFlag = new AtomicBoolean(false);

    private Constructor<?> noArgConstructor;

    /**
     * A map of all class properties, including properties from superclasses. Used to access by name.
     */
    private Map<String, PropertyMetadata> propertyMap;

    /**
     * Sorted properties according to sorting strategy. Used for serialization property ordering.
     */
    private PropertyMetadata[] orderedProperties;

    private final PropertyNamingStrategy namingStrategy;

    /**
     * Introspected customization for a class.
     *
     * @return Immutable class customization.
     */
    public ClassSerializationConfig getClassCustomization() {
        return serializationConfig;
    }

    /**
     * Sets parsed properties of the class.
     *
     * @param parsedPropertyList class properties
     */
    public void setProperties(List<PropertyMetadata> parsedPropertyList) {
        orderedProperties = parsedPropertyList.toArray(new PropertyMetadata[] {});
        this.propertyMap = parsedPropertyList.stream().collect(Collectors.toMap(PropertyMetadata::getPropertyName, (modifier) -> modifier));
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
    public PropertyMetadata[] getSortedProperties() {
        return orderedProperties;
    }

    private PropertyMetadata findPropertyByJsonReadName(ClassDescriptor classDescriptor, String jsonFieldName) {
        //Standard javabean properties without overridden name (most of the cases)
        final PropertyMetadata foundProperty = classDescriptor.getPropertyModel(jsonFieldName);
        if (null != foundProperty && foundProperty.getPropertyName().equals(foundProperty.getReadName())) {
            return foundProperty;
        }
        //Search for overridden name on setter with @JsonbProperty annotation
        for (PropertyMetadata propModel : propertyMap.values()) {
            if (isReadNameEqual(jsonFieldName, propModel)) {
                return propModel;
            }
        }
        //property not found
        return null;
    }

    /**
     * Create instance of class model.
     *
     * @param objectType                  Class to model.
     * @param config          Customization of the class parsed from annotations.
     * @param parentDescriptor       Class model of parent class.
     * @param namingStrategy Property naming strategy.
     */
    public ClassDescriptor(Class<?> objectType, ClassSerializationConfig config, ClassDescriptor parentDescriptor, PropertyNamingStrategy namingStrategy) {
        this.objectType = objectType;
        this.serializationConfig = config;
        this.parentDescriptor = parentDescriptor;
        this.namingStrategy = namingStrategy;
        setProperties(new ArrayList<>());
    }

    /**
     * Check if name is equal according to property strategy. In case of {@link CaseInsensitiveStrategy} ignore case.
     * User can provide own strategy implementation, cast to custom interface is not an option.
     *
     * @return True if names are equal.
     */
    private boolean isReadNameEqual(String jsonFieldName, PropertyMetadata propModel) {
        final String actualReadName = propModel.getReadName();
        if (StrategiesProvider.CASE_INSENSITIVE_STRATEGY == namingStrategy) {
            return jsonFieldName.equalsIgnoreCase(actualReadName);
        }
        return jsonFieldName.equals(actualReadName);
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
            noArgConstructor = ReflectionUtils.getDefaultConstructor(objectType, false);
            initializedFlag.set(true);
        }
        return noArgConstructor;
    }

    /**
     * Gets a property model by default (non customized) name.
     *
     * @param propertyName A name as parsed from field / getter / setter without annotation customizing.
     * @return Property model.
     */
    public PropertyMetadata getPropertyModel(String propertyName) {
        return propertyMap.get(propertyName);
    }

    /**
     * Search for field in this class model and superclasses of its class.
     *
     * @param jsonFieldName name as it appears in JSON during reading.
     * @return PropertyModel if found.
     */
    public PropertyMetadata getPropertyModelByJsonReadName(String jsonFieldName) {
        Objects.requireNonNull(jsonFieldName);
        return findPropertyByJsonReadName(this, jsonFieldName);
    }

    /**
     * Gets type.
     *
     * @return Type.
     */
    public Class<?> getType() {
        return objectType;
    }

    /**
     * Get class properties copy, combination of field and its getter / setter, javabeans alike.
     *
     * @return class properties.
     */
    public Map<String, PropertyMetadata> getProperties() {
        return Collections.unmodifiableMap(propertyMap);
    }

}

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
import java.util.stream.Collectors;
import jakarta.json.bind.config.PropertyNamingStrategy;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.model.customization.StrategiesProvider;

/**
 * A model for Java class.
 */
public class ClassDescriptor {

    private final Class<?> targetType;

    private final ClassSerializationConfig serializationConfig;

    private final ClassDescriptor parentDescriptor;

    private final Constructor<?> noArgConstructor;

    /**
     * A map of all class properties, including properties from superclasses. Used to access by name.
     */
    private Map<String, PropertyModel> propertyMap;

    /**
     * Sorted properties according to sorting strategy. Used for serialization property ordering.
     */
    private PropertyModel[] orderedProperties;

    private final PropertyNamingStrategy namingStrategy;

    /**
     * Get class properties copy, combination of field and its getter / setter, javabeans alike.
     *
     * @return class properties.
     */
    public Map<String, PropertyModel> getProperties() {
        return Collections.unmodifiableMap(propertyMap);
    }

    /**
     * Get sorted class properties copy, combination of field and its getter / setter, javabeans alike.
     *
     * @return sorted class properties.
     */
    public PropertyModel[] getSortedProperties() {
        return orderedProperties;
    }

    /**
     * Sets parsed properties of the class.
     *
     * @param incomingProperties class properties
     */
    public void setProperties(List<PropertyModel> incomingProperties) {
        orderedProperties = incomingProperties.toArray(new PropertyModel[] {});
        this.propertyMap = incomingProperties.stream().collect(Collectors.toMap(PropertyModel::getPropertyName, (modifier) -> modifier));
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
     * Default no argument constructor of the class used for deserialization.
     *
     * @return default constructor
     */
    public Constructor<?> getDefaultConstructor() {
        return noArgConstructor;
    }

    private PropertyModel findPropertyModel(ClassDescriptor targetClassDescriptor, String jsonPropertyName) {
        //Standard javabean properties without overridden name (most of the cases)
        final PropertyModel foundProperty = targetClassDescriptor.getPropertyModel(jsonPropertyName);
        if (null != foundProperty && foundProperty.getPropertyName().equals(foundProperty.getReadName())) {
            return foundProperty;
        }
        //Search for overridden name on setter with @JsonbProperty annotation
        for (PropertyModel candidateProperty : propertyMap.values()) {
            if (matchesReadName(jsonPropertyName, candidateProperty)) {
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
    private boolean matchesReadName(String candidateJsonName, PropertyModel candidateProperty) {
        final String resolvedReadName = candidateProperty.getReadName();
        if (StrategiesProvider.CASE_INSENSITIVE_STRATEGY == namingStrategy) {
            return candidateJsonName.equalsIgnoreCase(resolvedReadName);
        }
        return candidateJsonName.equals(resolvedReadName);
    }

    /**
     * Gets type.
     *
     * @return Type.
     */
    public Class<?> getType() {
        return targetType;
    }

    /**
     * Search for field in this class model and superclasses of its class.
     *
     * @param jsonPropertyName name as it appears in JSON during reading.
     * @return PropertyModel if found.
     */
    public PropertyModel findPropertyByJsonReadName(String jsonPropertyName) {
        Objects.requireNonNull(jsonPropertyName);
        return findPropertyModel(this, jsonPropertyName);
    }

    /**
     * Gets a property model by default (non customized) name.
     *
     * @param propertyName A name as parsed from field / getter / setter without annotation customizing.
     * @return Property model.
     */
    public PropertyModel getPropertyModel(String propertyName) {
        return propertyMap.get(propertyName);
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
     * Create instance of class model.
     *
     * @param targetType                  Class to model.
     * @param serializationConfig          Customization of the class parsed from annotations.
     * @param parentDescriptor       Class model of parent class.
     * @param namingStrategy Property naming strategy.
     */
    public ClassDescriptor(Class<?> targetType, ClassSerializationConfig serializationConfig, ClassDescriptor parentDescriptor, PropertyNamingStrategy namingStrategy) {
        this.targetType = targetType;
        this.serializationConfig = serializationConfig;
        this.parentDescriptor = parentDescriptor;
        this.namingStrategy = namingStrategy;
        this.noArgConstructor = ReflectionTypeResolver.getDefaultConstructor(targetType, false);
        setProperties(new ArrayList<>());
    }

}

/*******************************************************************************
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 * <p>
 * Contributors:
 * Dmitry Kornilov - initial implementation
 ******************************************************************************/
package org.eclipse.yasson.internal.model;

import org.eclipse.yasson.internal.model.customization.naming.CaseInsensitiveStrategy;
import org.eclipse.yasson.internal.model.customization.ClassCustomization;

import javax.json.bind.config.PropertyNamingStrategy;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A model for Java class.
 *
 * @author Dmitry Kornilov
 */
public class ClassDescriptor {

    private final Class<?> targetClass;

    private final ClassCustomization customizationConfig;

    private final ClassDescriptor parentDescriptor;

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
     * Gets a property model by default (non customized) name.
     *
     * @param propertyName A name as parsed from field / getter / setter without annotation customizing.
     * @return Property model.
     */
    public PropertyModel getPropertyModel(String propertyName) {
        return propertyMap.get(propertyName);
    }

    /**
     * Create instance of class model.
     *
     * @param targetClass Class to model.
     * @param classConfig Customization of the class parsed from annotations.
     * @param parentDescriptor Class model of parent class.
     * @param namingStrategy Property naming strategy.
     */
    public ClassDescriptor(Class<?> targetClass, ClassCustomization classConfig, ClassDescriptor parentDescriptor, PropertyNamingStrategy namingStrategy) {
        this.targetClass = targetClass;
        this.customizationConfig = classConfig;
        this.parentDescriptor = parentDescriptor;
        this.namingStrategy = namingStrategy;
        setProperties(new ArrayList<>());
    }

    /**
     * Search for field in this class model and superclasses of its class.
     *
     * @param readName name as it appears in JSON during reading.
     * @return PropertyModel if found.
     */
    public PropertyModel findPropertyModelByReadName(String readName) {
        Objects.requireNonNull(readName);
        return findProperty(this, readName);
    }

    private PropertyModel findProperty(ClassDescriptor targetDescriptor, String readName) {
        //Standard javabean properties without overridden name (most of the cases)
        final PropertyModel matchedProperty = targetDescriptor.getPropertyModel(readName);
        if (matchedProperty != null && matchedProperty.getPropertyName().equals(matchedProperty.getReadName())) {
            return matchedProperty;
        }
        //Search for overridden name on setter with @JsonbProperty annotation
        for (PropertyModel candidateProperty : propertyMap.values()) {
            if (isReadNameEqual(readName, candidateProperty)) {
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
    private boolean isReadNameEqual(String expectedName, PropertyModel candidateProperty) {
        final String readName = candidateProperty.getReadName();
        if (namingStrategy instanceof CaseInsensitiveStrategy) {
            return expectedName.equalsIgnoreCase(readName);
        }
        return expectedName.equals(readName);
    }

    /**
     * Gets customization.
     *
     * @return Customization.
     */
    public ClassCustomization getCustomization() {
        return customizationConfig;
    }

    /**
     * Gets type.
     *
     * @return Type.
     */
    public Class<?> getType() {
        return targetClass;
    }

    /**
     * Introspected customization for a class.
     *
     * @return Immutable class customization.
     */
    public ClassCustomization getClassCustomization() {
        return customizationConfig;
    }

    /**
     * Class model of parent class if present.
     * @return class model of a parent
     */
    public ClassDescriptor getParentClassModel() {
        return parentDescriptor;
    }

    /**
     * Get sorted class properties copy, combination of field and its getter / setter, javabeans alike.
     * @return sorted class properties.
     */
    public PropertyModel[] getSortedProperties() {
        return orderedProperties;
    }

    /**
     * Sets parsed properties of the class.
     *
     * @param propertyList class properties
     */
    public void setProperties(List<PropertyModel> propertyList) {
        orderedProperties = propertyList.toArray(new PropertyModel[]{});
        this.propertyMap = propertyList.stream().collect(Collectors.toMap(PropertyModel::getPropertyName, (model) -> model));
    }

    /**
     * Get class properties copy, combination of field and its getter / setter, javabeans alike.
     * @return class properties.
     */
    public Map<String, PropertyModel> getProperties() {
        return Collections.unmodifiableMap(propertyMap);
    }
}

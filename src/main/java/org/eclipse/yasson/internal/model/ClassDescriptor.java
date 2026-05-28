/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  <p>
 *  Contributors:
 *  Dmitry Kornilov - initial implementation
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.model;

import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.model.customization.naming.CaseInsensitiveStrategy;
import org.eclipse.yasson.internal.model.customization.ClassCustomization;
import javax.json.bind.config.PropertyNamingStrategy;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A model for Java class.
 *
 * @author Dmitry Kornilov
 */
public class ClassDescriptor {

    private final Class<?> targetClass;

    private final ClassCustomization typeCustomization;

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
     * Default no argument constructor of the class used for deserialization.
     * @return default constructor
     */
    public Constructor<?> getDefaultConstructor() {
        return noArgConstructor;
    }

    /**
     * Get class properties copy, combination of field and its getter / setter, javabeans alike.
     * @return class properties.
     */
    public Map<String, PropertyModel> getProperties() {
        return Collections.unmodifiableMap(propertyMap);
    }

    /**
     * Class model of parent class if present.
     * @return class model of a parent
     */
    public ClassDescriptor getParentClassModel() {
        return parentDescriptor;
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
     * Search for field in this class model and superclasses of its class.
     *
     * @param jsonReadKey name as it appears in JSON during reading.
     * @return PropertyModel if found.
     */
    public PropertyModel locatePropertyModelByJsonReadName(String jsonReadKey) {
        Objects.requireNonNull(jsonReadKey);
        return findProperty(this, jsonReadKey);
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
     * Sets parsed properties of the class.
     *
     * @param parsedPropertyList class properties
     */
    public void setProperties(List<PropertyModel> parsedPropertyList) {
        orderedProperties = parsedPropertyList.toArray(new PropertyModel[] {});
        this.propertyMap = parsedPropertyList.stream().collect(Collectors.toMap(PropertyModel::getPropertyName, (model) -> model));
    }

    /**
     * Gets customization.
     *
     * @return Customization.
     */
    public ClassCustomization getCustomization() {
        return typeCustomization;
    }

    /**
     * Check if name is equal according to property strategy. In case of {@link CaseInsensitiveStrategy} ignore case.
     * User can provide own strategy implementation, cast to custom interface is not an option.
     *
     * @return True if names are equal.
     */
    private boolean matchesReadName(String jsonPropertyName, PropertyModel propModel) {
        final String readName = propModel.getReadName();
        if (namingStrategy instanceof CaseInsensitiveStrategy) {
            return jsonPropertyName.equalsIgnoreCase(readName);
        }
        return jsonPropertyName.equals(readName);
    }

    /**
     * Get sorted class properties copy, combination of field and its getter / setter, javabeans alike.
     * @return sorted class properties.
     */
    public PropertyModel[] getSortedProperties() {
        return orderedProperties;
    }

    /**
     * Create instance of class model.
     *
     * @param targetClass Class to model.
     * @param customizer Customization of the class parsed from annotations.
     * @param parentDescriptor Class model of parent class.
     * @param namingStrategy Property naming strategy.
     */
    public ClassDescriptor(Class<?> targetClass, ClassCustomization customizer, ClassDescriptor parentDescriptor, PropertyNamingStrategy namingStrategy) {
        this.targetClass = targetClass;
        this.typeCustomization = customizer;
        this.parentDescriptor = parentDescriptor;
        this.namingStrategy = namingStrategy;
        this.noArgConstructor = ReflectionTypeResolver.getDefaultConstructor(targetClass, false);
        setProperties(new ArrayList<>());
    }

    private PropertyModel findProperty(ClassDescriptor descriptor, String jsonReadKey) {
        //Standard javabean properties without overridden name (most of the cases)
        final PropertyModel foundProperty = descriptor.getPropertyModel(jsonReadKey);
        if (null != foundProperty && foundProperty.getPropertyName().equals(foundProperty.getReadName())) {
            return foundProperty;
        }
        //Search for overridden name on setter with @JsonbProperty annotation
        for (PropertyModel propModel : propertyMap.values()) {
            if (matchesReadName(jsonReadKey, propModel)) {
                return propModel;
            }
        }
        //property not found
        return null;
    }

    /**
     * Introspected customization for a class.
     *
     * @return Immutable class customization.
     */
    public ClassCustomization getClassCustomization() {
        return typeCustomization;
    }

}

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

import org.eclipse.yasson.internal.ReflectiveTypeResolver;
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

    private final Class<?> targetType;

    private final ClassCustomization customizationSettings;

    private final ClassDescriptor parentDescriptor;

    private final Constructor<?> noArgConstructor;

    /**
     * A map of all class properties, including properties from superclasses. Used to access by name.
     */
    private Map<String, PropertyDescriptor> propertyMap;

    /**
     * Sorted properties according to sorting strategy. Used for serialization property ordering.
     */
    private PropertyDescriptor[] orderedDescriptors;

    private final PropertyNamingStrategy namingStrategy;

    /**
     * Get class properties copy, combination of field and its getter / setter, javabeans alike.
     * @return class properties.
     */
    public Map<String, PropertyDescriptor> getProperties() {
        return Collections.unmodifiableMap(propertyMap);
    }

    /**
     * Create instance of class model.
     *
     * @param targetType Class to model.
     * @param customizationOptions Customization of the class parsed from annotations.
     * @param parentDescriptor Class model of parent class.
     * @param namingStrategy Property naming strategy.
     */
    public ClassDescriptor(Class<?> targetType, ClassCustomization customizationOptions, ClassDescriptor parentDescriptor, PropertyNamingStrategy namingStrategy) {
        this.targetType = targetType;
        this.customizationSettings = customizationOptions;
        this.parentDescriptor = parentDescriptor;
        this.namingStrategy = namingStrategy;
        this.noArgConstructor = ReflectiveTypeResolver.getDefaultConstructor(targetType, false);
        setProperties(new ArrayList<>());
    }

    /**
     * Check if name is equal according to property strategy. In case of {@link CaseInsensitiveStrategy} ignore case.
     * User can provide own strategy implementation, cast to custom interface is not an option.
     *
     * @return True if names are equal.
     */
    private boolean isReadNameEqual(String jsonPropertyName, PropertyDescriptor propModel) {
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
    public PropertyDescriptor[] getSortedProperties() {
        return orderedDescriptors;
    }

    /**
     * Default no argument constructor of the class used for deserialization.
     * @return default constructor
     */
    public Constructor<?> getDefaultConstructor() {
        return noArgConstructor;
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
    public PropertyDescriptor getPropertyModelByJsonReadName(String jsonPropertyName) {
        Objects.requireNonNull(jsonPropertyName);
        return findProperty(this, jsonPropertyName);
    }

    /**
     * Sets parsed properties of the class.
     *
     * @param parsedDescriptors class properties
     */
    public void setProperties(List<PropertyDescriptor> parsedDescriptors) {
        orderedDescriptors = parsedDescriptors.toArray(new PropertyDescriptor[] {});
        this.propertyMap = parsedDescriptors.stream().collect(Collectors.toMap(PropertyDescriptor::getPropertyName, (modifier) -> modifier));
    }

    private PropertyDescriptor findProperty(ClassDescriptor descriptor, String jsonPropertyName) {
        //Standard javabean properties without overridden name (most of the cases)
        final PropertyDescriptor foundProperty = descriptor.getPropertyModel(jsonPropertyName);
        if (null != foundProperty && foundProperty.getPropertyName().equals(foundProperty.getReadName())) {
            return foundProperty;
        }
        //Search for overridden name on setter with @JsonbProperty annotation
        for (PropertyDescriptor propModel : propertyMap.values()) {
            if (isReadNameEqual(jsonPropertyName, propModel)) {
                return propModel;
            }
        }
        //property not found
        return null;
    }

    /**
     * Gets a property model by default (non customized) name.
     *
     * @param propertyName A name as parsed from field / getter / setter without annotation customizing.
     * @return Property model.
     */
    public PropertyDescriptor getPropertyModel(String propertyName) {
        return propertyMap.get(propertyName);
    }

    /**
     * Gets customization.
     *
     * @return Customization.
     */
    public ClassCustomization getCustomization() {
        return customizationSettings;
    }

    /**
     * Introspected customization for a class.
     *
     * @return Immutable class customization.
     */
    public ClassCustomization getClassCustomization() {
        return customizationSettings;
    }

    /**
     * Class model of parent class if present.
     * @return class model of a parent
     */
    public ClassDescriptor getParentClassModel() {
        return parentDescriptor;
    }

}

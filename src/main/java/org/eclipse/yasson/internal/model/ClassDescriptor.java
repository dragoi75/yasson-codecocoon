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

import org.eclipse.yasson.internal.ReflectionUtils;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.model.customization.naming.CaseInsensitiveStrategy;
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

    private final Class<?> javaType;

    private final ClassSerializationConfig serializationConfig;

    private final ClassDescriptor superDescriptor;

    private final Constructor<?> noArgConstructor;

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
     * Gets a property model by default (non customized) name.
     *
     * @param propName A name as parsed from field / getter / setter without annotation customizing.
     * @return Property model.
     */
    public BeanPropertyDescriptor getPropertyModel(String propName) {
        return propertyMap.get(propName);
    }

    /**
     * Create instance of class model.
     *
     * @param javaType Class to model.
     * @param serializationOptions Customization of the class parsed from annotations.
     * @param superDescriptor Class model of parent class.
     * @param namingStrategy Property naming strategy.
     */
    public ClassDescriptor(Class<?> javaType, ClassSerializationConfig serializationOptions, ClassDescriptor superDescriptor, PropertyNamingStrategy namingStrategy) {
        this.javaType = javaType;
        this.serializationConfig = serializationOptions;
        this.superDescriptor = superDescriptor;
        this.namingStrategy = namingStrategy;
        this.noArgConstructor = ReflectionUtils.getDefaultConstructor(javaType, false);
        setProperties(new ArrayList<>());
    }

    /**
     * Search for field in this class model and superclasses of its class.
     *
     * @param jsonFieldName name as it appears in JSON during reading.
     * @return PropertyModel if found.
     */
    public BeanPropertyDescriptor getPropertyModelByJsonReadName(String jsonFieldName) {
        Objects.requireNonNull(jsonFieldName);
        return findProperty(this, jsonFieldName);
    }

    private BeanPropertyDescriptor findProperty(ClassDescriptor typeDescriptor, String jsonFieldName) {
        //Standard javabean properties without overridden name (most of the cases)
        final BeanPropertyDescriptor foundProperty = typeDescriptor.getPropertyModel(jsonFieldName);
        if (null != foundProperty && foundProperty.getPropertyName().equals(foundProperty.getReadName())) {
            return foundProperty;
        }
        //Search for overridden name on setter with @JsonbProperty annotation
        for (BeanPropertyDescriptor propDescriptor : propertyMap.values()) {
            if (isReadNameEqual(jsonFieldName, propDescriptor)) {
                return propDescriptor;
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
    private boolean isReadNameEqual(String candidateName, BeanPropertyDescriptor propDescriptor) {
        final String resolvedReadName = propDescriptor.getReadName();
        if (namingStrategy instanceof CaseInsensitiveStrategy) {
            return candidateName.equalsIgnoreCase(resolvedReadName);
        }
        return candidateName.equals(resolvedReadName);
    }

    /**
     * Gets customization.
     *
     * @return Customization.
     */
    public ClassSerializationConfig getCustomization() {
        return serializationConfig;
    }

    /**
     * Gets type.
     *
     * @return Type.
     */
    public Class<?> getType() {
        return javaType;
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
     * Class model of parent class if present.
     * @return class model of a parent
     */
    public ClassDescriptor getParentClassModel() {
        return superDescriptor;
    }

    /**
     * Get sorted class properties copy, combination of field and its getter / setter, javabeans alike.
     * @return sorted class properties.
     */
    public BeanPropertyDescriptor[] getSortedProperties() {
        return orderedProperties;
    }

    /**
     * Sets parsed properties of the class.
     *
     * @param extractedProperties class properties
     */
    public void setProperties(List<BeanPropertyDescriptor> extractedProperties) {
        orderedProperties = extractedProperties.toArray(new BeanPropertyDescriptor[] {});
        this.propertyMap = extractedProperties.stream().collect(Collectors.toMap(BeanPropertyDescriptor::getPropertyName, (modifier) -> modifier));
    }

    /**
     * Get class properties copy, combination of field and its getter / setter, javabeans alike.
     * @return class properties.
     */
    public Map<String, BeanPropertyDescriptor> getProperties() {
        return Collections.unmodifiableMap(propertyMap);
    }

    /**
     * Default no argument constructor of the class used for deserialization.
     * @return default constructor
     */
    public Constructor<?> getDefaultConstructor() {
        return noArgConstructor;
    }
}

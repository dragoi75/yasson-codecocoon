/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *      Dmitry Kornilov - initial implementation
 *      Maxence Laurent - parse default methods in interface as properties
 * ****************************************************************************
 */
package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.CreatorModel;
import org.eclipse.yasson.internal.model.JsonbAnnotatedElement;
import org.eclipse.yasson.internal.model.JsonbCreator;
import org.eclipse.yasson.internal.model.Property;
import org.eclipse.yasson.internal.model.PropertyDescriptor;
import org.eclipse.yasson.internal.model.ReflectionPropagation;
import org.eclipse.yasson.internal.model.customization.CreatorCustomization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.Messages;
import javax.json.bind.JsonbException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Created a class internal model.
 *
 * @author Dmitry Kornilov
 */
class ClassParser {

    public static final String IS_PREFIX = "is";

    public static final String GET_PREFIX = "get";

    public static final String SET_PREFIX = "set";

    private final JsonbContext jsonbContext;

    private boolean isGetter(Method m) {
        return (m.getName().startsWith(GET_PREFIX) || m.getName().startsWith(IS_PREFIX)) && 0 == m.getParameterCount();
    }

    private Property mergeProperty(Property current, PropertyDescriptor parentProp, JsonbAnnotatedElement<Class<?>> classElement) {
        Field field = null != current.getField() ? current.getField() : parentProp.getPropagation().getField();
        Method getter = selectMostSpecificNonDefaultMethod(current.getGetter(), parentProp.getPropagation().getGetter());
        Method setter = selectMostSpecificNonDefaultMethod(current.getSetter(), parentProp.getPropagation().getSetter());
        Property merged = new Property(parentProp.getPropertyName(), classElement);
        if (null != field) {
            merged.setField(field);
        }
        if (null != getter) {
            merged.setGetter(getter);
        }
        if (null != setter) {
            merged.setSetter(setter);
        }
        return merged;
    }

    private boolean isPropertyMethod(Method m) {
        return isGetter(m) || isSetter(m);
    }

    private String lowerFirstLetter(String name) {
        Objects.requireNonNull(name);
        if (0 == name.length()) {
            //methods named get() or set()
            return name;
        }
        if (1 < name.length() && Character.isUpperCase(name.charAt(1)) && Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    private void parseMethods(Class<?> clazz, JsonbAnnotatedElement<Class<?>> classElement, Map<String, Property> classProperties) {
        Method[] declaredMethods = AccessController.doPrivileged((PrivilegedAction<Method[]>) clazz::getDeclaredMethods);
        for (Method method : declaredMethods) {
            String name = method.getName();
            //isBridge method filters out methods inherited from interfaces
            if (!isPropertyMethod(method) || method.isBridge()) {
                continue;
            }
            final String propertyName = toPropertyMethod(name);
            Property property = registerMethod(propertyName, method, classElement, classProperties);
        }
    }

    private void checkPropertyNameClash(List<PropertyDescriptor> collectedProperties, Class cls) {
        final List<PropertyDescriptor> checkedProperties = new ArrayList<>();
        for (PropertyDescriptor collectedPropertyModel : collectedProperties) {
            for (PropertyDescriptor checkedPropertyModel : checkedProperties) {
                if ((checkedPropertyModel.getReadName().equals(collectedPropertyModel.getReadName()) && checkedPropertyModel.isReadable() && collectedPropertyModel.isReadable()) || (checkedPropertyModel.getWriteName().equals(collectedPropertyModel.getWriteName())) && checkedPropertyModel.isWritable() && collectedPropertyModel.isWritable()) {
                    throw new JsonbException(Messages.getMessage(MessageKeyConstants.PROPERTY_NAME_CLASH, checkedPropertyModel.getPropertyName(), collectedPropertyModel.getPropertyName(), cls.getName()));
                }
            }
            checkedProperties.add(collectedPropertyModel);
        }
    }

    /**
     * Parse class fields and getters setters. Merge to java bean like properties.
     */
    public void parseProperties(ClassDescriptor classModel, JsonbAnnotatedElement<Class<?>> classElement) {
        final Map<String, Property> classProperties = new HashMap<>();
        parseFields(classElement, classProperties);
        parseClassAndInterfaceMethods(classElement, classProperties);
        //add sorted properties from parent, if they are not overridden in current class
        //parent properties are by default first by alphabet, than properties from a subclass
        final List<PropertyDescriptor> sortedParentProperties = getSortedParentProperties(classModel, classElement, classProperties);
        List<PropertyDescriptor> classPropertyModels = classProperties.values().stream().map(property -> new PropertyDescriptor(classModel, property, jsonbContext)).collect(Collectors.toList());
        //check for collision on same property read name
        List<PropertyDescriptor> unsortedMerged = new ArrayList<>();
        unsortedMerged.addAll(sortedParentProperties);
        unsortedMerged.addAll(classPropertyModels);
        checkPropertyNameClash(unsortedMerged, classModel.getType());
        List<PropertyDescriptor> sortedPropertyModels = new ArrayList<>();
        sortedPropertyModels.addAll(sortedParentProperties);
        sortedPropertyModels.addAll(jsonbContext.getConfigProperties().getPropertyOrdering().orderProperties(classPropertyModels, classModel));
        //reference property to creator parameter by name to merge configuration in runtime
        JsonbCreator creator = classModel.getClassCustomization().getCreator();
        if (null != creator) {
            sortedPropertyModels.forEach((propertyModel -> {
                for (CreatorModel creatorModel : creator.getParams()) {
                    if (creatorModel.getName().equals(propertyModel.getPropertyName())) {
                        CreatorCustomization customization = (CreatorCustomization) creatorModel.getCustomization();
                        customization.setPropertyModel(propertyModel);
                    }
                }
            }));
        }
        classModel.setProperties(sortedPropertyModels);
    }

    private void parseFields(JsonbAnnotatedElement<Class<?>> classElement, Map<String, Property> classProperties) {
        Field[] declaredFields = AccessController.doPrivileged((PrivilegedAction<Field[]>) () -> classElement.getElement().getDeclaredFields());
        for (Field field : declaredFields) {
            final String name = field.getName();
            if (field.isSynthetic()) {
                continue;
            }
            final Property property = new Property(name, classElement);
            property.setField(field);
            classProperties.put(name, property);
        }
    }

    private void parseIfaceMethodAnnotations(Class<?> ifc, JsonbAnnotatedElement<Class<?>> classElement, Map<String, Property> classProperties) {
        Method[] declaredMethods = AccessController.doPrivileged((PrivilegedAction<Method[]>) ifc::getDeclaredMethods);
        for (Method method : declaredMethods) {
            final String methodName = method.getName();
            if (!isPropertyMethod(method)) {
                continue;
            }
            String propertyName = toPropertyMethod(methodName);
            Property property = classProperties.get(propertyName);
            if (method.isDefault()) {
                // Interface provides default implementation
                if (null != property) {
                    // property already exists, take care not overriding already parsed implementation
                    if (!isSetter(method)) {
                        if (null == property.getGetter()) {
                            property.setGetter(method);
                        }
                    } else {
                        if (null == property.getSetter()) {
                            property.setSetter(method);
                        }
                    }
                } else {
                    // the property does not yet exists : create it from scratch
                    property = registerMethod(propertyName, method, classElement, classProperties);
                }
            }
            if (null == property) {
                //May happen for classes which both extend a class with some method and implement interface with same method.
                continue;
            }
            JsonbAnnotatedElement<Method> methodElement = isGetter(method) ? property.getGetterElement() : property.getSetterElement();
            //Only push iface annotations if not overridden on impl classes
            for (Annotation ann : method.getDeclaredAnnotations()) {
                if (null == methodElement.getAnnotation(ann.annotationType())) {
                    methodElement.putAnnotation(ann);
                }
            }
        }
    }

    /**
     * Merges current class properties with parent class properties.
     * If javabean property is declared in more than one inheritance levels,
     * merge field, getters and setters of that property.
     * <p>
     * For example BaseClass contains field foo and getter getFoo. In BaseExtensions there is a setter setFoo.
     * All three will be merged for BaseExtension.
     * <p>
     * Such property is sorted based on where its getter or field is located.
     */
    private List<PropertyDescriptor> getSortedParentProperties(ClassDescriptor classModel, JsonbAnnotatedElement<Class<?>> classElement, Map<String, Property> classProperties) {
        List<PropertyDescriptor> sortedProperties = new ArrayList<>();
        //Pull properties from parent
        if (null != classModel.getParentClassModel()) {
            for (PropertyDescriptor parentProp : classModel.getParentClassModel().getSortedProperties()) {
                final Property current = classProperties.get(parentProp.getPropertyName());
                //don't replace overridden properties
                if (null != current) {
                    //merge
                    final Property merged = mergeProperty(current, parentProp, classElement);
                    ReflectionPropagation propagation = new ReflectionPropagation(current, classModel.getClassCustomization().getPropertyVisibilityStrategy());
                    if (!propagation.isReadable()) {
                        sortedProperties.add(new PropertyDescriptor(classModel, merged, jsonbContext));
                        classProperties.remove(current.getName());
                    } else {
                        classProperties.replace(current.getName(), merged);
                    }
                } else {
                    sortedProperties.add(parentProp);
                }
            }
        }
        return sortedProperties;
    }

    ClassParser(JsonbContext jsonbContext) {
        this.jsonbContext = jsonbContext;
    }

    /**
     * Select the correct method to use. The correct method is the most specific
     * method which is not a default one:
     * <ul>
     * <li> if current is not defined, returns parent;</li>
     * <li> if parent is not defined, returns current;</li>
     * <li> if current is a default method and parent is not, returns parent;</li>
     * <ul>
     * <li><i>By definition, it is not possible to make a choice betweentwo default
     * methods. <br/>Here, the most specific is selected, but a concrete
     * implementation MUST eventually be provided as the source code won't even
     * compile if such a method does not exist</i></li>
     * </ul>
     * <li> returns current otherwise</li>
     * </ul>
     *
     * @param current current 'child' implementation
     * @param parent  parent implementation
     * @return effective method to register as getter or setter
     */
    private Method selectMostSpecificNonDefaultMethod(Method current, Method parent) {
        return (null != current ? (null != parent && current.isDefault() && !parent.isDefault() ? parent : current) : parent);
    }

    private String toPropertyMethod(String name) {
        return lowerFirstLetter(name.substring(name.startsWith(IS_PREFIX) ? 2 : 3, name.length()));
    }

    private Property registerMethod(String propertyName, Method method, JsonbAnnotatedElement<Class<?>> classElement, Map<String, Property> classProperties) {
        Property property = classProperties.computeIfAbsent(propertyName, n -> new Property(n, classElement));
        if (!isSetter(method)) {
            property.setGetter(method);
        } else {
            property.setSetter(method);
        }
        return property;
    }

    private void parseClassAndInterfaceMethods(JsonbAnnotatedElement<Class<?>> classElement, Map<String, Property> classProperties) {
        Class<?> concreteClass = classElement.getElement();
        parseMethods(concreteClass, classElement, classProperties);
        for (Class<?> ifc : jsonbContext.getAnnotationIntrospector().collectInterfaces(concreteClass)) {
            parseIfaceMethodAnnotations(ifc, classElement, classProperties);
        }
    }

    private boolean isSetter(Method m) {
        return m.getName().startsWith(SET_PREFIX) && 1 == m.getParameterCount();
    }

}

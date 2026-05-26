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
package org.eclipse.yasson.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.CreatorProfile;
import org.eclipse.yasson.internal.model.JsonbAnnotationHolder;
import org.eclipse.yasson.internal.model.JsonbInstantiator;
import org.eclipse.yasson.internal.model.PropertyDescriptor;
import org.eclipse.yasson.internal.model.PropertyMetadata;
import org.eclipse.yasson.internal.properties.ErrorMessageKeys;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Created a class internal model.
 */
class ClassModelParser {

    private static final String BOOLEAN_GETTER_PREFIX = "is";

    private static final String GETTER_PREFIX = "get";

    private static final String SETTER_PREFIX = "set";

    private final JsonbRuntimeContext bindingContext;

    private static void collectFields(JsonbAnnotationHolder<Class<?>> annotatedClassHolder, Map<String, PropertyDescriptor> propertyMap) {
        Field[] fieldArray = AccessController.doPrivileged((PrivilegedAction<Field[]>) () -> annotatedClassHolder.getElement().getDeclaredFields());
        for (Field currentMember : fieldArray) {
            final String identifier = currentMember.getName();
            if (currentMember.isSynthetic()) {
                continue;
            }
            final PropertyDescriptor prop = new PropertyDescriptor(identifier, annotatedClassHolder);
            prop.setField(currentMember);
            propertyMap.put(identifier, prop);
        }
    }

    private static String decapitalize(String identifier) {
        Objects.requireNonNull(identifier);
        if (0 == identifier.length()) {
            //methods named get() or set()
            return identifier;
        }
        if (1 < identifier.length() && Character.isUpperCase(identifier.charAt(1)) && Character.isUpperCase(identifier.charAt(0))) {
            return identifier;
        }
        char[] charArray = identifier.toCharArray();
        charArray[0] = Character.toLowerCase(charArray[0]);
        return new String(charArray);
    }

    private static PropertyDescriptor mergePropertyDescriptors(PropertyDescriptor activeDescriptor, PropertyMetadata ancestorModel, JsonbAnnotationHolder<Class<?>> annotatedClassHolder) {
        Field currentMember = null != activeDescriptor.getField() ? activeDescriptor.getField() : ancestorModel.getField();
        Method readMethod = selectMostSpecificMethod(activeDescriptor.getGetter(), ancestorModel.getGetter());
        Method writeMethod = selectMostSpecificMethod(activeDescriptor.getSetter(), ancestorModel.getSetter());
        PropertyDescriptor combinedDescriptor = new PropertyDescriptor(ancestorModel.getPropertyName(), annotatedClassHolder);
        if (null != currentMember) {
            combinedDescriptor.setField(currentMember);
        }
        if (null != readMethod) {
            combinedDescriptor.setGetter(readMethod);
        }
        if (null != writeMethod) {
            combinedDescriptor.setSetter(writeMethod);
        }
        return combinedDescriptor;
    }

    /**
     * Filter out certain methods that get forcibly added to some classes.
     * For example the public groovy.lang.MetaClass X.getMetaClass() method from Groovy classes
     */
    private static boolean isSpecialCaseMethod(Class<?> clazz, Method candidateMember) {
        if (!Modifier.isPublic(candidateMember.getModifiers()) || Modifier.isStatic(candidateMember.getModifiers()) || candidateMember.isSynthetic()) {
            return false;
        }
        // Groovy objects will have public groovy.lang.MetaClass X.getMetaClass()
        // which causes an infinite loop in serialization
        if (candidateMember.getName().equals("getMetaClass") && candidateMember.getReturnType().getCanonicalName().equals("groovy.lang.MetaClass")) {
            return true;
        }
        // WELD proxy objects will have 'public org.jboss.weld
        if (candidateMember.getName().equals("getMetadata") && candidateMember.getReturnType().getCanonicalName().equals("org.jboss.weld.proxy.WeldClientProxy$Metadata")) {
            return true;
        }
        return false;
    }

    private PropertyDescriptor registerPropertyMethod(String propName, Method refMethod, JsonbAnnotationHolder<Class<?>> annotatedClassHolder, Map<String, PropertyDescriptor> propertyMap) {
        PropertyDescriptor prop = propertyMap.computeIfAbsent(propName, nameParam -> new PropertyDescriptor(nameParam, annotatedClassHolder));
        if (!isSetter(refMethod)) {
            prop.setGetter(refMethod);
        } else {
            prop.setSetter(refMethod);
        }
        return prop;
    }

    private static void checkPropertyNameCollision(List<PropertyMetadata> propList, Class<?> targetType) {
        final List<PropertyMetadata> validatedList = new ArrayList<>();
        for (PropertyMetadata incomingModel : propList) {
            for (PropertyMetadata existingModel : validatedList) {
                if ((//
                existingModel.getReadName().equals(incomingModel.getReadName()) && existingModel.isReadable() && incomingModel.isReadable()) || (//
                existingModel.getWriteName().equals(incomingModel.getWriteName()) && existingModel.isWritable() && incomingModel.isWritable())) {
                    throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.PROPERTY_NAME_CLASH, existingModel.getPropertyName(), incomingModel.getPropertyName(), targetType.getName()));
                }
            }
            validatedList.add(incomingModel);
        }
    }

    /**
     * Parse class fields and getters setters. Merge to java bean like properties.
     */
    void extractProperties(ClassDescriptor classDescriptor, JsonbAnnotationHolder<Class<?>> annotatedClassHolder) {
        final Map<String, PropertyDescriptor> propertyMap = new HashMap<>();
        collectFields(annotatedClassHolder, propertyMap);
        parseMethodsFromClassAndInterfaces(annotatedClassHolder, propertyMap);
        //add sorted properties from parent, if they are not overridden in current class
        //parent properties are by default first by alphabet, than properties from a subclass
        final List<PropertyMetadata> parentPropertiesSorted = getSortedParentProperties(classDescriptor, annotatedClassHolder, propertyMap);
        List<PropertyMetadata> propertyModels = propertyMap.values().stream().map(prop -> new PropertyMetadata(classDescriptor, prop, bindingContext)).collect(Collectors.toList());
        //check for collision on same property read name
        List<PropertyMetadata> unsortedList = new ArrayList<>(parentPropertiesSorted.size() + propertyModels.size());
        unsortedList.addAll(parentPropertiesSorted);
        unsortedList.addAll(propertyModels);
        checkPropertyNameCollision(unsortedList, classDescriptor.getType());
        mergeProperties(propertyModels);
        List<PropertyMetadata> sortedModels = new ArrayList<>(parentPropertiesSorted.size() + propertyModels.size());
        sortedModels.addAll(parentPropertiesSorted);
        sortedModels.addAll(bindingContext.getConfigProperties().getPropertyOrdering().sortProperties(propertyModels, classDescriptor));
        //reference property to creator parameter by name to merge configuration in runtime
        JsonbInstantiator instantiator = classDescriptor.getClassCustomization().getCreator();
        if (null != instantiator) {
            sortedModels.forEach(propModel -> {
                for (CreatorProfile creatorProfile : instantiator.getParams()) {
                    if (creatorProfile.getName().equals(propModel.getPropertyName())) {
                        creatorProfile.getCustomization().setPropertyModel(propModel);
                    }
                }
            });
        }
        classDescriptor.setProperties(sortedModels);
    }

    private void parseInterfaceMethodAnnotations(Class<?> interfaceType, JsonbAnnotationHolder<Class<?>> annotatedClassHolder, Map<String, PropertyDescriptor> propertyMap) {
        Method[] methodsArray = AccessController.doPrivileged((PrivilegedAction<Method[]>) interfaceType::getDeclaredMethods);
        for (Method refMethod : methodsArray) {
            final String name = refMethod.getName();
            if (!isPropertyMethod(refMethod)) {
                continue;
            }
            String propName = toPropertyNameFromMethod(name);
            PropertyDescriptor prop = propertyMap.get(propName);
            if (refMethod.isDefault()) {
                // Interface provides default implementation
                if (null != prop) {
                    // property already exists, take care not overriding already parsed implementation
                    if (!isSetter(refMethod)) {
                        if (null == prop.getGetter()) {
                            prop.setGetter(refMethod);
                        }
                    } else {
                        if (null == prop.getSetter()) {
                            prop.setSetter(refMethod);
                        }
                    }
                } else {
                    // the property does not yet exists : create it from scratch
                    prop = registerPropertyMethod(propName, refMethod, annotatedClassHolder, propertyMap);
                }
            }
            if (null == prop) {
                //May happen for classes which both extend a class with some method and implement interface with same method.
                continue;
            }
            JsonbAnnotationHolder<Method> methodHolder = isGetter(refMethod) ? prop.getGetterElement() : prop.getSetterElement();
            //Only push iface annotations if not overridden on impl classes
            for (Annotation annotation : refMethod.getDeclaredAnnotations()) {
                if (null == methodHolder.getAnnotation(annotation.annotationType())) {
                    methodHolder.addAnnotation(annotation);
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
    private List<PropertyMetadata> getSortedParentProperties(ClassDescriptor classDescriptor, JsonbAnnotationHolder<Class<?>> annotatedClassHolder, Map<String, PropertyDescriptor> propertyMap) {
        List<PropertyMetadata> orderedList = new ArrayList<>();
        //Pull properties from parent
        if (null != classDescriptor.getParentClassModel()) {
            for (PropertyMetadata ancestorModel : classDescriptor.getParentClassModel().getSortedProperties()) {
                final PropertyDescriptor activeDescriptor = propertyMap.get(ancestorModel.getPropertyName());
                //don't replace overridden properties
                if (null != activeDescriptor) {
                    //merge
                    final PropertyDescriptor combinedDescriptor = mergePropertyDescriptors(activeDescriptor, ancestorModel, annotatedClassHolder);
                    PropertyVisibilityStrategy visibilityPolicy = classDescriptor.getClassCustomization().getPropertyVisibilityStrategy();
                    if (!PropertyMetadata.isPropertyReadable(activeDescriptor.getField(), activeDescriptor.getGetter(), visibilityPolicy)) {
                        orderedList.add(new PropertyMetadata(classDescriptor, combinedDescriptor, bindingContext));
                        propertyMap.remove(activeDescriptor.getName());
                    } else {
                        propertyMap.replace(activeDescriptor.getName(), combinedDescriptor);
                    }
                } else {
                    orderedList.add(ancestorModel);
                }
            }
        }
        return orderedList;
    }

    private void parseMethodsFromClassAndInterfaces(JsonbAnnotationHolder<Class<?>> annotatedClassHolder, Map<String, PropertyDescriptor> propertyMap) {
        Class<?> actualClass = annotatedClassHolder.getElement();
        collectMethods(actualClass, annotatedClassHolder, propertyMap);
        for (Class<?> interfaceType : bindingContext.getAnnotationIntrospector().gatherInterfaces(actualClass)) {
            parseInterfaceMethodAnnotations(interfaceType, annotatedClassHolder, propertyMap);
        }
    }

    private static void mergeProperties(List<PropertyMetadata> unsortedList) {
        PropertyMetadata[] copies = unsortedList.toArray(new PropertyMetadata[unsortedList.size()]);
        int index = 0;
        while (copies.length > index) {
            int innerIndex = index + 1;
            while (copies.length > innerIndex) {
                if (copies[index].equals(copies[innerIndex])) {
                    // Need to merge two properties
                    unsortedList.remove(copies[index]);
                    unsortedList.remove(copies[innerIndex]);
                    unsortedList.add(new PropertyMetadata(copies[index], copies[innerIndex]));
                }
                innerIndex += 1;
            }
            index += 1;
        }
    }

    private static boolean isGetter(Method candidateMember) {
        return (candidateMember.getName().startsWith(GETTER_PREFIX) || candidateMember.getName().startsWith(BOOLEAN_GETTER_PREFIX)) && 0 == candidateMember.getParameterCount();
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
     * @param activeDescriptor current 'child' implementation
     * @param superCandidate  parent implementation
     * @return effective method to register as getter or setter
     */
    private static Method selectMostSpecificMethod(Method activeDescriptor, Method superCandidate) {
        return (null != activeDescriptor ? (null != superCandidate && activeDescriptor.isDefault() && !superCandidate.isDefault() ? superCandidate : activeDescriptor) : superCandidate);
    }

    ClassModelParser(JsonbRuntimeContext bindingContext) {
        this.bindingContext = bindingContext;
    }

    private void collectMethods(Class<?> targetType, JsonbAnnotationHolder<Class<?>> annotatedClassHolder, Map<String, PropertyDescriptor> propertyMap) {
        Method[] methodsArray = AccessController.doPrivileged((PrivilegedAction<Method[]>) targetType::getDeclaredMethods);
        for (Method refMethod : methodsArray) {
            String identifier = refMethod.getName();
            //isBridge method filters out methods inherited from interfaces
            if (!isPropertyMethod(refMethod) || refMethod.isBridge() || isSpecialCaseMethod(targetType, refMethod)) {
                continue;
            }
            final String propName = toPropertyNameFromMethod(identifier);
            registerPropertyMethod(propName, refMethod, annotatedClassHolder, propertyMap);
        }
    }

    private static boolean isPropertyMethod(Method candidateMember) {
        return isGetter(candidateMember) || isSetter(candidateMember);
    }

    private static boolean isSetter(Method candidateMember) {
        return candidateMember.getName().startsWith(SETTER_PREFIX) && 1 == candidateMember.getParameterCount();
    }

    private static String toPropertyNameFromMethod(String identifier) {
        return decapitalize(identifier.substring(identifier.startsWith(BOOLEAN_GETTER_PREFIX) ? 2 : 3, identifier.length()));
    }

}

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

import org.eclipse.yasson.internal.model.BeanPropertyModel;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.CreatorProfile;
import org.eclipse.yasson.internal.model.JsonbAnnotatedMember;
import org.eclipse.yasson.internal.model.JsonbInstantiator;
import org.eclipse.yasson.internal.model.PropertyDescriptor;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Created a class internal model.
 */
class ClassIntrospector {

    private static final String BOOLEAN_GETTER_PREFIX = "is";

    private static final String GETTER_PREFIX = "get";

    private static final String SETTER_PREFIX = "set";

    private final JsonbContextManager jsonbManager;

    ClassIntrospector(JsonbContextManager jsonbManager) {
        this.jsonbManager = jsonbManager;
    }

    /**
     * Parse class fields and getters setters. Merge to java bean like properties.
     */
    void collectProperties(ClassDescriptor classDescriptor, JsonbAnnotatedMember<Class<?>> annotatedClassMember) {
        final Map<String, PropertyDescriptor> propertyMap = new HashMap<>();
        parseDeclaredFields(annotatedClassMember, propertyMap);
        analyzeClassAndInterfaceMethods(annotatedClassMember, propertyMap);

        //add sorted properties from parent, if they are not overridden in current class
        //parent properties are by default first by alphabet, than properties from a subclass
        final List<BeanPropertyModel> parentPropertyModels = getSortedParentProperties(classDescriptor, annotatedClassMember, propertyMap);

        List<BeanPropertyModel> propertyModelList = propertyMap.values().stream()
                .map(propEntry -> new BeanPropertyModel(classDescriptor, propEntry, jsonbManager))
                .collect(Collectors.toList());

        //check for collision on same property read name
        List<BeanPropertyModel> mergedUnsortedList = new ArrayList<>(parentPropertyModels.size() + propertyModelList.size());
        mergedUnsortedList.addAll(parentPropertyModels);
        mergedUnsortedList.addAll(propertyModelList);
        detectPropertyNameClashes(mergedUnsortedList, classDescriptor.getType());

        mergeProperties(propertyModelList);

        List<BeanPropertyModel> sortedModelList = new ArrayList<>(parentPropertyModels.size() + propertyModelList.size());
        sortedModelList.addAll(parentPropertyModels);
        sortedModelList.addAll(jsonbManager.getConfigProperties().getPropertyOrdering()
                                            .sortProperties(propertyModelList, classDescriptor));

        //reference property to creator parameter by name to merge configuration in runtime
        JsonbInstantiator instantiator = classDescriptor.getClassCustomization().getCreator();
        if (instantiator != null) {
            sortedModelList.forEach(propModel -> {
                for (CreatorProfile creatorProfile : instantiator.getParams()) {
                    if (creatorProfile.getName().equals(propModel.getPropertyName())) {
                        creatorProfile.getCustomization().setPropertyModel(propModel);
                    }
                }
            });
        }

        classDescriptor.setProperties(sortedModelList);

    }

    private static void mergeProperties(List<BeanPropertyModel> mergedUnsortedList) {
        BeanPropertyModel[] clonedArray = mergedUnsortedList.toArray(new BeanPropertyModel[mergedUnsortedList.size()]);
        for (int idx = 0; idx < clonedArray.length; idx++) {
            for (int jdx = idx + 1; jdx < clonedArray.length; jdx++) {
                if (clonedArray[idx].equals(clonedArray[jdx])) {
                    // Need to merge two properties
                    mergedUnsortedList.remove(clonedArray[idx]);
                    mergedUnsortedList.remove(clonedArray[jdx]);
                    mergedUnsortedList.add(new BeanPropertyModel(clonedArray[idx], clonedArray[jdx]));
                }
            }
        }
    }

    private void analyzeClassAndInterfaceMethods(JsonbAnnotatedMember<Class<?>> annotatedClassMember,
                                                 Map<String, PropertyDescriptor> propertyMap) {
        Class<?> actualClass = annotatedClassMember.getElement();
        parseDeclaredMethods(actualClass, annotatedClassMember, propertyMap);
        for (Class<?> iface : jsonbManager.getAnnotationIntrospector().collectAllInterfaces(actualClass)) {
            parseInterfaceMethodAnnotations(iface, annotatedClassMember, propertyMap);
        }
    }

    private void parseInterfaceMethodAnnotations(Class<?> iface,
                                                 JsonbAnnotatedMember<Class<?>> annotatedClassMember,
                                                 Map<String, PropertyDescriptor> propertyMap) {
        Method[] methodsArray = AccessController.doPrivileged((PrivilegedAction<Method[]>) iface::getDeclaredMethods);
        for (Method m : methodsArray) {
            final String name = m.getName();
            if (!isPropertyMethod(m)) {
                continue;
            }
            String propName = methodToPropertyName(name);

            PropertyDescriptor propEntry = propertyMap.get(propName);

            if (m.isDefault()) {
                // Interface provides default implementation
                if (propEntry == null) {
                    // the property does not yet exists : create it from scratch
                    propEntry = registerPropertyMethod(propName, m, annotatedClassMember, propertyMap);
                } else {
                    // property already exists, take care not overriding already parsed implementation
                    if (isSetter(m)) {
                        if (propEntry.getSetter() == null) {
                            propEntry.setSetter(m);
                        }
                    } else {
                        if (propEntry.getGetter() == null) {
                            propEntry.setGetter(m);
                        }
                    }
                }
            }

            if (propEntry == null) {
                //May happen for classes which both extend a class with some method and implement interface with same method.
                continue;
            }
            JsonbAnnotatedMember<Method> annotatedMember = isGetter(m)
                    ? propEntry.getGetterElement() : propEntry.getSetterElement();
            //Only push iface annotations if not overridden on impl classes
            for (Annotation annotationInstance : m.getDeclaredAnnotations()) {
                if (annotatedMember.getAnnotation(annotationInstance.annotationType()) == null) {
                    annotatedMember.addAnnotation(annotationInstance);
                }
            }
        }
    }

    private PropertyDescriptor registerPropertyMethod(String propName,
                                                      Method m,
                                                      JsonbAnnotatedMember<Class<?>> annotatedClassMember,
                                                      Map<String, PropertyDescriptor> propertyMap) {
        PropertyDescriptor propEntry = propertyMap.computeIfAbsent(propName, num -> new PropertyDescriptor(num, annotatedClassMember));
        if (isSetter(m)) {
            propEntry.setSetter(m);
        } else {
            propEntry.setGetter(m);
        }

        return propEntry;
    }

    private void parseDeclaredMethods(Class<?> targetType,
                                      JsonbAnnotatedMember<Class<?>> annotatedClassMember,
                                      Map<String, PropertyDescriptor> propertyMap) {
        Method[] methodsArray = AccessController.doPrivileged((PrivilegedAction<Method[]>) targetType::getDeclaredMethods);
        for (Method m : methodsArray) {
            String identifier = m.getName();
            //isBridge method filters out methods inherited from interfaces
            if (!isPropertyMethod(m) || m.isBridge() || isSpecialCaseMethod(targetType, m)) {
                continue;
            }
            final String propName = methodToPropertyName(identifier);

            registerPropertyMethod(propName, m, annotatedClassMember, propertyMap);
        }
    }

    /**
     * Filter out certain methods that get forcibly added to some classes.
     * For example the public groovy.lang.MetaClass X.getMetaClass() method from Groovy classes
     */
    private static boolean isSpecialCaseMethod(Class<?> clazz, Method methodRef) {
        if (!Modifier.isPublic(methodRef.getModifiers()) || Modifier.isStatic(methodRef.getModifiers()) || methodRef.isSynthetic()) {
            return false;
        }
        // Groovy objects will have public groovy.lang.MetaClass X.getMetaClass()
        // which causes an infinite loop in serialization
        if (methodRef.getName().equals("getMetaClass")
                && methodRef.getReturnType().getCanonicalName().equals("groovy.lang.MetaClass")) {
            return true;
        }
        // WELD proxy objects will have 'public org.jboss.weld
        if (methodRef.getName().equals("getMetadata")
                && methodRef.getReturnType().getCanonicalName().equals("org.jboss.weld.proxy.WeldClientProxy$Metadata")) {
            return true;
        }
        return false;
    }

    private static boolean isGetter(Method methodRef) {
        return (methodRef.getName().startsWith(GETTER_PREFIX) || methodRef.getName().startsWith(BOOLEAN_GETTER_PREFIX)) && methodRef.getParameterCount() == 0;
    }

    private static boolean isSetter(Method methodRef) {
        return methodRef.getName().startsWith(SETTER_PREFIX) && methodRef.getParameterCount() == 1;
    }

    private static String methodToPropertyName(String identifier) {
        return decapitalize(identifier.substring(identifier.startsWith(BOOLEAN_GETTER_PREFIX) ? 2 : 3, identifier.length()));
    }

    private static String decapitalize(String identifier) {
        Objects.requireNonNull(identifier);
        if (identifier.length() == 0) {
            //methods named get() or set()
            return identifier;
        }
        if (identifier.length() > 1
                && Character.isUpperCase(identifier.charAt(1))
                && Character.isUpperCase(identifier.charAt(0))) {
            return identifier;
        }
        char[] charBuffer = identifier.toCharArray();
        charBuffer[0] = Character.toLowerCase(charBuffer[0]);
        return new String(charBuffer);
    }

    private static boolean isPropertyMethod(Method methodRef) {
        return isGetter(methodRef) || isSetter(methodRef);
    }

    private static void parseDeclaredFields(JsonbAnnotatedMember<Class<?>> annotatedClassMember, Map<String, PropertyDescriptor> propertyMap) {
        Field[] fieldsArray = AccessController.doPrivileged(
                (PrivilegedAction<Field[]>) () -> annotatedClassMember.getElement().getDeclaredFields());
        for (Field member : fieldsArray) {
            final String identifier = member.getName();
            if (member.isSynthetic()) {
                continue;
            }
            final PropertyDescriptor propEntry = new PropertyDescriptor(identifier, annotatedClassMember);
            propEntry.setField(member);
            propertyMap.put(identifier, propEntry);
        }
    }

    private static void detectPropertyNameClashes(List<BeanPropertyModel> propertyModels, Class<?> targetType) {
        final List<BeanPropertyModel> verifiedList = new ArrayList<>();
        for (BeanPropertyModel gatheredModel : propertyModels) {
            for (BeanPropertyModel existingModel : verifiedList) {
                if ((existingModel.getReadName().equals(gatheredModel.getReadName())
                        && existingModel.isReadable() //
                        && gatheredModel.isReadable())
                        || (existingModel.getWriteName().equals(gatheredModel.getWriteName())
                                && existingModel.isWritable() //
                                && gatheredModel.isWritable())) {
                    throw new JsonbException(
                            LocalizedMessages.getMessage(MessageKeyConstants.PROPERTY_NAME_CLASH, existingModel.getPropertyName(),
                                    gatheredModel.getPropertyName(), targetType.getName()));
                }
            }
            verifiedList.add(gatheredModel);
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
    private List<BeanPropertyModel> getSortedParentProperties(ClassDescriptor classDescriptor,
                                                              JsonbAnnotatedMember<Class<?>> annotatedClassMember,
                                                              Map<String, PropertyDescriptor> propertyMap) {
        List<BeanPropertyModel> orderedProperties = new ArrayList<>();
        //Pull properties from parent
        if (classDescriptor.getParentClassModel() != null) {
            for (BeanPropertyModel parentModel : classDescriptor.getParentClassModel().getSortedProperties()) {
                final PropertyDescriptor activeDescriptor = propertyMap.get(parentModel.getPropertyName());
                //don't replace overridden properties
                if (activeDescriptor == null) {
                    orderedProperties.add(parentModel);
                } else {
                    //merge
                    final PropertyDescriptor combinedDescriptor = mergePropertyDescriptor(activeDescriptor, parentModel, annotatedClassMember);
                    PropertyVisibilityStrategy visibilityStrategy = classDescriptor.getClassCustomization().getPropertyVisibilityStrategy();
                    
                    if (BeanPropertyModel.isPropertyReadable(activeDescriptor.getField(), activeDescriptor.getGetter(), visibilityStrategy)) {
                        propertyMap.replace(activeDescriptor.getName(), combinedDescriptor);
                    } else {
                        orderedProperties.add(new BeanPropertyModel(classDescriptor, combinedDescriptor, jsonbManager));
                        propertyMap.remove(activeDescriptor.getName());
                    }

                }
            }
        }
        return orderedProperties;
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
     * @param superMethod  parent implementation
     * @return effective method to register as getter or setter
     */
    private static Method chooseMostSpecificNonDefaultMethod(Method activeDescriptor, Method superMethod) {
        return (
                activeDescriptor != null ? (
                        superMethod != null && activeDescriptor.isDefault()
                                && !superMethod.isDefault() ? superMethod : activeDescriptor) : superMethod);
    }

    private static PropertyDescriptor mergePropertyDescriptor(PropertyDescriptor activeDescriptor, BeanPropertyModel parentModel, JsonbAnnotatedMember<Class<?>> annotatedClassMember) {
        Field member = activeDescriptor.getField() != null
                ? activeDescriptor.getField() : parentModel.getField();
        Method accessorMethod = chooseMostSpecificNonDefaultMethod(activeDescriptor.getGetter(),
                                                           parentModel.getGetter());
        Method mutatorMethod = chooseMostSpecificNonDefaultMethod(activeDescriptor.getSetter(),
                                                           parentModel.getSetter());

        PropertyDescriptor combinedDescriptor = new PropertyDescriptor(parentModel.getPropertyName(), annotatedClassMember);
        if (member != null) {
            combinedDescriptor.setField(member);
        }
        if (accessorMethod != null) {
            combinedDescriptor.setGetter(accessorMethod);
        }
        if (mutatorMethod != null) {
            combinedDescriptor.setSetter(mutatorMethod);
        }
        return combinedDescriptor;
    }
}

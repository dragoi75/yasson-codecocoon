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

import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.CreatorProfile;
import org.eclipse.yasson.internal.model.JsonbAnnotationHolder;
import org.eclipse.yasson.internal.model.JsonbCreatorInvoker;
import org.eclipse.yasson.internal.model.PropertyDescriptor;
import org.eclipse.yasson.internal.model.PropertyModel;
import org.eclipse.yasson.internal.model.ReflectionPropagation;
import org.eclipse.yasson.internal.model.customization.CreatorCustomization;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Created a class internal model.
 */
class ClassModelParser {

    private static final String PREDICATE_PREFIX = "is";

    private static final String ACCESSOR_PREFIX = "get";

    private static final String MUTATOR_PREFIX = "set";

    private final JsonbRuntimeContext runtimeContext;

    ClassModelParser(JsonbRuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    /**
     * Parse class fields and getters setters. Merge to java bean like properties.
     */
    void collectProperties(ClassDescriptor classDescriptor, JsonbAnnotationHolder<Class<?>> annotationHolder) {
        final Map<String, PropertyDescriptor> propertyMap = new HashMap<>();
        collectFields(annotationHolder, propertyMap);
        parseClassAndInterfaceMethodsImpl(annotationHolder, propertyMap);

        //add sorted properties from parent, if they are not overridden in current class
        //parent properties are by default first by alphabet, than properties from a subclass
        final List<PropertyModel> parentPropertiesSorted = getSortedParentProperties(classDescriptor, annotationHolder, propertyMap);

        List<PropertyModel> propertyModelsList = propertyMap.values().stream()
                .map(propModel -> new PropertyModel(classDescriptor, propModel, runtimeContext))
                .collect(Collectors.toList());

        //check for collision on same property read name
        List<PropertyModel> mergedUnsortedList = new ArrayList<>(parentPropertiesSorted.size() + propertyModelsList.size());
        mergedUnsortedList.addAll(parentPropertiesSorted);
        mergedUnsortedList.addAll(propertyModelsList);
        detectPropertyNameClash(mergedUnsortedList, classDescriptor.getType());

        combinePropertyModels(propertyModelsList);

        List<PropertyModel> orderedPropertyModels = new ArrayList<>(parentPropertiesSorted.size() + propertyModelsList.size());
        orderedPropertyModels.addAll(parentPropertiesSorted);
        orderedPropertyModels.addAll(runtimeContext.getConfigProperties().getPropertyOrdering()
                                            .orderProperties(propertyModelsList, classDescriptor));

        //reference property to creator parameter by name to merge configuration in runtime
        JsonbCreatorInvoker factoryInvoker = classDescriptor.getClassCustomization().getCreator();
        if (factoryInvoker != null) {
            orderedPropertyModels.forEach(modelParam -> {
                for (CreatorProfile creationProfile : factoryInvoker.getParams()) {
                    if (creationProfile.getName().equals(modelParam.getPropertyName())) {
                        CreatorCustomization customizationProfile = (CreatorCustomization) creationProfile.getCustomization();
                        customizationProfile.setPropertyModel(modelParam);
                    }
                }
            });
        }

        classDescriptor.setProperties(orderedPropertyModels);

    }

    private void combinePropertyModels(List<PropertyModel> mergedUnsortedList) {
        PropertyModel[] clonedArray = mergedUnsortedList.toArray(new PropertyModel[mergedUnsortedList.size()]);
        for (int idx = 0; idx < clonedArray.length; idx++) {
            for (int jdx = idx + 1; jdx < clonedArray.length; jdx++) {
                if (clonedArray[idx].equals(clonedArray[jdx])) {
                    // Need to merge two properties
                    mergedUnsortedList.remove(clonedArray[idx]);
                    mergedUnsortedList.remove(clonedArray[jdx]);
                    mergedUnsortedList.add(new PropertyModel(clonedArray[idx], clonedArray[jdx]));
                }
            }
        }
    }

    private void parseClassAndInterfaceMethodsImpl(JsonbAnnotationHolder<Class<?>> annotationHolder,
                                                   Map<String, PropertyDescriptor> propertyMap) {
        Class<?> concreteType = annotationHolder.getElement();
        collectMethods(concreteType, annotationHolder, propertyMap);
        for (Class<?> iface : runtimeContext.getAnnotationIntrospector().collectAllInterfaces(concreteType)) {
            parseInterfaceMethodAnnotations(iface, annotationHolder, propertyMap);
        }
    }

    private void parseInterfaceMethodAnnotations(Class<?> iface,
                                                 JsonbAnnotationHolder<Class<?>> annotationHolder,
                                                 Map<String, PropertyDescriptor> propertyMap) {
        Method[] methodsArray = AccessController.doPrivileged((PrivilegedAction<Method[]>) iface::getDeclaredMethods);
        for (Method m : methodsArray) {
            final String name = m.getName();
            if (!isPropertyMethod(m)) {
                continue;
            }
            String propKey = toPropertyName(name);

            PropertyDescriptor propModel = propertyMap.get(propKey);

            if (m.isDefault()) {
                // Interface provides default implementation
                if (propModel == null) {
                    // the property does not yet exists : create it from scratch
                    propModel = registerPropertyMethod(propKey, m, annotationHolder, propertyMap);
                } else {
                    // property already exists, take care not overriding already parsed implementation
                    if (isSetter(m)) {
                        if (propModel.getSetter() == null) {
                            propModel.setSetter(m);
                        }
                    } else {
                        if (propModel.getGetter() == null) {
                            propModel.setGetter(m);
                        }
                    }
                }
            }

            if (propModel == null) {
                //May happen for classes which both extend a class with some method and implement interface with same method.
                continue;
            }
            JsonbAnnotationHolder<Method> methodAnnotationHolder = isGetter(m)
                    ? propModel.getGetterElement() : propModel.getSetterElement();
            //Only push iface annotations if not overridden on impl classes
            for (Annotation annotation : m.getDeclaredAnnotations()) {
                if (methodAnnotationHolder.getAnnotation(annotation.annotationType()) == null) {
                    methodAnnotationHolder.addAnnotation(annotation);
                }
            }
        }
    }

    private PropertyDescriptor registerPropertyMethod(String name,
                                                      Method m,
                                                      JsonbAnnotationHolder<Class<?>> annotationHolder,
                                                      Map<String, PropertyDescriptor> propertyMap) {
        PropertyDescriptor propModel = propertyMap.computeIfAbsent(name, idx -> new PropertyDescriptor(idx, annotationHolder));
        if (isSetter(m)) {
            propModel.setSetter(m);
        } else {
            propModel.setGetter(m);
        }

        return propModel;
    }

    private void collectMethods(Class<?> targetType,
                                JsonbAnnotationHolder<Class<?>> annotationHolder,
                                Map<String, PropertyDescriptor> propertyMap) {
        Method[] methodsArray = AccessController.doPrivileged((PrivilegedAction<Method[]>) targetType::getDeclaredMethods);
        for (Method m : methodsArray) {
            String identifier = m.getName();
            //isBridge method filters out methods inherited from interfaces
            if (!isPropertyMethod(m) || m.isBridge() || isSpecialCaseMethod(targetType, m)) {
                continue;
            }
            final String name = toPropertyName(identifier);

            registerPropertyMethod(name, m, annotationHolder, propertyMap);
        }
    }

    /**
     * Filter out certain methods that get forcibly added to some classes.
     * For example the public groovy.lang.MetaClass X.getMetaClass() method from Groovy classes
     */
    private boolean isSpecialCaseMethod(Class<?> clazz, Method candidateMethod) {
        if (!Modifier.isPublic(candidateMethod.getModifiers()) || Modifier.isStatic(candidateMethod.getModifiers()) || candidateMethod.isSynthetic()) {
            return false;
        }
        // Groovy objects will have public groovy.lang.MetaClass X.getMetaClass()
        // which causes an infinite loop in serialization
        if (candidateMethod.getName().equals("getMetaClass")
                && candidateMethod.getReturnType().getCanonicalName().equals("groovy.lang.MetaClass")) {
            return true;
        }
        // WELD proxy objects will have 'public org.jboss.weld
        if (candidateMethod.getName().equals("getMetadata")
                && candidateMethod.getReturnType().getCanonicalName().equals("org.jboss.weld.proxy.WeldClientProxy$Metadata")) {
            return true;
        }
        return false;
    }

    private boolean isGetter(Method candidateMethod) {
        return (candidateMethod.getName().startsWith(ACCESSOR_PREFIX) || candidateMethod.getName().startsWith(PREDICATE_PREFIX)) && candidateMethod.getParameterCount() == 0;
    }

    private boolean isSetter(Method candidateMethod) {
        return candidateMethod.getName().startsWith(MUTATOR_PREFIX) && candidateMethod.getParameterCount() == 1;
    }

    private String toPropertyName(String identifier) {
        return decapitalize(identifier.substring(identifier.startsWith(PREDICATE_PREFIX) ? 2 : 3, identifier.length()));
    }

    private String decapitalize(String identifier) {
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
        char[] charArray = identifier.toCharArray();
        charArray[0] = Character.toLowerCase(charArray[0]);
        return new String(charArray);
    }

    private boolean isPropertyMethod(Method candidateMethod) {
        return isGetter(candidateMethod) || isSetter(candidateMethod);
    }

    private void collectFields(JsonbAnnotationHolder<Class<?>> annotationHolder, Map<String, PropertyDescriptor> propertyMap) {
        Field[] fieldArray = AccessController.doPrivileged(
                (PrivilegedAction<Field[]>) () -> annotationHolder.getElement().getDeclaredFields());
        for (Field member : fieldArray) {
            final String identifier = member.getName();
            if (member.isSynthetic()) {
                continue;
            }
            final PropertyDescriptor propModel = new PropertyDescriptor(identifier, annotationHolder);
            propModel.setField(member);
            propertyMap.put(identifier, propModel);
        }
    }

    private void detectPropertyNameClash(List<PropertyModel> collectedModels, Class<?> targetType) {
        final List<PropertyModel> verifiedModels = new ArrayList<>();
        for (PropertyModel candidateModel : collectedModels) {
            for (PropertyModel verifiedModel : verifiedModels) {
                if ((verifiedModel.getReadName().equals(candidateModel.getReadName())
                        && verifiedModel.isReadable() //
                        && candidateModel.isReadable())
                        || (verifiedModel.getWriteName().equals(candidateModel.getWriteName())
                                && verifiedModel.isWritable() //
                                && candidateModel.isWritable())) {
                    throw new JsonbException(
                            MessageBundle.getMessage(MessageKeyConstants.PROPERTY_NAME_CLASH, verifiedModel.getPropertyName(),
                                    candidateModel.getPropertyName(), targetType.getName()));
                }
            }
            verifiedModels.add(candidateModel);
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
    private List<PropertyModel> getSortedParentProperties(ClassDescriptor classDescriptor,
                                                          JsonbAnnotationHolder<Class<?>> annotationHolder,
                                                          Map<String, PropertyDescriptor> propertyMap) {
        List<PropertyModel> sortedList = new ArrayList<>();
        //Pull properties from parent
        if (classDescriptor.getParentClassModel() != null) {
            for (PropertyModel parentModel : classDescriptor.getParentClassModel().getSortedProperties()) {
                final PropertyDescriptor activeDescriptor = propertyMap.get(parentModel.getPropertyName());
                //don't replace overridden properties
                if (activeDescriptor == null) {
                    sortedList.add(parentModel);
                } else {
                    //merge
                    final PropertyDescriptor combinedDescriptor = mergePropertyDescriptors(activeDescriptor, parentModel, annotationHolder);
                    ReflectionPropagation reflectionHandler = new ReflectionPropagation(activeDescriptor,
                                                                                  classDescriptor.getClassCustomization()
                                                                                          .getPropertyVisibilityStrategy());
                    if (reflectionHandler.isReadable()) {
                        propertyMap.replace(activeDescriptor.getName(), combinedDescriptor);
                    } else {
                        sortedList.add(new PropertyModel(classDescriptor, combinedDescriptor, runtimeContext));
                        propertyMap.remove(activeDescriptor.getName());
                    }

                }
            }
        }
        return sortedList;
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
     * @param ancestorMethod  parent implementation
     * @return effective method to register as getter or setter
     */
    private Method selectMostSpecificNonDefault(Method activeDescriptor, Method ancestorMethod) {
        return (
                activeDescriptor != null ? (
                        ancestorMethod != null && activeDescriptor.isDefault()
                                && !ancestorMethod.isDefault() ? ancestorMethod : activeDescriptor) : ancestorMethod);
    }

    private PropertyDescriptor mergePropertyDescriptors(PropertyDescriptor activeDescriptor, PropertyModel parentModel, JsonbAnnotationHolder<Class<?>> annotationHolder) {
        Field member = activeDescriptor.getField() != null
                ? activeDescriptor.getField() : parentModel.getPropagation().getField();
        Method accessorMethod = selectMostSpecificNonDefault(activeDescriptor.getGetter(),
                                                           parentModel.getPropagation().getGetter());
        Method mutatorMethod = selectMostSpecificNonDefault(activeDescriptor.getSetter(),
                                                           parentModel.getPropagation().getSetter());

        PropertyDescriptor combinedDescriptor = new PropertyDescriptor(parentModel.getPropertyName(), annotationHolder);
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

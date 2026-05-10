/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.model.customization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.config.PropertyOrderStrategy;

import org.eclipse.yasson.internal.model.BeanPropertyModel;
import org.eclipse.yasson.internal.model.ClassDescriptor;

/**
 * Order properties in bean object. {@link jakarta.json.bind.annotation.JsonbPropertyOrder} have always precedence.
 * If configured with {@link JsonbConfig} provided property order strategy will be used.
 */
public class PropertyOrderer {

    private final Consumer<List<BeanPropertyModel>> propertiesOrderConsumer;

    /**
     * Creates a new instance.
     *
     * @param propertiesOrderConsumer Property order strategy. Must be not null.
     */
    public PropertyOrderer(Consumer<List<BeanPropertyModel>> propertiesOrderConsumer) {
        this.propertiesOrderConsumer = Objects.requireNonNull(propertiesOrderConsumer);
    }

    /**
     * Sorts class properties either, by class {@link jakarta.json.bind.annotation.JsonbPropertyOrder} annotation,
     * or by {@link PropertyOrderStrategy} if set in {@link JsonbConfig}.
     *
     * @param beanPropertyList Properties to sort.
     * @param classDescriptor Class model.
     * @return Sorted list of properties.
     */
    public List<BeanPropertyModel> sortProperties(List<BeanPropertyModel> beanPropertyList, ClassDescriptor classDescriptor) {
        Map<String, BeanPropertyModel> readNameToPropertyMap = new HashMap<>();
        beanPropertyList.forEach(beanProperty -> readNameToPropertyMap.put(beanProperty.getPropertyName(), beanProperty));

        String[] orderSequence = classDescriptor.getClassCustomization().getPropertyOrder();
        List<BeanPropertyModel> orderedProperties = new ArrayList<>();
        if (orderSequence != null) {
            //if @JsonbPropertyOrder annotation is defined on a class
            for (String propertyName : orderSequence) {
                final BeanPropertyModel propertyToRemove = readNameToPropertyMap.remove(propertyName);
                if (propertyToRemove != null) {
                    orderedProperties.add(propertyToRemove);
                }
            }
        }

        List<BeanPropertyModel> readNamesList = new ArrayList<>(readNameToPropertyMap.values());
        propertiesOrderConsumer.accept(readNamesList);
        orderedProperties.addAll(readNamesList);
        return orderedProperties;
    }
}

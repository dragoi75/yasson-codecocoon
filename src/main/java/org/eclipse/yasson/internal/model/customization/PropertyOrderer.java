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
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.PropertyMetadata;

/**
 * Order properties in bean object. {@link jakarta.json.bind.annotation.JsonbPropertyOrder} have always precedence.
 * If configured with {@link JsonbConfig} provided property order strategy will be used.
 */
public class PropertyOrderer {

    private final Consumer<List<PropertyMetadata>> propertySorter;

    /**
     * Creates a new instance.
     *
     * @param propertySorter Property order strategy. Must be not null.
     */
    public PropertyOrderer(Consumer<List<PropertyMetadata>> propertySorter) {
        this.propertySorter = Objects.requireNonNull(propertySorter);
    }

    /**
     * Sorts class properties either, by class {@link jakarta.json.bind.annotation.JsonbPropertyOrder} annotation,
     * or by {@link PropertyOrderStrategy} if set in {@link JsonbConfig}.
     *
     * @param propertyList Properties to sort.
     * @param classDescriptor Class model.
     * @return Sorted list of properties.
     */
    public List<PropertyMetadata> sortProperties(List<PropertyMetadata> propertyList, ClassDescriptor classDescriptor) {
        Map<String, PropertyMetadata> readNameToPropertyMap = new HashMap<>();
        propertyList.forEach(propertyMeta -> readNameToPropertyMap.put(propertyMeta.getPropertyName(), propertyMeta));
        String[] orderingSequence = classDescriptor.getClassCustomization().getPropertyOrder();
        List<PropertyMetadata> sortedPropertyList = new ArrayList<>();
        if (null != orderingSequence) {
            //if @JsonbPropertyOrder annotation is defined on a class
            for (String propertyName : orderingSequence) {
                final PropertyMetadata removedProperty = readNameToPropertyMap.remove(propertyName);
                if (null != removedProperty) {
                    sortedPropertyList.add(removedProperty);
                }
            }
        }
        List<PropertyMetadata> readNameCandidates = new ArrayList<>(readNameToPropertyMap.values());
        propertySorter.accept(readNameCandidates);
        sortedPropertyList.addAll(readNameCandidates);
        return sortedPropertyList;
    }
}

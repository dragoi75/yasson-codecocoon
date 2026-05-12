/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/
package org.eclipse.yasson.internal.model.customization.ordering;

import org.eclipse.yasson.internal.model.BeanPropertyDescriptor;
import org.eclipse.yasson.internal.model.ClassDescriptor;

import javax.json.bind.JsonbConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Order properties in bean object. {@link javax.json.bind.annotation.JsonbPropertyOrder} have always precedence.
 * If configured with {@link JsonbConfig} provided property order strategy will be used.
 *
 * @author Roman Grigoriadi
 */
public class PropertyOrderer {

    private PropertyOrderStrategy orderStrategy;

    /**
     * Creates a new instance.
     *
     * @param orderStrategy Property order strategy. Must be not null.
     */
    public PropertyOrderer(PropertyOrderStrategy orderStrategy) {
        Objects.requireNonNull(orderStrategy);
        this.orderStrategy = orderStrategy;
    }

    /**
     * Sorts class properties either, by class {@link javax.json.bind.annotation.JsonbPropertyOrder} annotation,
     * or by {@link javax.json.bind.config.PropertyOrderStrategy} if set in {@link JsonbConfig}.
     *
     * @param propertyList Properties to sort.
     * @param typeDescriptor Class model.
     * @return Sorted list of properties.
     */
    public List<BeanPropertyDescriptor> sortProperties(List<BeanPropertyDescriptor> propertyList, ClassDescriptor typeDescriptor) {
        Map<String, BeanPropertyDescriptor> readNameMap = new HashMap<>();
        propertyList.stream().forEach(propDescriptor -> readNameMap.put(propDescriptor.getReadName(), propDescriptor));

        String[] sequence = typeDescriptor.getClassCustomization().getPropertyOrder();
        List<BeanPropertyDescriptor> orderedPropertyList = new ArrayList<>();
        if (sequence != null) {
            //if @JsonbPropertyOrder annotation is defined on a class
            for (String propertyName : sequence) {
                final BeanPropertyDescriptor removedProperty = readNameMap.remove(propertyName);
                if (removedProperty != null) {
                    orderedPropertyList.add(removedProperty);
                }
            }
        }

        orderedPropertyList.addAll(orderStrategy.orderProperties(readNameMap.values()));
        return orderedPropertyList;

    }

    /**
     * Returns a property order strategy from {@link JsonbConfig}.
     *
     * @return {@link PropertyOrderStrategy} or null if not present.
     */
    public PropertyOrderStrategy getPropertyOrderStrategy() {
        return orderStrategy;
    }
}

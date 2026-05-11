/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.model.customization.ordering;

import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.PropertyModel;
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
public class PropertyOrderManager {

    private PropertyOrderStrategy orderStrategy;

    /**
     * Creates a new instance.
     *
     * @param orderStrategy Property order strategy. Must be not null.
     */
    public PropertyOrderManager(PropertyOrderStrategy orderStrategy) {
        Objects.requireNonNull(orderStrategy);
        this.orderStrategy = orderStrategy;
    }

    /**
     * Sorts class properties either, by class {@link javax.json.bind.annotation.JsonbPropertyOrder} annotation,
     * or by {@link javax.json.bind.config.PropertyOrderStrategy} if set in {@link JsonbConfig}.
     *
     * @param propertyList Properties to sort.
     * @param classDescriptor Class model.
     * @return Sorted list of properties.
     */
    public List<PropertyModel> sortPropertiesByOrder(List<PropertyModel> propertyList, ClassDescriptor classDescriptor) {
        Map<String, PropertyModel> readNameMap = new HashMap<>();
        propertyList.stream().forEach(propItem -> readNameMap.put(propItem.getReadName(), propItem));
        String[] sortSequence = classDescriptor.getClassCustomization().getPropertyOrder();
        List<PropertyModel> orderedList = new ArrayList<>();
        if (null != sortSequence) {
            //if @JsonbPropertyOrder annotation is defined on a class
            for (String propKey : sortSequence) {
                final PropertyModel removedItem = readNameMap.remove(propKey);
                if (null != removedItem) {
                    orderedList.add(removedItem);
                }
            }
        }
        orderedList.addAll(orderStrategy.orderProperties(readNameMap.values()));
        return orderedList;
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

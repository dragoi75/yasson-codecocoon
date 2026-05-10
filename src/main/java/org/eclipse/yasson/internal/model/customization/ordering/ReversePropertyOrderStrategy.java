/*******************************************************************************
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *     David Kral - initial implementation
 ******************************************************************************/
package org.eclipse.yasson.internal.model.customization.ordering;

import org.eclipse.yasson.internal.model.PropertyModel;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Revers ordering strategy
 *
 * @author David Kral
 */
public class ReversePropertyOrderStrategy extends PropertyOrderStrategy implements Comparator<PropertyModel> {

    @Override
    public List<PropertyModel> orderProperties(Collection<PropertyModel> propertyModels) {
        return propertyModels.stream().sorted(this).collect(toList());
    }

    @Override
    public int compare(PropertyModel firstProperty, PropertyModel secondProperty) {
        return secondProperty.getWriteName().compareTo(firstProperty.getWriteName());
    }
}

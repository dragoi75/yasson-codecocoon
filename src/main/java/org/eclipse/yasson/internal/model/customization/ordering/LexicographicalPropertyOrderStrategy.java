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
 * Lexicographical ordering strategy
 *
 * @author David Kral
 */
public class LexicographicalPropertyOrderStrategy extends PropertyOrderStrategy implements Comparator<PropertyModel> {

    @Override
    public int compare(PropertyModel firstModel, PropertyModel secondModel) {
        return firstModel.getWriteName().compareTo(secondModel.getWriteName());
    }

    @Override
    public List<PropertyModel> orderProperties(Collection<PropertyModel> attributeModels) {
        return attributeModels.stream().sorted(this).collect(toList());
    }

}

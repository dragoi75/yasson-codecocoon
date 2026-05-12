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

import org.eclipse.yasson.internal.model.BeanPropertyDescriptor;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Lexicographical ordering strategy
 *
 * @author David Kral
 */
public class LexicographicalOrderStrategy extends PropertyOrderStrategy implements Comparator<BeanPropertyDescriptor> {

    @Override
    public List<BeanPropertyDescriptor> orderProperties(Collection<BeanPropertyDescriptor> properties) {
        return properties.stream().sorted(this).collect(toList());
    }

    @Override
    public int compare(BeanPropertyDescriptor object1, BeanPropertyDescriptor object2) {
        return object1.getWriteName().compareTo(object2.getWriteName());
    }
}

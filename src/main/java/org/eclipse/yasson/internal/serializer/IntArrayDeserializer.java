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
package org.eclipse.yasson.internal.serializer;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.yasson.internal.JsonbDeserializer;

/**
 * Array unmarshaller item implementation for small int.
 */
public class IntArrayDeserializer extends AbstractArrayDeserializer<int[]> {

    private final List<Integer> items = new ArrayList<>();

    @Override
    public int[] getInstance(JsonbDeserializer unmarshaller) {
        final int size = items.size();
        final int[] intArray = new int[size];
        int i = 0;
        while (size > i) {
            intArray[i] = items.get(i);
            i += 1;
        }
        return intArray;
    }

    /**
     * Creates new instance of int array deserializer.
     *
     * @param builder deserializer builder
     */
    protected IntArrayDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
    }

    @Override
    protected List<?> getItems() {
        return items;
    }

}

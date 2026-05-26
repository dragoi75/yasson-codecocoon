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
package org.eclipse.yasson.internal.serializer;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.yasson.internal.JsonbUnmarshaller;

/**
 * Item for handling arrays of objects.
 *
 * @param <T> object type
 */
public class ObjectArrayDeserializer<T> extends AbstractArrayDeserializer<T[]> {

    private final List<T> items = new ArrayList<>();

    private T[] arrayInstance;

    @SuppressWarnings("unchecked")
    @Override
    public T[] getInstance(JsonbUnmarshaller unmarshaller) {
        if (null == arrayInstance || items.size() != arrayInstance.length) {
            arrayInstance = (T[]) Array.newInstance(getComponentClass(), items.size());
        }
        return items.toArray(arrayInstance);
    }

    @Override
    protected List<?> getItems() {
        return items;
    }

    /**
     * Creates new instance of object array deserializer.
     *
     * @param builder deserializer builder
     */
    protected ObjectArrayDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
    }

}

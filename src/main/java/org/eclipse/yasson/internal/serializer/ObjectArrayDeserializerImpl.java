/**
 * ****************************************************************************
 *  Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonbUnmarshaller;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Item for handling arrays of objects.
 *
 * @author Roman Grigoriadi
 */
public class ObjectArrayDeserializerImpl<T> extends AbstractArrayDeserializer<T[]> {

    private final List<T> elements = new ArrayList<>();

    private T[] elementsArray;

    @SuppressWarnings("unchecked")
    @Override
    public T[] getInstance(JsonbUnmarshaller unmarshaller) {
        if (null == elementsArray || elements.size() != elementsArray.length) {
            elementsArray = (T[]) Array.newInstance(componentClass, elements.size());
        }
        return elements.toArray(elementsArray);
    }

    @Override
    protected List<?> getItems() {
        return elements;
    }

    protected ObjectArrayDeserializerImpl(JsonValueDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }

}

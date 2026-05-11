/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;

/**
 * Array unmarshaller item implementation for small float.
 *
 * @author Roman Grigoriadi
 */
public class PrimitiveFloatArrayDeserializer extends AbstractArrayDeserializer<float[]> {

    private final List<Float> floatValues = new ArrayList<>();

    protected PrimitiveFloatArrayDeserializer(JsonValueDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }

    @Override
    protected List<?> getItems() {
        return floatValues;
    }

    @Override
    public float[] getInstance(JsonbUnmarshaller unmarshaller) {
        final int length = floatValues.size();
        final float[] primitiveFloats = new float[length];
        int index = 0;
        while (length > index) {
            primitiveFloats[index] = floatValues.get(index);
            index += 1;
        }
        return primitiveFloats;
    }
}

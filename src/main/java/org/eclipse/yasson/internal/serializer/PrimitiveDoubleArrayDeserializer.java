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
 * Array unmarshaller item implementation for small double.
 *
 * @author Roman Grigoriadi
 */
public class PrimitiveDoubleArrayDeserializer extends AbstractArrayDeserializer<double[]> {

    private final List<Double> doubleValues = new ArrayList<>();

    protected PrimitiveDoubleArrayDeserializer(JsonValueDeserializerBuilder deserializerCreator) {
        super(deserializerCreator);
    }

    @Override
    protected List<?> getItems() {
        return doubleValues;
    }

    @Override
    public double[] getInstance(JsonbUnmarshaller unmarshaller) {
        final int length = doubleValues.size();
        final double[] doubles = new double[length];
        int idx = 0;
        while (length > idx) {
            doubles[idx] = doubleValues.get(idx);
            idx += 1;
        }
        return doubles;
    }
}

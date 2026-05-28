/**
 * ****************************************************************************
 *  Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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
 * Array unmarshaller item implementation for small short.
 *
 * @author Roman Grigoriadi
 */
public class ShortArrayDeserializerImpl extends AbstractArrayDeserializer<short[]> {

    private final List<Short> shortList = new ArrayList<>();

    @Override
    protected List<?> getItems() {
        return shortList;
    }

    @Override
    public short[] getInstance(JsonbUnmarshaller unmarshaller) {
        final int length = shortList.size();
        final short[] shorts = new short[length];
        int index = 0;
        while (length > index) {
            shorts[index] = shortList.get(index);
            index += 1;
        }
        return shorts;
    }

    protected ShortArrayDeserializerImpl(JsonValueDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }

}

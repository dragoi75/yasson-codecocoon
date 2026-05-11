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

import org.eclipse.yasson.internal.Unmarshaller;
import java.util.ArrayList;
import java.util.List;

/**
 * Array unmarshaller item implementation for small int.
 *
 * @author Roman Grigoriadi
 */
public class ByteArrayToObjectDeserializer extends AbstractArrayDeserializer<byte[]> {

    private final List<Byte> byteList = new ArrayList<>();

    protected ByteArrayToObjectDeserializer(JsonbDeserializerBuilder jsonbDeserializerFactory) {
        super(jsonbDeserializerFactory);
    }

    @Override
    protected List<?> getItems() {
        return byteList;
    }

    @Override
    public byte[] getInstance(Unmarshaller unmarshaller) {
        final int length = byteList.size();
        final byte[] bytes = new byte[length];
        int index = 0;
        while (length > index) {
            bytes[index] = byteList.get(index);
            index += 1;
        }
        return bytes;
    }
}

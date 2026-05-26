/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
import org.eclipse.yasson.internal.Unmarshaller;

/**
 * Array unmarshaller item implementation for char.
 */
public class CharArrayDeserializer extends AbstractArrayDeserializer<char[]> {

    private final List<Character> items = new ArrayList<>();

    @Override
    public char[] getInstance(Unmarshaller unmarshaller) {
        final int size = items.size();
        final char[] charArray = new char[size];
        int i = 0;
        while (size > i) {
            charArray[i] = items.get(i);
            i += 1;
        }
        return charArray;
    }

    @Override
    protected List<?> getItems() {
        return items;
    }

    /**
     * Creates new instance of char array deserializer.
     *
     * @param builder deserializer builder
     */
    protected CharArrayDeserializer(DeserializerBuilder builder) {
        super(builder);
    }

}

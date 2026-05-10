/*******************************************************************************
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonbUnmarshaller;

import java.util.ArrayList;
import java.util.List;

/**
 * Array unmarshaller item implementation for char.
 *
 * @author Bernd Zeitler
 */
public class CharacterArrayDeserializer extends AbstractArrayDeserializer<char[]> {

    private final List<Character> characters = new ArrayList<>();

    protected CharacterArrayDeserializer(JsonValueDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }

    @Override
    protected List<?> getItems() {
        return characters;
    }

    @Override
    public char[] getInstance(JsonbUnmarshaller unmarshaller) {
        final int length = characters.size();
        final char[] chars = new char[length];
        for(int index = 0; index < length; index++) {
            chars[index] = characters.get(index);
        }
        return chars;
    }
}

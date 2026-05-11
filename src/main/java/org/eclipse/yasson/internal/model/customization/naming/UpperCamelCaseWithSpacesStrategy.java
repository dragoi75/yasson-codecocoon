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
package org.eclipse.yasson.internal.model.customization.naming;

import java.nio.CharBuffer;

/**
 * Upper case first character separate words by spaces.
 *
 * @author Roman Grigoriadi
 */
public class UpperCamelCaseWithSpacesStrategy extends UpperCamelCaseStrategy {

    @Override
    public String translateName(String propertyName) {
        String upperCased = super.translateName(propertyName);
        CharBuffer buffer = CharBuffer.allocate(upperCased.length() * 2);
        char last = Character.MIN_VALUE;
        int i = 0;
        while (upperCased.length() > i) {
            char current = upperCased.charAt(i);
            if (0 < i && Character.isUpperCase(current) && isLowerCaseCharacter(last)) {
                buffer.append(' ');
            }
            last = current;
            buffer.append(current);
            i += 1;
        }
        return new String(buffer.array(), 0, buffer.position());
    }

    private boolean isLowerCaseCharacter(char character) {
        return Character.isAlphabetic(character) && Character.isLowerCase(character);
    }
}

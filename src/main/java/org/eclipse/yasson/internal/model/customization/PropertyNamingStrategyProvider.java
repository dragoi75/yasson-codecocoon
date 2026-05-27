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
package org.eclipse.yasson.internal.model.customization;

import java.nio.CharBuffer;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.PropertyNamingStrategy;
import org.eclipse.yasson.internal.model.BeanPropertyDescriptor;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;
import static java.util.Comparator.comparing;
import static jakarta.json.bind.config.PropertyNamingStrategy.CASE_INSENSITIVE;
import static jakarta.json.bind.config.PropertyNamingStrategy.IDENTITY;
import static jakarta.json.bind.config.PropertyNamingStrategy.LOWER_CASE_WITH_DASHES;
import static jakarta.json.bind.config.PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES;
import static jakarta.json.bind.config.PropertyNamingStrategy.UPPER_CAMEL_CASE;
import static jakarta.json.bind.config.PropertyNamingStrategy.UPPER_CAMEL_CASE_WITH_SPACES;
import static jakarta.json.bind.config.PropertyOrderStrategy.ANY;
import static jakarta.json.bind.config.PropertyOrderStrategy.LEXICOGRAPHICAL;
import static jakarta.json.bind.config.PropertyOrderStrategy.REVERSE;

/**
 * Provides strategies for {@link jakarta.json.bind.config.PropertyNamingStrategy} and
 * {@link jakarta.json.bind.config.PropertyOrderStrategy}.
 */
public final class PropertyNamingStrategyProvider {

    /**
     * Case insensitive naming strategy.
     */
    public static final PropertyNamingStrategy CASE_INSENSITIVE_STRATEGY = Objects::requireNonNull;

    private static boolean isLowerCaseCharacter(char ch) {
        return Character.isAlphabetic(ch) && Character.isLowerCase(ch);
    }

    private static PropertyNamingStrategy createLowerCaseWithSeparatorStrategy(char delimiter) {
        return name -> {
            Objects.requireNonNull(name);
            CharBuffer charsSequence = CharBuffer.allocate(name.length() * 2);
            char prevChar = Character.MIN_VALUE;
            int index = 0;
            while (name.length() > index) {
                char activeChar = name.charAt(index);
                if (0 < index && Character.isUpperCase(activeChar) && isLowerCaseCharacter(prevChar)) {
                    charsSequence.append(delimiter);
                }
                prevChar = activeChar;
                charsSequence.append(Character.toLowerCase(activeChar));
                ++index;
            }
            return new String(charsSequence.array(), 0, charsSequence.position());
        };
    }

    /**
     * Returns an ordering strategy which corresponds to the ordering strategy name.
     *
     * @param orderingScheme ordering strategy name
     * @return ordering strategy
     */
    public static Consumer<List<BeanPropertyDescriptor>> getOrderingFunction(String orderingScheme) {
        switch(orderingScheme) {
            case LEXICOGRAPHICAL:
                return propertyMap -> propertyMap.sort(comparing(BeanPropertyDescriptor::getWriteName));
            case ANY:
                return props -> {
                };
            case REVERSE:
                return propertyMap -> propertyMap.sort(comparing(BeanPropertyDescriptor::getWriteName).reversed());
            default:
                throw new JsonbException(MessageProvider.getMessage(MessageConstants.PROPERTY_ORDER, orderingScheme));
        }
    }

    /**
     * Returns a naming strategy which corresponds to the naming strategy name.
     *
     * @param orderingScheme naming strategy name
     * @return naming strategy
     */
    public static PropertyNamingStrategy getPropertyNamingStrategy(String orderingScheme) {
        switch(orderingScheme) {
            case LOWER_CASE_WITH_UNDERSCORES:
                return createLowerCaseWithSeparatorStrategy('_');
            case LOWER_CASE_WITH_DASHES:
                return createLowerCaseWithSeparatorStrategy('-');
            case UPPER_CAMEL_CASE:
                return createPascalCaseStrategy();
            case UPPER_CAMEL_CASE_WITH_SPACES:
                return createUpperCamelCaseWithSpacesStrategy();
            case IDENTITY:
                return Objects::requireNonNull;
            case CASE_INSENSITIVE:
                return CASE_INSENSITIVE_STRATEGY;
            default:
                throw new JsonbException("No property naming strategy was found for: " + orderingScheme);
        }
    }

    private static PropertyNamingStrategy createUpperCamelCaseWithSpacesStrategy() {
        return name -> {
            String upperCaseVersion = createPascalCaseStrategy().translateName(name);
            CharBuffer charSeq = CharBuffer.allocate(upperCaseVersion.length() * 2);
            char prevChar = Character.MIN_VALUE;
            int index = 0;
            while (upperCaseVersion.length() > index) {
                char activeChar = upperCaseVersion.charAt(index);
                if (0 < index && Character.isUpperCase(activeChar) && isLowerCaseCharacter(prevChar)) {
                    charSeq.append(' ');
                }
                prevChar = activeChar;
                charSeq.append(activeChar);
                ++index;
            }
            return new String(charSeq.array(), 0, charSeq.position());
        };
    }

    private static PropertyNamingStrategy createPascalCaseStrategy() {
        return name -> {
            Objects.requireNonNull(name);
            char initialChar = Character.toUpperCase(name.charAt(0));
            return initialChar + name.substring(1);
        };
    }

    private PropertyNamingStrategyProvider() {
    }

}

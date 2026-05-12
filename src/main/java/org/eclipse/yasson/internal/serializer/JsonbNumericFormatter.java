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

package org.eclipse.yasson.internal.serializer;

/**
 * Formatter for numbers.
 */
public class JsonbNumericFormatter {

    private final String pattern;

    private final String regionCode;

    /**
     * Construct with format string and locale.
     *
     * @param pattern formatter format
     * @param regionCode locale
     */
    public JsonbNumericFormatter(String pattern, String regionCode) {
        this.pattern = pattern;
        this.regionCode = regionCode;
    }

    /**
     * Format string to be used either by formatter.
     *
     * @return format
     */
    public String getFormat() {
        return pattern;
    }

    /**
     * Locale to use with formatter.
     *
     * @return locale
     */
    public String getLocale() {
        return regionCode;
    }

}

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

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

import jakarta.json.bind.annotation.JsonbDateFormat;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

/**
 * Formatter wrapper for different types of dates.
 */
public class JsonbDateTimeFormatter {

    private static final JsonbDateTimeFormatter STANDARD_FORMATTER = new JsonbDateTimeFormatter(JsonbDateFormat.DEFAULT_FORMAT,
                                                                             Locale.getDefault().toLanguageTag());

    /**
     * Default I-JSON date time formatter.
     */
    public static final DateTimeFormatter IJSON_DATE_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .appendLiteral('Z')
            .appendOffset("+HH:MM", "+00:00")
            .toFormatter();

    private final DateTimeFormatter timestampFormatter;

    private final String pattern;

    private final String languageTag;

    /**
     * Locale to use with formatter.
     *
     * @return Locale.
     */
    public String getLocale() {
        return languageTag;
    }

    public boolean isDefault() {
        return JsonbDateFormat.DEFAULT_FORMAT.equals(pattern);
    }

    public static JsonbDateTimeFormatter getDefault() {
        return STANDARD_FORMATTER;
    }

    /**
     * Creates an instance with format string and locale.
     * Formatter will be created on every formatting / parsing operation.
     *
     * @param pattern Formatter format.
     * @param languageTag Locale in string.
     */
    public JsonbDateTimeFormatter(String pattern, String languageTag) {
        this.pattern = pattern;
        this.languageTag = languageTag;
        this.timestampFormatter = null;
    }

    /**
     * Format string to be used either by formatter.
     * Needed for formatting {@link java.util.Date} with {@link java.text.SimpleDateFormat},
     * which is not threadsafe.
     *
     * @return Format.
     */
    public String getFormat() {
        return pattern;
    }

    /**
     * Creates an instance with cached {@link DateTimeFormatter}, format and locale.
     *
     * @param timestampFormatter Reused time formatter.
     * @param pattern            Format in string.
     * @param languageTag            Locale in string.
     */
    public JsonbDateTimeFormatter(DateTimeFormatter timestampFormatter, String pattern, String languageTag) {
        this.timestampFormatter = timestampFormatter;
        this.pattern = pattern;
        this.languageTag = languageTag;
    }

    /**
     * Creates an instance with cached instance of {@link DateTimeFormatter}.
     *
     * @return Formatter instance.
     */
    public DateTimeFormatter getDateTimeFormatter() {
        return timestampFormatter;
    }

}

/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Common serializer for {@link Date} and {@link java.sql.Date} types.
 * @param <T> date type
 */
public class SqlDateSerializer<T extends Date> extends DateSerializer<T> {

    @Override
    protected String formatUsingFormatter(Date date, DateTimeFormatter dateTimeFormat) {
        if (!(date instanceof java.sql.Date)) {
            return super.formatUsingFormatter(date, dateTimeFormat);
        } else {
            return ((java.sql.Date) date).toLocalDate().format(dateTimeFormat);
        }
    }

    @Override
    protected String formatWithDefault(Date date, Locale languageTag) {
        if (!(date instanceof java.sql.Date)) {
            return super.formatWithDefault(date, languageTag);
        } else {
            // Z is the UTC timezone indicator
            return date.toString() + 'Z';
        }
    }

    @Override
    protected Instant asInstant(Date date) {
        if (!(date instanceof java.sql.Date)) {
            return super.asInstant(date);
        } else {
            // java.sql.Date doesn't have a time component, so do our best if TIME_IN_MILLIS is requested
            // In the future (at a breaking change boundary) we should probably reject this code path
            return Instant.ofEpochMilli(date.getTime());
        }
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public SqlDateSerializer(Customization customConfig) {
        super(customConfig);
    }

}

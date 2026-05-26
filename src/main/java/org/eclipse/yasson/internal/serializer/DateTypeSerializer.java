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
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Serializer for {@link Date} type.
 * @param <T> date type
 */
public class DateTypeSerializer<T extends Date> extends AbstractDateTimeSerializer<T> {
    
    private static final DateTimeFormatter DEFAULT_DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME.withZone(UTC);

    @Override
    protected String formatStrictIJson(Date value) {
        return JsonbDateFormatter.IJSON_DATE_FORMATTER.withZone(UTC).format(toTemporalAccessor(value));
    }

    @Override
    protected String formatWithFormatter(Date value, DateTimeFormatter formatter) {
        return getZonedFormatter(formatter).format(toTemporalAccessor(value));
    }

    @Override
    protected Instant toInstant(Date value) {
        return value.toInstant();
    }

    @Override
    protected TemporalAccessor toTemporalAccessor(Date object) {
        return toInstant(object);
    }

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    public DateTypeSerializer(Customization customization) {
        super(customization);
    }

    @Override
    protected String formatDefault(Date value, Locale locale) {
        return DEFAULT_DATE_FORMATTER.withLocale(locale).format(toInstant(value));
    }

}

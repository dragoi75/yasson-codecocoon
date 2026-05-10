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

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Locale;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Serializer for {@link Calendar} type.
 */
public class CalendarTypeSerializer extends AbstractDateTimeSerializer<Calendar> {

    /**
     * Creates a new instance.
     *
     * @param config Model customization.
     */
    public CalendarTypeSerializer(Customization config) {
        super(config);
    }

    @Override
    protected Instant toInstant(Calendar calendar) {
        return calendar.toInstant();
    }

    @Override
    protected String formatDefault(Calendar calendar, Locale region) {
        DateTimeFormatter dateTimeFmt = calendar.isSet(Calendar.HOUR) || calendar.isSet(Calendar.HOUR_OF_DAY)
                ? DateTimeFormatter.ISO_DATE_TIME
                : DateTimeFormatter.ISO_DATE;
        return dateTimeFmt.withZone(calendar.getTimeZone().toZoneId())
                .withLocale(region).format(toTemporalAccessor(calendar));
    }

    @Override
    protected TemporalAccessor toTemporalAccessor(Calendar calendar) {
        return asZonedDateTime(calendar);
    }

    private ZonedDateTime asZonedDateTime(Calendar calendar) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(calendar.getTimeInMillis()),
                                       calendar.getTimeZone().toZoneId());
    }
}

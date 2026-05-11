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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Deserializer for {@link Calendar} type.
 */
public class CalendarTypeParser extends AbstractDateTimeDeserializer<Calendar> {

    private static final LocalTime MIDNIGHT = LocalTime.parse("00:00:00");

    private final Calendar calendarPrototype;

    /**
     * Creates an instance.
     *
     * @param customOptions Model customization.
     */
    public CalendarTypeParser(Customization customOptions) {
        super(Calendar.class, customOptions);
        this.calendarPrototype = new GregorianCalendar();
        this.calendarPrototype.clear();
        this.calendarPrototype.setTimeZone(TimeZone.getTimeZone(UTC));
    }

    @Override
    protected Calendar fromInstant(Instant timestamp) {
        final Calendar cal = (Calendar) calendarPrototype.clone();
        cal.setTimeInMillis(timestamp.toEpochMilli());
        return cal;
    }

    @Override
    protected Calendar parseDefault(String jsonText, Locale region) {
        DateTimeFormatter dateTimeFmt = jsonText.contains("T") ? DateTimeFormatter.ISO_DATE_TIME : DateTimeFormatter.ISO_DATE;
        return parseWithFormatter(jsonText, dateTimeFmt.withLocale(region));
    }

    @Override
    protected Calendar parseWithFormatter(String jsonText, DateTimeFormatter dateTimeFmt) {
        final TemporalAccessor temporalAccessor = dateTimeFmt.parse(jsonText);
        LocalTime moment = temporalAccessor.query(TemporalQueries.localTime());
        ZoneId tz = temporalAccessor.query(TemporalQueries.zone());
        if (null == tz) {
            tz = UTC;
        }
        if (null == moment) {
            moment = MIDNIGHT;
        }
        ZonedDateTime zonedDateTime = LocalDate.from(temporalAccessor).atTime(moment).atZone(tz);
        return GregorianCalendar.from(zonedDateTime);
    }
}

/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
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
package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.model.customization.Customization;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Deserializer for {@link Calendar} type.
 *
 * @author David Kral
 */
public class CalendarTypeParser extends AbstractDateTimeDeserializer<Calendar> {

    private final Calendar templateReference;

    private final LocalTime midnightTime = LocalTime.parse("00:00:00");

    /**
     * Creates an instance.
     *
     * @param config Model customization.
     */
    public CalendarTypeParser(Customization config) {
        super(Calendar.class, config);
        this.templateReference = new GregorianCalendar();
        this.templateReference.clear();
        this.templateReference.setTimeZone(TimeZone.getTimeZone(UTC));
    }

    @Override
    protected Calendar fromInstant(Instant timestamp) {
        final Calendar calInstance = (Calendar) templateReference.clone();
        calInstance.setTimeInMillis(timestamp.toEpochMilli());
        return calInstance;
    }

    @Override
    protected Calendar parseDefault(String jsonText, Locale region) {
        DateTimeFormatter dateTimeFormat = jsonText.contains("T") ? DateTimeFormatter.ISO_DATE_TIME : DateTimeFormatter.ISO_DATE;
        return parseWithFormatter(jsonText, dateTimeFormat.withLocale(region));
    }

    @Override
    protected Calendar parseWithFormatter(String jsonText, DateTimeFormatter dateTimeFormat) {
        final TemporalAccessor temporalAccessor = dateTimeFormat.parse(jsonText);
        LocalTime localMoment = temporalAccessor.query(TemporalQueries.localTime());
        ZoneId tzId = temporalAccessor.query(TemporalQueries.zone());
        if (null == tzId) {
            tzId = UTC;
        }
        if (null == localMoment) {
            localMoment = midnightTime;
        }
        ZonedDateTime zonedDateTime = LocalDate.from(temporalAccessor).atTime(localMoment).atZone(tzId);
        return GregorianCalendar.from(zonedDateTime);
    }
}

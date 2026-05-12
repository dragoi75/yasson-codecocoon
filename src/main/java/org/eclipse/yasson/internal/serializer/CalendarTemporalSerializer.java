/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.model.customization.Customization;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Locale;

/**
 * Serializer for {@link Calendar} type.
 *
 * @author David Kral
 */
public class CalendarTemporalSerializer extends AbstractDateTimeSerializer<Calendar> {


    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public CalendarTemporalSerializer(Customization customConfig) {
        super(customConfig);
    }

    @Override
    protected Instant toInstant(Calendar calendar) {
        return calendar.toInstant();
    }

    @Override
    protected String formatDefault(Calendar calendar, Locale region) {
        DateTimeFormatter dateTimeFormat = calendar.isSet(Calendar.HOUR) || calendar.isSet(Calendar.HOUR_OF_DAY) ?
                DateTimeFormatter.ISO_DATE_TIME : DateTimeFormatter.ISO_DATE;
        return dateTimeFormat.withZone(calendar.getTimeZone().toZoneId())
                .withLocale(region).format(toTemporalAccessor(calendar));
    }

    @Override
    protected TemporalAccessor toTemporalAccessor(Calendar calendar) {
        return convertToZonedDateTime(calendar);
    }

    private ZonedDateTime convertToZonedDateTime(Calendar calendar) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(calendar.getTimeInMillis()),
                calendar.getTimeZone().toZoneId());
    }
}

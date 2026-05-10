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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

import javax.xml.datatype.XMLGregorianCalendar;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Serializer for {@link XMLGregorianCalendar} type.
 */
public class XMLGregorianCalendarSerializer extends AbstractDateTimeSerializer<XMLGregorianCalendar> {

    /**
     * Creates a new instance.
     *
     * @param customizer Model customization.
     */
    public XMLGregorianCalendarSerializer(Customization customizer) {
        super(customizer);
    }

    @Override
    protected Instant toInstant(XMLGregorianCalendar xmlCalendar) {
        return Instant.ofEpochMilli(xmlCalendar.toGregorianCalendar().getTimeInMillis());
    }

    @Override
    protected String formatDefault(XMLGregorianCalendar xmlCalendar, Locale region) {
        DateTimeFormatter formatPattern = DateTimeFormatter.ISO_DATE_TIME;
        return formatPattern
                .withLocale(region)
                .withZone(xmlCalendar.toGregorianCalendar().getTimeZone().toZoneId())
                .format(toTemporalAccessor(xmlCalendar));
    }

    @Override
    protected TemporalAccessor toTemporalAccessor(XMLGregorianCalendar calendar) {
        return toZonedDateTimeFromXmlGregorianCalendar(calendar);
    }

    private ZonedDateTime toZonedDateTimeFromXmlGregorianCalendar(XMLGregorianCalendar calendar) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(calendar.toGregorianCalendar().getTimeInMillis()),
                                       calendar.toGregorianCalendar().getTimeZone().toZoneId());
    }
}

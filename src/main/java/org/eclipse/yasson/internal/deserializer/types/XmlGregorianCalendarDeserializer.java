/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.yasson.internal.deserializer.types;

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
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import jakarta.json.bind.JsonbException;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer of the {@link XMLGregorianCalendar} type.
 */
class XmlGregorianCalendarDeserializer extends AbstractDateDeserializer<XMLGregorianCalendar> {

    private static final LocalTime ZERO_LOCAL_TIME = LocalTime.parse("00:00:00");

    private final Calendar calendarTemplate;

    private final DatatypeFactory datatypeFactory;

    @Override
    protected XMLGregorianCalendar parseDefault(String jsonValue, Locale locale) {
        DateTimeFormatter formatter = jsonValue.contains("T") ? DateTimeFormatter.ISO_DATE_TIME : DateTimeFormatter.ISO_DATE;
        return parseWithFormatter(jsonValue, formatter.withLocale(locale));
    }

    @Override
    protected XMLGregorianCalendar parseWithFormatter(String jsonValue, DateTimeFormatter formatter) {
        final TemporalAccessor parsed = formatter.parse(jsonValue);
        LocalTime time = parsed.query(TemporalQueries.localTime());
        ZoneId zone = parsed.query(TemporalQueries.zone());
        if (null == zone) {
            zone = UTC;
        }
        if (null == time) {
            time = ZERO_LOCAL_TIME;
        }
        ZonedDateTime result = LocalDate.from(parsed).atTime(time).atZone(zone);
        return datatypeFactory.newXMLGregorianCalendar(GregorianCalendar.from(result));
    }

    XmlGregorianCalendarDeserializer(TypeDeserializerBuilder builder) {
        super(builder);
        this.calendarTemplate = new GregorianCalendar();
        this.calendarTemplate.clear();
        this.calendarTemplate.setTimeZone(TimeZone.getTimeZone(UTC));
        try {
            this.datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DATATYPE_FACTORY_CREATION_FAILED), e);
        }
    }

    @Override
    protected XMLGregorianCalendar fromInstant(Instant instant) {
        final GregorianCalendar calendar = (GregorianCalendar) calendarTemplate.clone();
        calendar.setTimeInMillis(instant.toEpochMilli());
        return datatypeFactory.newXMLGregorianCalendar(calendar);
    }

}

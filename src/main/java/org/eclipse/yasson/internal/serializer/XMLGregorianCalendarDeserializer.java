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
import jakarta.json.bind.JsonbException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Deserializer for {@link XMLGregorianCalendar} type.
 */
public class XMLGregorianCalendarDeserializer extends AbstractDateTimeDeserializer<XMLGregorianCalendar> {

    private static final LocalTime MIDNIGHT_LOCAL_TIME = LocalTime.parse("00:00:00");

    private final Calendar calendarPrototype;

    private final DatatypeFactory typeFactory;

    /**
     * Creates an instance.
     *
     * @param customSettings Model customization.
     */
    public XMLGregorianCalendarDeserializer(Customization customSettings) {
        super(XMLGregorianCalendar.class, customSettings);
        this.calendarPrototype = new GregorianCalendar();
        this.calendarPrototype.clear();
        this.calendarPrototype.setTimeZone(TimeZone.getTimeZone(UTC));
        try {
            this.typeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException ex) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.DATATYPE_FACTORY_CREATION_FAILED), ex);
        }
    }

    @Override
    protected XMLGregorianCalendar fromInstant(Instant timestamp) {
        final GregorianCalendar gregorianInstance = (GregorianCalendar) calendarPrototype.clone();
        gregorianInstance.setTimeInMillis(timestamp.toEpochMilli());
        return typeFactory.newXMLGregorianCalendar(gregorianInstance);
    }

    @Override
    protected XMLGregorianCalendar parseDefault(String jsonStr, Locale region) {
        DateTimeFormatter dateFormat = jsonStr.contains("T") ? DateTimeFormatter.ISO_DATE_TIME : DateTimeFormatter.ISO_DATE;
        return parseWithFormatter(jsonStr, dateFormat.withLocale(region));
    }

    @Override
    protected XMLGregorianCalendar parseWithFormatter(String jsonStr, DateTimeFormatter dateFormat) {
        final TemporalAccessor temporalResult = dateFormat.parse(jsonStr);
        LocalTime timeOfDay = temporalResult.query(TemporalQueries.localTime());
        ZoneId zoneId = temporalResult.query(TemporalQueries.zone());
        if (null == zoneId) {
            zoneId = UTC;
        }
        if (null == timeOfDay) {
            timeOfDay = MIDNIGHT_LOCAL_TIME;
        }
        ZonedDateTime zonedDateTime = LocalDate.from(temporalResult).atTime(timeOfDay).atZone(zoneId);
        return typeFactory.newXMLGregorianCalendar(GregorianCalendar.from(zonedDateTime));
    }
}

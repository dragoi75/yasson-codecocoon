/*******************************************************************************
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

import javax.json.bind.JsonbException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Deserializer for {@link XMLGregorianCalendar} type.
 *
 * @author David Kral
 */
public class XMLGregorianCalendarDeserializer extends AbstractDateTimeDeserializer<XMLGregorianCalendar> {

    private final Calendar dateTemplate;
    private final DatatypeFactory typeFactory;
    private final LocalTime midnightTime = LocalTime.parse("00:00:00");

    /**
     * Creates an instance.
     *
     * @param configOptions Model customization.
     */
    public XMLGregorianCalendarDeserializer(Customization configOptions) {
        super(XMLGregorianCalendar.class, configOptions);
        this.dateTemplate = new GregorianCalendar();
        this.dateTemplate.clear();
        this.dateTemplate.setTimeZone(TimeZone.getTimeZone(UTC));
        try {
            this.typeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException configException) {
            throw new JsonbException(Messages.getMessage(MessageKeys.DATATYPE_FACTORY_CREATION_FAILED), configException);
        }
    }


    @Override
    protected XMLGregorianCalendar fromInstant(Instant timePoint) {
        final GregorianCalendar dateHolder = (GregorianCalendar) dateTemplate.clone();
        dateHolder.setTimeInMillis(timePoint.toEpochMilli());
        return typeFactory.newXMLGregorianCalendar(dateHolder);
    }

    @Override
    protected XMLGregorianCalendar parseDefault(String jsonText, Locale region) {
        DateTimeFormatter dateFormat = jsonText.contains("T") ?
                DateTimeFormatter.ISO_DATE_TIME : DateTimeFormatter.ISO_DATE;
        return parseWithFormatter(jsonText, dateFormat.withLocale(region));
    }

    @Override
    protected XMLGregorianCalendar parseWithFormatter(String jsonText, DateTimeFormatter dateFormat) {
        final TemporalAccessor temporalAccessor = dateFormat.parse(jsonText);
        LocalTime timeOfDay = temporalAccessor.query(TemporalQueries.localTime());
        ZoneId timeRegion = temporalAccessor.query(TemporalQueries.zone());
        if (timeRegion == null) {
            timeRegion = UTC;
        }
        if (timeOfDay == null) {
            timeOfDay = midnightTime;
        }
        ZonedDateTime zonedDateTime = LocalDate.from(temporalAccessor).atTime(timeOfDay).atZone(timeRegion);
        return typeFactory.newXMLGregorianCalendar(GregorianCalendar.from(zonedDateTime));
    }
}

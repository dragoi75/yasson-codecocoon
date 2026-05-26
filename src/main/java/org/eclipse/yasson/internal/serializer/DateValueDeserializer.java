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
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;

/**
 * Deserializer for {@link Date} type.
 *
 * @author David Kral
 */
public class DateValueDeserializer extends AbstractDateTimeDeserializer<Date> {

    private static final DateTimeFormatter DEFAULT_ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME.withZone(UTC);


    @Override
    protected Date parseWithFormatter(String jsonString, DateTimeFormatter dateTimeFormat) {
        final TemporalAccessor temporalResult = getZonedFormatter(dateTimeFormat).parse(jsonString);
        return new Date(Instant.from(temporalResult).toEpochMilli());
    }

    @Override
    protected Date parseDefault(String jsonString, Locale regionalSetting) {
        final TemporalAccessor temporalResult = DEFAULT_ISO_DATE_TIME.withLocale(regionalSetting).parse(jsonString);
        return new Date(Instant.from(temporalResult).toEpochMilli());
    }

    @Override
    protected Date fromInstant(Instant timePoint) {
        return new Date(timePoint.toEpochMilli());
    }

    /**
     * Creates an instance.
     *
     * @param customOptions Model customization.
     */
    public DateValueDeserializer(Customization customOptions) {
        super(Date.class, customOptions);
    }

}

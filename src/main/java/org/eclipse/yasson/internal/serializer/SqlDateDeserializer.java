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

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

/**
 * Deserializer for {@link Date} type.
 *
 */
public class SqlDateDeserializer extends AbstractDateTimeDeserializer<Date> {

    private static final DateTimeFormatter SQL_DATE_PATTERN = DateTimeFormatter.ISO_DATE.withZone(UTC);

    /**
     * Creates an instance.
     *
     * @param customConfig Model customization.
     */
    public SqlDateDeserializer(Customization customConfig) {
        super(Date.class, customConfig);
    }

    /**
     * No arg constructor in order ot make usable in {@link javax.json.bind.annotation.JsonbTypeDeserializer}.
     */
    public SqlDateDeserializer() {
        super(Date.class, null);
    }

    @Override
    protected Date fromInstant(Instant timePoint) {
        return new Date(timePoint.toEpochMilli());
    }

    @Override
    protected Date parseDefault(String jsonText, Locale region) {
        final TemporalAccessor temporalResult = SQL_DATE_PATTERN.withLocale(region).parse(jsonText);
        return new Date(getInstant(temporalResult).toEpochMilli());
    }

    @Override
    protected Date parseWithFormatter(String jsonText, DateTimeFormatter dateTimeFormat) {
        final TemporalAccessor temporalResult = getZonedFormatter(dateTimeFormat).parse(jsonText);
        return new Date(getInstant(temporalResult).toEpochMilli());
    }

    private Instant getInstant(TemporalAccessor temporalResult) {
        LocalDate datePart = LocalDate.from(temporalResult);
        return datePart.atStartOfDay().atZone(ZoneId.of("UTC")).toInstant();
    }
}

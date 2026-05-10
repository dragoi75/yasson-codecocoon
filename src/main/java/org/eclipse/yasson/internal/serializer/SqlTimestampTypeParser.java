/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Deserializer for {@link java.sql.Timestamp} type.
 */
public class SqlTimestampTypeParser extends AbstractDateTimeDeserializer<Timestamp> {

    private static final DateTimeFormatter TIMESTAMP_PATTERN = DateTimeFormatter.ISO_DATE_TIME.withZone(UTC);

    /**
     * Creates an instance.
     *
     * @param configOptions Model customization.
     */
    public SqlTimestampTypeParser(Customization configOptions) {
        super(Timestamp.class, configOptions);
    }

    /**
     * No arg constructor in order to make usable in {@link jakarta.json.bind.annotation.JsonbTypeDeserializer}.
     */
    public SqlTimestampTypeParser() {
        super(Timestamp.class, null);
    }

    @Override
    protected Timestamp fromInstant(Instant moment) {
        return Timestamp.from(moment);
    }

    @Override
    protected Timestamp parseDefault(String jsonText, Locale region) {
        final TemporalAccessor temporalAccessor = TIMESTAMP_PATTERN.withLocale(region).parse(jsonText);
        return Timestamp.from(getInstant(temporalAccessor));
    }

    @Override
    protected Timestamp parseWithFormatter(String jsonText, DateTimeFormatter dateTimeFmt) {
        final TemporalAccessor temporalAccessor = getZonedFormatter(dateTimeFmt).parse(jsonText);
        return Timestamp.from(getInstant(temporalAccessor));
    }

    private Instant getInstant(TemporalAccessor temporalAccessor) {
        LocalDateTime dateTime = LocalDateTime.from(temporalAccessor);
        return dateTime.atZone(ZoneId.of("UTC")).toInstant();
    }

}

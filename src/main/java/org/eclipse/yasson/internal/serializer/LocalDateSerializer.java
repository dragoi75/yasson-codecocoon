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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Serializer for {@link LocalDate} type.
 */
public class LocalDateSerializer extends AbstractDateTimeSerializer<LocalDate> {

    private static final DateTimeFormatter ISO_LOCAL_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE.withZone(UTC);

    @Override
    protected String formatDefault(LocalDate localDate, Locale userRegion) {
        return ISO_LOCAL_DATE_FORMATTER.withLocale(userRegion).format(localDate);
    }

    @Override
    protected String formatStrictIJson(LocalDate localDate) {
        final ZonedDateTime dateTimeInZone = localDate.atTime(0, 0, 0).atZone(UTC);
        return JsonbDateFormatter.IJSON_DATE_FORMATTER.withZone(UTC).format(dateTimeInZone);
    }

    @Override
    protected Instant toInstant(LocalDate localDate) {
        return Instant.from(localDate.atStartOfDay(UTC));
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public LocalDateSerializer(Customization customConfig) {
        super(customConfig);
    }

}

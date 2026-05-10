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
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Serializer for {@link LocalDateTime} type.
 */
public class LocalDateTimeJsonSerializer extends AbstractDateTimeSerializer<LocalDateTime> {

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public LocalDateTimeJsonSerializer(Customization customConfig) {
        super(customConfig);
    }

    @Override
    protected Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(UTC).toInstant();
    }

    @Override
    protected String formatDefault(LocalDateTime localDateTime, Locale languageTag) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(languageTag).format(localDateTime);
    }

    @Override
    protected String formatWithFormatter(LocalDateTime localDateTime, DateTimeFormatter formatPattern) {
        return getZonedFormatter(formatPattern).format(localDateTime);
    }

    @Override
    protected String formatStrictIJson(LocalDateTime localDateTime) {
        final ZonedDateTime zonedTime = localDateTime.atZone(UTC);
        return JsonbDateFormatter.IJSON_DATE_FORMATTER.format(zonedTime);
    }
}

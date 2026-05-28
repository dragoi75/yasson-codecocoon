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
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Serializer for {@link Date} type.
 * @param <T> date type
 */
public class DateSerializer<T extends Date> extends AbstractDateTimeSerializer<T> {
    
    private static final DateTimeFormatter FALLBACK_DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME.withZone(UTC);

    @Override
    protected String formatUsingFormatter(Date inputDate, DateTimeFormatter dateTimeFormatter) {
        return getZonedFormatter(dateTimeFormatter).format(asTemporalAccessor(inputDate));
    }

    @Override
    protected TemporalAccessor asTemporalAccessor(Date dateCandidate) {
        return asInstant(dateCandidate);
    }

    @Override
    protected String formatStrictIJson(Date inputDate) {
        return JsonbDateFormatter.IJSON_DATE_FORMATTER.withZone(UTC).format(asTemporalAccessor(inputDate));
    }

    /**
     * Creates a new instance.
     *
     * @param customizer Model customization.
     */
    public DateSerializer(Customization customizer) {
        super(customizer);
    }

    @Override
    protected String formatWithDefault(Date inputDate, Locale userRegion) {
        return FALLBACK_DATE_FORMATTER.withLocale(userRegion).format(asInstant(inputDate));
    }

    @Override
    protected Instant asInstant(Date inputDate) {
        return inputDate.toInstant();
    }

}

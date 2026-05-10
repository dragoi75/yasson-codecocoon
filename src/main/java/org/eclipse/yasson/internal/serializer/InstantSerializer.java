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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Serializer for {@link Instant} type.
 */
public class InstantSerializer extends AbstractDateTimeSerializer<Instant> {

    /**
     * Creates a new instance.
     *
     * @param settings Model customization.
     */
    public InstantSerializer(Customization settings) {
        super(settings);
    }

    @Override
    protected Instant toInstant(Instant instant) {
        return instant;
    }

    @Override
    protected String formatDefault(Instant instant, Locale region) {
        return DateTimeFormatter.ISO_INSTANT.withLocale(region).format(instant);
    }

    @Override
    protected String formatWithFormatter(Instant instant, DateTimeFormatter dateTimeFormat) {
        return dateTimeFormat.withZone(UTC).format(instant);
    }

    @Override
    protected String formatStrictIJson(Instant instant) {
        return JsonbDateFormatter.IJSON_DATE_FORMATTER.withZone(UTC).format(instant);
    }

}

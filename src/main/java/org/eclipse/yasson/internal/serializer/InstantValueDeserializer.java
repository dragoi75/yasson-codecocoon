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
 * Deserializer for {@link Instant} type.
 */
public class InstantValueDeserializer extends AbstractDateTimeDeserializer<Instant> {

    private static final DateTimeFormatter ISO_INSTANT_FORMAT = DateTimeFormatter.ISO_INSTANT.withZone(UTC);

    /**
     * Creates an instance.
     *
     * @param customConfig Model customization.
     */
    public InstantValueDeserializer(Customization customConfig) {
        super(Instant.class, customConfig);
    }

    @Override
    protected Instant fromInstant(Instant timePoint) {
        return timePoint;
    }

    @Override
    protected Instant parseDefault(String jsonText, Locale region) {
        return Instant.from(ISO_INSTANT_FORMAT.withLocale(region).parse(jsonText));
    }

    @Override
    protected Instant parseWithFormatter(String jsonText, DateTimeFormatter dateTimeFormat) {
        return Instant.from(getZonedFormatter(dateTimeFormat).parse(jsonText));
    }
}

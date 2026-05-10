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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Deserializer for {@link LocalDate} type.
 */
public class LocalDateDeserializer extends AbstractDateTimeDeserializer<LocalDate> {

    /**
     * Creates a new instance.
     *
     * @param config Customization model.
     */
    public LocalDateDeserializer(Customization config) {
        super(LocalDate.class, config);
    }

    @Override
    protected LocalDate fromInstant(Instant timestamp) {
        return timestamp.atZone(UTC).toLocalDate();
    }

    @Override
    protected LocalDate parseDefault(String rawJson, Locale userRegion) {
        return LocalDate.parse(rawJson, DateTimeFormatter.ISO_LOCAL_DATE.withLocale(userRegion));
    }

    @Override
    protected LocalDate parseWithFormatter(String rawJson, DateTimeFormatter datePattern) {
        return LocalDate.parse(rawJson, datePattern);
    }
}

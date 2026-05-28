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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Deserializer for {@link LocalDateTime} type.
 */
public class LocalDateTimeDeserializer extends AbstractDateTimeDeserializer<LocalDateTime> {

    @Override
    protected LocalDateTime parseDefault(String jsonText, Locale region) {
        return LocalDateTime.parse(jsonText, DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(region));
    }

    @Override
    protected LocalDateTime parseWithFormatter(String jsonText, DateTimeFormatter dateTimeFormat) {
        return LocalDateTime.parse(jsonText, dateTimeFormat);
    }

    /**
     * Creates an instance.
     *
     * @param customConfig Model customization.
     */
    public LocalDateTimeDeserializer(Customization customConfig) {
        super(LocalDateTime.class, customConfig);
    }

    @Override
    protected LocalDateTime fromInstant(Instant epochTime) {
        return LocalDateTime.ofInstant(epochTime, UTC);
    }

}

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
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Serializer for {@link LocalDateTime} type.
 * 
 * @author David Kral
 */
public class LocalDateTimeSerializer extends AbstractDateTimeSerializer<LocalDateTime> {

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public LocalDateTimeSerializer(Customization customConfig) {
        super(customConfig);
    }

    @Override
    protected Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(UTC).toInstant();
    }


    @Override
    protected String formatDefault(LocalDateTime dateTime, Locale userRegion) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(userRegion).format(dateTime);
    }

    @Override
    protected String formatWithFormatter(LocalDateTime dateTime, DateTimeFormatter dateFormat) {
        return getZonedFormatter(dateFormat).format(dateTime);
    }

    @Override
    protected String formatStrictIJson(LocalDateTime dateTime) {
        final ZonedDateTime offsetDateTime = dateTime.atZone(UTC);
        return JsonbDateFormatter.IJSON_DATE_FORMATTER.format(offsetDateTime);
    }
}
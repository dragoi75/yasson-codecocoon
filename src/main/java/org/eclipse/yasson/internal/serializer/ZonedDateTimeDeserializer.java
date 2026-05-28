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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer for {@link ZonedDateTime} type.
 */
public class ZonedDateTimeDeserializer extends AbstractDateTimeDeserializer<ZonedDateTime> {

    private static final Logger DESERIALIZER_LOG = Logger.getLogger(ZonedDateTimeDeserializer.class.getName());

    @Override
    protected ZonedDateTime parseWithFormatter(String value, DateTimeFormatter format) {
        return ZonedDateTime.parse(value, getZonedFormatter(format));
    }

    @Override
    protected ZonedDateTime parseDefault(String value, Locale languageTag) {
        return ZonedDateTime.parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME.withLocale(languageTag));
    }

    /**
     * fromInstant is called only in case {@link jakarta.json.bind.annotation.JsonbDateFormat} is TIME_IN_MILLIS,
     * which doesn't make much sense for usage with ZonedDateTime.
     */
    @Override
    protected ZonedDateTime fromInstant(Instant timestamp) {
        DESERIALIZER_LOG.warning(LocalizedMessages.getMessage(MessageKeyConstants.OFFSET_DATE_TIME_FROM_MILLIS, ZonedDateTime.class.getSimpleName(), UTC));
        return ZonedDateTime.ofInstant(timestamp, UTC);
    }

    /**
     * Creates an instance.
     *
     * @param config Model customization.
     */
    public ZonedDateTimeDeserializer(Customization config) {
        super(ZonedDateTime.class, config);
    }

}

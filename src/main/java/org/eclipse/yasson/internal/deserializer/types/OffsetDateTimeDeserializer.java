/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.deserializer.types;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * Deserializer of the {@link OffsetDateTime} type.
 */
class OffsetDateTimeDeserializer extends AbstractDateDeserializer<OffsetDateTime> {

    private static final Logger LOGGER = Logger.getLogger(OffsetDateTimeDeserializer.class.getName());

    @Override
    protected OffsetDateTime parseWithFormatter(String jsonValue, DateTimeFormatter formatter) {
        return OffsetDateTime.parse(jsonValue, formatter);
    }

    /**
     * fromInstant is called only in case {@link jakarta.json.bind.annotation.JsonbDateFormat} is TIME_IN_MILLIS,
     * which doesn't make much sense for usage with OffsetDateTime.
     */
    @Override
    protected OffsetDateTime fromInstant(Instant instant) {
        LOGGER.warning(MessageProvider.getMessage(MessageConstants.OFFSET_DATE_TIME_FROM_MILLIS, OffsetDateTime.class.getSimpleName(), UTC));
        return OffsetDateTime.ofInstant(instant, UTC);
    }

    OffsetDateTimeDeserializer(TypeDeserializerBuilder builder) {
        super(builder);
    }

    @Override
    protected OffsetDateTime parseDefault(String jsonValue, Locale locale) {
        return OffsetDateTime.parse(jsonValue, DateTimeFormatter.ISO_OFFSET_DATE_TIME.withLocale(locale));
    }

}

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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Deserializer for {@link OffsetDateTime} type.
 */
public class OffsetDateTimeDeserializer extends AbstractDateTimeDeserializer<OffsetDateTime> {

    private static final Logger LOG = Logger.getLogger(OffsetDateTimeDeserializer.class.getName());

    /**
     * Creates an instance.
     *
     * @param customizer Model customization.
     */
    public OffsetDateTimeDeserializer(Customization customizer) {
        super(OffsetDateTime.class, customizer);
    }

    /**
     * fromInstant is called only in case {@link jakarta.json.bind.annotation.JsonbDateFormat} is TIME_IN_MILLIS,
     * which doesn't make much sense for usage with OffsetDateTime.
     */
    @Override
    protected OffsetDateTime fromInstant(Instant timestamp) {
        LOG.warning(LocalizedMessages.getMessage(MessageKeyConstants.OFFSET_DATE_TIME_FROM_MILLIS, OffsetDateTime.class.getSimpleName(), UTC));
        return OffsetDateTime.ofInstant(timestamp, UTC);
    }

    @Override
    protected OffsetDateTime parseDefault(String jsonText, Locale region) {
        return OffsetDateTime.parse(jsonText, DateTimeFormatter.ISO_OFFSET_DATE_TIME.withLocale(region));
    }

    @Override
    protected OffsetDateTime parseWithFormatter(String jsonText, DateTimeFormatter dateTimeFormat) {
        return OffsetDateTime.parse(jsonText, dateTimeFormat);
    }
}

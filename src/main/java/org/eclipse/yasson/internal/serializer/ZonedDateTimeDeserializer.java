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
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Deserializer for {@link ZonedDateTime} type.
 *
 * @author David Kral
 */
public class ZonedDateTimeDeserializer extends AbstractDateTimeDeserializer<ZonedDateTime> {
    private static final Logger DESERIALIZER_AUDITOR = Logger.getLogger(ZonedDateTimeDeserializer.class.getName());

    /**
     * Creates an instance.
     *
     * @param customConfig Model customization.
     */
    public ZonedDateTimeDeserializer(Customization customConfig) {
        super(ZonedDateTime.class, customConfig);
    }

    /**
     * fromInstant is called only in case {@link javax.json.bind.annotation.JsonbDateFormat} is TIME_IN_MILLIS,
     * which doesn't make much sense for usage with ZonedDateTime.
     */
    @Override
    protected ZonedDateTime fromInstant(Instant timestamp) {
        DESERIALIZER_AUDITOR.warning(Messages.getMessage(MessageKeys.OFFSET_DATE_TIME_FROM_MILLIS, ZonedDateTime.class.getSimpleName(), UTC));
        return ZonedDateTime.ofInstant(timestamp, UTC);
    }

    @Override
    protected ZonedDateTime parseDefault(String valueString, Locale region) {
        return ZonedDateTime.parse(valueString, DateTimeFormatter.ISO_ZONED_DATE_TIME.withLocale(region));
    }

    @Override
    protected ZonedDateTime parseWithFormatter(String valueString, DateTimeFormatter dateTimeFormat) {
        return ZonedDateTime.parse(valueString, getZonedFormatter(dateTimeFormat));
    }
}

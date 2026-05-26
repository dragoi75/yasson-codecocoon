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
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import jakarta.json.bind.JsonbException;

import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer of the {@link OffsetTime} type.
 */
class OffsetTimeDeserializer extends AbstractDateDeserializer<OffsetTime> {

    @Override
    protected OffsetTime parseDefault(String jsonValue, Locale locale) {
        return OffsetTime.parse(jsonValue, DateTimeFormatter.ISO_OFFSET_TIME.withLocale(locale));
    }

    @Override
    protected OffsetTime parseWithFormatter(String jsonValue, DateTimeFormatter formatter) {
        return OffsetTime.parse(jsonValue, formatter);
    }

    @Override
    protected OffsetTime fromInstant(Instant instant) {
        throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.TIME_TO_EPOCH_MILLIS_ERROR, OffsetTime.class.getSimpleName()));
    }

    OffsetTimeDeserializer(TypeDeserializerBuilder builder) {
        super(builder);
    }

}

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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import jakarta.json.bind.JsonbException;

import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Serializer for {@link LocalTime} type.
 */
public class LocalTimeInstantSerializer extends AbstractDateTimeSerializer<LocalTime> {

    @Override
    protected String formatDefault(LocalTime localTime, Locale languageTag) {
        return DateTimeFormatter.ISO_LOCAL_TIME.withLocale(languageTag).format(localTime);
    }

    @Override
    protected Instant toInstant(LocalTime value) {
        throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.TIME_TO_EPOCH_MILLIS_ERROR, LocalTime.class.getSimpleName()));
    }

    /**
     * Creates a new instance.
     *
     * @param customizer Model customization.
     */
    public LocalTimeInstantSerializer(Customization customizer) {
        super(customizer);
    }

}

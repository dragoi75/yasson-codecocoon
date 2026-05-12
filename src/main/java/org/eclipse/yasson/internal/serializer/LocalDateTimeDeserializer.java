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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Deserializer for {@link LocalDateTime} type.
 * 
 * @author David Kral
 */
public class LocalDateTimeDeserializer extends AbstractDateTimeDeserializer<LocalDateTime> {

    /**
     * Creates an instance.
     *
     * @param customConfig Model customization.
     */
    public LocalDateTimeDeserializer(Customization customConfig) {
        super(LocalDateTime.class, customConfig);
    }

    @Override
    protected LocalDateTime fromInstant(Instant timestamp) {
        return LocalDateTime.ofInstant(timestamp, UTC);
    }

    @Override
    protected LocalDateTime parseDefault(String jsonText, Locale languageTag) {
        return LocalDateTime.parse(jsonText, DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(languageTag));
    }

    @Override
    protected LocalDateTime parseWithFormatter(String jsonText, DateTimeFormatter dateTimeFormat) {
        return LocalDateTime.parse(jsonText, dateTimeFormat);
    }
}

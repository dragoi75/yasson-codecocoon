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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Deserializer for {@link LocalDate} type.
 * 
 * @author David Kral
 */
public class LocalDateDeserializer extends AbstractDateTimeDeserializer<LocalDate> {

    /**
     * Creates a new instance.
     *
     * @param settings Customization model.
     */
    public LocalDateDeserializer(Customization settings) {
        super(LocalDate.class, settings);
    }

    @Override
    protected LocalDate fromInstant(Instant moment) {
        return moment.atZone(UTC).toLocalDate();
    }

    @Override
    protected LocalDate parseDefault(String jsonText, Locale region) {
        return LocalDate.parse(jsonText, DateTimeFormatter.ISO_LOCAL_DATE.withLocale(region));
    }

    @Override
    protected LocalDate parseWithFormatter(String jsonText, DateTimeFormatter dateFormat) {
        return LocalDate.parse(jsonText, dateFormat);
    }
}

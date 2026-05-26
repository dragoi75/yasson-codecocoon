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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Serializer for {@link Instant} type.
 *
 * @author David Kral
 */
public class InstantSerializer extends AbstractDateTimeSerializer<Instant> {

    @Override
    protected String formatStrictIJson(Instant instant) {
        return JsonbDateFormatter.IJSON_DATE_FORMATTER.withZone(UTC).format(instant);
    }

    @Override
    protected String formatWithFormatter(Instant instant, DateTimeFormatter dateFormat) {
        return dateFormat.withZone(UTC).format(instant);
    }

    @Override
    protected String formatDefault(Instant instant, Locale region) {
        return DateTimeFormatter.ISO_INSTANT.withLocale(region).format(instant);
    }

    /**
     * Creates a new instance.
     *
     * @param config Model customization.
     */
    public InstantSerializer(Customization config) {
        super(config);
    }

    @Override
    protected Instant toInstant(Instant instant) {
        return instant;
    }

}

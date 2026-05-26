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
 * Deserializer for {@link Instant} type.
 *
 * @author David Kral
 */
public class InstantDeserializer extends AbstractDateTimeDeserializer<Instant> {

    private static final DateTimeFormatter STANDARD_FORMATTER = DateTimeFormatter.ISO_INSTANT.withZone(UTC);

    @Override
    protected Instant parseDefault(String jsonText, Locale region) {
        return Instant.from(STANDARD_FORMATTER.withLocale(region).parse(jsonText));
    }

    @Override
    protected Instant parseWithFormatter(String jsonText, DateTimeFormatter dateTimeFormat) {
        return Instant.from(getZonedFormatter(dateTimeFormat).parse(jsonText));
    }

    @Override
    protected Instant fromInstant(Instant timestamp) {
        return timestamp;
    }

    /**
     * Creates an instance.
     *
     * @param customConfig Model customization.
     */
    public InstantDeserializer(Customization customConfig) {
        super(Instant.class, customConfig);
    }

}

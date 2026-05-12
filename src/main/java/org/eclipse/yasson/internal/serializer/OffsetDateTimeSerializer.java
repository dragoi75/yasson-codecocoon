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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Serializer for {@link OffsetDateTime} type.
 *
 * @author David Kral
 */
public class OffsetDateTimeSerializer extends AbstractDateTimeSerializer<OffsetDateTime> {

    /**
     * Creates a new instance.
     *
     * @param configuration Model customization.
     */
    public OffsetDateTimeSerializer(Customization configuration) {
        super(configuration);
    }

    @Override
    protected Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime.toInstant();
    }

    @Override
    protected String formatDefault(OffsetDateTime offsetDateTime, Locale region) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.withLocale(region).format(offsetDateTime);
    }
}
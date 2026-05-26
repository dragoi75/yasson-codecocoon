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

import javax.json.bind.JsonbException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Serializer for {@link LocalTime} type.
 *
 * @author David Kral
 */
public class LocalTimeSerializer extends AbstractDateTimeSerializer<LocalTime> {

    @Override
    protected String formatDefault(LocalTime localTime, Locale userRegion) {
        return DateTimeFormatter.ISO_LOCAL_TIME.withLocale(userRegion).format(localTime);
    }

    @Override
    protected Instant toInstant(LocalTime value) {
        throw new JsonbException(Messages.getMessage(MessageKeys.TIME_TO_EPOCH_MILLIS_ERROR, LocalTime.class.getSimpleName()));
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public LocalTimeSerializer(Customization customConfig) {
        super(customConfig);
    }

}

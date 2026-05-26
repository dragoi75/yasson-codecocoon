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
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Deserializer for {@link OffsetTime} type.
 * 
 * @author David Kral
 */
public class OffsetTimeDeserializer extends AbstractDateTimeDeserializer<OffsetTime> {

    @Override
    protected OffsetTime parseWithFormatter(String jsonText, DateTimeFormatter formatSpec) {
        return OffsetTime.parse(jsonText, formatSpec);
    }

    @Override
    protected OffsetTime fromInstant(Instant instant) {
        throw new JsonbException(Messages.getMessage(MessageKeys.TIME_TO_EPOCH_MILLIS_ERROR, OffsetTime.class.getSimpleName()));
    }

    /**
     * Creates an instance.
     *
     * @param customOptions Model customization.
     */
    public OffsetTimeDeserializer(Customization customOptions) {
        super(OffsetTime.class, customOptions);
    }

    @Override
    protected OffsetTime parseDefault(String jsonText, Locale region) {
        return OffsetTime.parse(jsonText, DateTimeFormatter.ISO_OFFSET_TIME.withLocale(region));
    }

}

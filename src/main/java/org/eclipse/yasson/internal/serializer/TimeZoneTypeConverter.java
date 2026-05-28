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

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

import jakarta.json.bind.JsonbException;

import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Deserializer for {@link TimeZone} type.
 */
public class TimeZoneTypeConverter extends AbstractValueTypeDeserializer<TimeZone> {

    @Override
    protected TimeZone deserialize(String jsonStr, Unmarshaller unmarshaller, Type rtType) {
        try {
            final ZoneId timeZoneId = ZoneId.of(jsonStr);
            final ZonedDateTime dateTimeWithZone = LocalDateTime.now().atZone(timeZoneId);
            return new SimpleTimeZone(dateTimeWithZone.getOffset().getTotalSeconds() * 1000, timeZoneId.getId());
        } catch (ZoneRulesException zoneRulesEx) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.ZONE_PARSE_ERROR, jsonStr), zoneRulesEx);
        }
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public TimeZoneTypeConverter(Customization customConfig) {
        super(TimeZone.class, customConfig);
    }

}

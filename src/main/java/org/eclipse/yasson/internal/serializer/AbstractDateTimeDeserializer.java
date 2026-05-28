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
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbDateFormat;
import org.eclipse.yasson.internal.JsonbDeserializer;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

/**
 * Abstract class for converting date objects from java.time.
 *
 * @param <T> date type
 */
public abstract class AbstractDateTimeDeserializer<T> extends AbstractValueTypeDeserializer<T> {

    /**
     * Default zone id.
     */
    public static final ZoneId UTC = ZoneId.of("UTC");

    private T parseWithFormatterInternal(String jsonValue, DateTimeFormatter formatter) {
        try {
            return parseWithFormatter(jsonValue, formatter);
        } catch (DateTimeException e) {
            throw new JsonbException(Messages.getMessage(MessageKeys.DATE_PARSE_ERROR, jsonValue, getPropertyType()), e);
        }
    }

    /**
     * Append UTC zone in case zone is not set on formatter.
     *
     * @param formatter formatter
     * @return zoned formatter
     */
    protected DateTimeFormatter getZonedFormatter(DateTimeFormatter formatter) {
        return null != formatter.getZone() ? formatter : formatter.withZone(UTC);
    }

    /**
     * Parse java.time date object with provided formatter.
     *
     * @param jsonValue string value to parse from
     * @param formatter a formatter to use
     * @return parsed date object
     */
    protected abstract T parseWithFormatter(String jsonValue, DateTimeFormatter formatter);

    /**
     * Construct date object from an instant containing epoch millisecond.
     * If date object supports zone offset / zone id, system default is used and warning is logged.
     *
     * @param instant instant to construct from
     * @return date object
     */
    protected abstract T fromInstant(Instant instant);

    /**
     * Parse java.time date object with default formatter.
     * Different default formatter for each date object type is used.
     *
     * @param jsonValue string value to parse from
     * @param locale    annotated locale or default
     * @return parsed date object
     */
    protected abstract T parseDefault(String jsonValue, Locale locale);

    @Override
    public T deserialize(String jsonValue, JsonbDeserializer unmarshaller, Type rtType) {
        final JsonbDateFormatter formatter = getJsonbDateFormatter(unmarshaller.getJsonbContext());
        if (!JsonbDateFormat.TIME_IN_MILLIS.equals(formatter.getFormat())) {
            if (null == formatter.getDateTimeFormatter()) {
                DateTimeFormatter configDateTimeFormatter = unmarshaller.getJsonbContext().getConfigProperties().getConfigDateFormatter().getDateTimeFormatter();
                if (null != configDateTimeFormatter) {
                    return parseWithFormatterInternal(jsonValue, configDateTimeFormatter);
                }
            } else {
                return parseWithFormatterInternal(jsonValue, formatter.getDateTimeFormatter());
            }
        } else {
            return fromInstant(Instant.ofEpochMilli(Long.parseLong(jsonValue)));
        }
        final boolean strictIJson = unmarshaller.getJsonbContext().getConfigProperties().isStrictIJson();
        if (strictIJson) {
            return parseWithFormatterInternal(jsonValue, JsonbDateFormatter.IJSON_DATE_FORMATTER);
        }
        try {
            return parseDefault(jsonValue, unmarshaller.getJsonbContext().getConfigProperties().getLocale(formatter.getLocale()));
        } catch (DateTimeException e) {
            throw new JsonbException(Messages.getMessage(MessageKeys.DATE_PARSE_ERROR, jsonValue, getPropertyType()), e);
        }
    }

    /**
     * Creates an instance.
     *
     * @param clazz         Class to create deserializer for.
     * @param customization Model customization.
     */
    public AbstractDateTimeDeserializer(Class<T> clazz, Customization customization) {
        super(clazz, customization);
    }

    /**
     * Returns registered deserialization jsonb date formatter.
     *
     * @param context context
     * @return date formatter
     */
    protected JsonbDateFormatter getJsonbDateFormatter(JsonbRuntimeContext context) {
        if (null != getCustomization() && null != getCustomization().getDeserializeDateFormatter()) {
            return getCustomization().getDeserializeDateFormatter();
        }
        return context.getConfigProperties().getConfigDateFormatter();
    }

}

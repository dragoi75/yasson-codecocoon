/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;
import javax.json.bind.JsonbException;
import javax.json.bind.annotation.JsonbDateFormat;
import java.lang.reflect.Type;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Abstract class for converting date objects from {@link java.time}.
 *
 * @author Roman Grigoriadi
 */
public abstract class AbstractDateTimeDeserializer<T> extends BaseValueTypeDeserializer<T> {

    public static final ZoneId UTC = ZoneId.of("UTC");

    /**
     * Parse {@link java.time} date object with provided formatter.
     *
     * @param jsonValue string value to parse from
     * @param formatter a formatter to use
     * @return parsed date object
     */
    protected abstract T parseWithFormatter(String jsonValue, DateTimeFormatter formatter);

    /**
     * Parse {@link java.time} date object with default formatter.
     * Different default formatter for each date object type is used.
     *
     * @param jsonValue string value to parse from
     * @param locale annotated locale or default
     * @return parsed date object
     */
    protected abstract T parseDefault(String jsonValue, Locale locale);

    /**
     * Append UTC zone in case zone is not set on formatter.
     *
     * @param formatter formatter
     * @return zoned formatter
     */
    protected DateTimeFormatter getZonedFormatter(DateTimeFormatter formatter) {
        return null != formatter.getZone() ? formatter : formatter.withZone(UTC);
    }

    private T parseWithFormatterInternal(String jsonValue, DateTimeFormatter formatter) {
        try {
            return parseWithFormatter(jsonValue, formatter);
        } catch (DateTimeException e) {
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.DATE_PARSE_ERROR, jsonValue, getPropertyType()), e);
        }
    }

    /**
     * Creates an instance.
     *
     * @param clazz Class to create deserializer for.
     * @param customization Model customization.
     */
    public AbstractDateTimeDeserializer(Class<T> clazz, SerializationCustomization customization) {
        super(clazz, customization);
    }

    /**
     * Construct date object from an instant containing epoch millisecond.
     * If date object supports zone offset / zone id, system default is used and warning is logged.
     *
     * @param instant instant to construct from
     * @return date object
     */
    protected abstract T fromInstant(Instant instant);

    protected JsonbDateTimeFormatter getJsonbDateFormatter(JsonbRuntimeContext context) {
        if (null != getCustomization() && null != getCustomization().getDeserializeDateFormatter()) {
            return getCustomization().getDeserializeDateFormatter();
        }
        return context.getConfigProperties().getConfigDateFormatter();
    }

    @Override
    public T deserializeValue(String jsonValue, JsonbUnmarshaller unmarshaller, Type rtType) {
        final JsonbDateTimeFormatter formatter = getJsonbDateFormatter(unmarshaller.getJsonbContext());
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
            return parseWithFormatterInternal(jsonValue, JsonbDateTimeFormatter.IJSON_DATE_FORMATTER);
        }
        try {
            return parseDefault(jsonValue, unmarshaller.getJsonbContext().getConfigProperties().getLocale(formatter.getLocale()));
        } catch (DateTimeException e) {
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.DATE_PARSE_ERROR, jsonValue, getPropertyType()), e);
        }
    }

}

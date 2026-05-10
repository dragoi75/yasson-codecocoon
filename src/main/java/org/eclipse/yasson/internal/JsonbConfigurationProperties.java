/*******************************************************************************
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
package org.eclipse.yasson.internal;

import org.eclipse.yasson.YassonProperties;
import org.eclipse.yasson.internal.model.customization.naming.DefaultNamingStrategies;
import org.eclipse.yasson.internal.model.customization.naming.IdentityStrategy;
import org.eclipse.yasson.internal.model.customization.ordering.AnyOrderStrategy;
import org.eclipse.yasson.internal.model.customization.ordering.LexicographicalOrderStrategy;
import org.eclipse.yasson.internal.model.customization.ordering.PropOrderStrategy;
import org.eclipse.yasson.internal.model.customization.ordering.PropertyOrdering;
import org.eclipse.yasson.internal.model.customization.ordering.ReverseOrderStrategy;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;

import javax.json.bind.JsonbConfig;
import javax.json.bind.JsonbException;
import javax.json.bind.annotation.JsonbDateFormat;
import javax.json.bind.config.BinaryDataStrategy;
import javax.json.bind.config.PropertyNamingStrategy;
import javax.json.bind.config.PropertyOrderStrategy;
import javax.json.bind.config.PropertyVisibilityStrategy;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolved properties from JSONB config.
 *
 * @author Roman Grigoriadi
 */
public class JsonbConfigurationProperties {

    private final JsonbConfig mappingConfig;

    private final PropertyVisibilityStrategy visibilityStrategy;

    private final PropertyNamingStrategy namingStrategy;

    private final PropertyOrdering orderingStrategy;

    private final JsonbDateFormatter dateTimeFormatterProvider;

    private final Locale languageTag;

    private final String binaryEncoding;

    private final boolean allowNulls;

    private final boolean errorOnUnknown;

    private final boolean iJsonStrictMode;

    private final boolean defaultZeroTime;

    private final Map<Class<?>, Class<?>> customTypeMap;

    public JsonbConfigurationProperties(JsonbConfig mappingConfig) {
        this.mappingConfig = mappingConfig;
        this.binaryEncoding = resolveBinaryDataStrategy();
        this.namingStrategy = resolvePropertyNamingStrategy();
        this.visibilityStrategy = resolvePropertyVisibilityStrategy();
        this.orderingStrategy = new PropertyOrdering(resolveOrderStrategy());
        this.languageTag = resolveConfigLocale();
        this.dateTimeFormatterProvider = createDateFormatter(this.languageTag);
        this.allowNulls = resolveConfigNullable();
        this.errorOnUnknown = resolveConfigFailOnUnknownProperties();
        this.iJsonStrictMode = resolveStrictJson();
        this.customTypeMap = loadUserTypeMapping();
        this.defaultZeroTime = enableZeroTimeDefaultingForJavaTime();
    }

    private boolean enableZeroTimeDefaultingForJavaTime() {
        return getBooleanConfigProperty(YassonProperties.ZERO_TIME_PARSE_DEFAULTING, false);
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>,Class<?>> loadUserTypeMapping() {
        Optional<Object> optionalValue = mappingConfig.getProperty(YassonProperties.USER_TYPE_MAPPING);
        if (!optionalValue.isPresent()) {
            return Collections.emptyMap();
        }
        Object resolvedValue = optionalValue.get();
        if (!(resolvedValue instanceof Map)) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, YassonProperties.USER_TYPE_MAPPING, Map.class.getSimpleName()));
        }
        return (Map<Class<?>, Class<?>>) resolvedValue;
    }

    private JsonbDateFormatter createDateFormatter(Locale languageTag) {
        final String formatPattern = getGlobalConfigJsonbDateFormat();
        if (JsonbDateFormat.DEFAULT_FORMAT.equals(formatPattern) || JsonbDateFormat.TIME_IN_MILLIS.equals(formatPattern)) {
            return new JsonbDateFormatter(formatPattern, languageTag.toLanguageTag());
        }
        DateTimeFormatterBuilder fmtAssembler = new DateTimeFormatterBuilder();
        fmtAssembler.appendPattern(formatPattern);
        if (isZeroTimeDefaulting()) {
            fmtAssembler.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            fmtAssembler.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            fmtAssembler.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter formatter = fmtAssembler.toFormatter(languageTag);
        return new JsonbDateFormatter(formatter, formatPattern, languageTag.toLanguageTag());
    }

    private String getGlobalConfigJsonbDateFormat() {
        final Optional<Object> formatOption = mappingConfig.getProperty(JsonbConfig.DATE_FORMAT);
        return formatOption.map(formatterFunction -> {
            if (!(formatterFunction instanceof String)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, JsonbConfig.DATE_FORMAT, String.class.getSimpleName()));
            }
            return (String) formatterFunction;
        }).orElse(JsonbDateFormat.DEFAULT_FORMAT);
    }

    private PropOrderStrategy resolveOrderStrategy() {
        final Optional<Object> optionalValue = mappingConfig.getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY);
        if (optionalValue.isPresent()) {
            final Object resolvedPolicy = optionalValue.get();
            if (!(resolvedPolicy instanceof String)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.PROPERTY_ORDER, resolvedPolicy));
            }
            switch ((String) resolvedPolicy) {
                case PropertyOrderStrategy.LEXICOGRAPHICAL:
                    return new LexicographicalOrderStrategy();
                case PropertyOrderStrategy.REVERSE:
                    return new ReverseOrderStrategy();
                case PropertyOrderStrategy.ANY:
                    return new AnyOrderStrategy();
                default:
                    throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.PROPERTY_ORDER, resolvedPolicy));
            }
        }
        //default by spec
        return new LexicographicalOrderStrategy();
    }

    private PropertyNamingStrategy resolvePropertyNamingStrategy() {
        final Optional<Object> optionalValue = mappingConfig.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY);
        if (!optionalValue.isPresent()) {
            return new IdentityStrategy();
        }
        Object namingStrategy = optionalValue.get();
        if (namingStrategy instanceof String) {
            String namingKey = (String) namingStrategy;
            final PropertyNamingStrategy resolvedNaming = DefaultNamingStrategies.getStrategy(namingKey);
            if (resolvedNaming == null) {
                throw new JsonbException("No property naming strategy was found for: " + namingKey);
            }
            return resolvedNaming;
        }
        if (!(namingStrategy instanceof PropertyNamingStrategy)) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.PROPERTY_NAMING_STRATEGY_INVALID));
        }
        return (PropertyNamingStrategy) optionalValue.get();
    }

    private PropertyVisibilityStrategy resolvePropertyVisibilityStrategy() {
        final Optional<Object> optionalValue = mappingConfig.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY);
        if (!optionalValue.isPresent()) {
            return null;
        }
        final Object visibilityStrategy = optionalValue.get();
        if (!(visibilityStrategy instanceof PropertyVisibilityStrategy)) {
            throw new JsonbException("JsonbConfig.PROPERTY_VISIBILITY_STRATEGY must be instance of " + PropertyVisibilityStrategy.class);
        }
        return (PropertyVisibilityStrategy) visibilityStrategy;
    }

    private String resolveBinaryDataStrategy() {
        final Optional<Boolean> iJsonFlag = mappingConfig.getProperty(JsonbConfig.STRICT_IJSON).map((value ->(Boolean) value));
        if (iJsonFlag.isPresent() && iJsonFlag.get()) {
            return BinaryDataStrategy.BASE_64_URL;
        }
        final Optional<String> resolvedPolicy = mappingConfig.getProperty(JsonbConfig.BINARY_DATA_STRATEGY).map((value) -> (String) value);
        return resolvedPolicy.orElse(BinaryDataStrategy.BYTE);
    }

    private boolean resolveConfigNullable() {
        return getBooleanConfigProperty(JsonbConfig.NULL_VALUES, false);
    }

    private boolean resolveConfigFailOnUnknownProperties() {
        return getBooleanConfigProperty(YassonProperties.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Gets nullable from {@link JsonbConfig}.
     * If true null values are serialized to json.
     *
     * @return Configured nullable
     */
    public boolean getConfigNullable() {
        return allowNulls;
    }

    /**
     * Gets unknown properties flag from {@link JsonbConfig}.
     * If false, {@link JsonbException} is not thrown for deserialization, when json key
     * cannot be mapped to class property.
     *
     * @return
     *      {@link JsonbException} is risen on unknown property. Default is true even if
     *      not set in json config.
     */
    public boolean getConfigFailOnUnknownProperties() {
        return errorOnUnknown;
    }

    private boolean getBooleanConfigProperty(String configKey, boolean defaultFlag) {
        final Optional<Object> optionalValue = mappingConfig.getProperty(configKey);
        if (optionalValue.isPresent()) {
            final Object resolvedValue = optionalValue.get();
            if (!(resolvedValue instanceof Boolean)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, configKey, Boolean.class.getSimpleName()));
            }
            return (boolean) resolvedValue;
        }
        return defaultFlag;
    }

    /**
     * Checks for binary data strategy to use.
     *
     * @return Binary data strategy.
     */
    public  String getBinaryDataStrategy() {
        return binaryEncoding;
    }

    /**
     * Converts string locale to {@link Locale}.
     *
     * @param languageTag Locale to convert.
     * @return {@link Locale} instance.
     */
    public Locale getLocale(String languageTag) {
        if (languageTag.equals(JsonbDateFormat.DEFAULT_LOCALE)) {
            return this.languageTag;
        }
        return Locale.forLanguageTag(languageTag);
    }

    /**
     * Gets locale from {@link JsonbConfig}.
     *
     * @return Configured locale.
     */
    private Locale resolveConfigLocale() {
        final Optional<Object> regionCandidate = mappingConfig.getProperty(JsonbConfig.LOCALE);
        return  regionCandidate.map(regionId -> {
            if (!(regionId instanceof Locale)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, JsonbConfig.LOCALE, Locale.class.getSimpleName()));
            }
            return (Locale) regionId;
        }).orElseGet(Locale::getDefault);
    }

    private boolean resolveStrictJson() {
        return getBooleanConfigProperty(JsonbConfig.STRICT_IJSON, false);
    }

    /**
     * Gets property visibility strategy.
     *
     * @return Property visibility strategy.
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy() {
        return visibilityStrategy;
    }

    /**
     * Gets property naming strategy.
     *
     * @return Property naming strategy.
     */
    public PropertyNamingStrategy getPropertyNamingStrategy() {
        return namingStrategy;
    }

    /**
     * Gets instantiated shared config date formatter.
     *
     * @return Date formatter.
     */
    public JsonbDateFormatter getConfigDateFormatter() {
        return dateTimeFormatterProvider;
    }

    /**
     * Gets property ordering component.
     *
     * @return Component for ordering properties.
     */
    public PropertyOrdering getPropertyOrdering() {
        return orderingStrategy;
    }

    /**
     * If strict IJSON patterns should be used.
     *
     * @return if IJSON is enabled
     */
    public boolean isStrictIJson() {
        return iJsonStrictMode;
    }

    /**
     * User type mapping for map interface to implementation classes.
     *
     * @return User type mapping.
     */
    public Map<Class<?>, Class<?>> getUserTypeMapping() {
        return customTypeMap;
    }

    /**
     * <p>Makes parsing dates defaulting to zero hour, minute and second.
     * This will made available to parse patterns like yyyy.MM.dd to
     * {@link java.util.Date}, {@link java.util.Calendar}, {@link java.time.Instant} {@link java.time.LocalDate}
     * or even {@link java.time.ZonedDateTime}.
     * <p>If time zone is not set in the pattern than UTC time zone is used.
     * So for example json value 2018.01.01 becomes 2018.01.01 00:00:00 UTC when parsed
     * to instant {@link java.time.Instant}.
     *
     * @return true if time should be defaulted to zero.
     */
    public boolean isZeroTimeDefaulting() {
        return defaultZeroTime;
    }
}

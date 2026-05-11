/**
 * ****************************************************************************
 *  Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
import org.eclipse.yasson.internal.properties.MessageConstants;
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

    private final JsonbConfig jsonbSettings;

    private final PropertyVisibilityStrategy visibilityStrategy;

    private final PropertyNamingStrategy namingStrategy;

    private final PropertyOrdering propertyOrder;

    private final JsonbDateFormatter dateParser;

    private final Locale localeSetting;

    private final String binaryEncoding;

    private final boolean allowNulls;

    private final boolean throwOnUnknown;

    private final boolean strictJsonMode;

    private final boolean defaultZeroTime;

    private final Map<Class<?>, Class<?>> typeMappings;

    public JsonbConfigurationProperties(JsonbConfig jsonbSettings) {
        this.jsonbSettings = jsonbSettings;
        this.binaryEncoding = determineBinaryDataStrategy();
        this.namingStrategy = initNamingStrategy();
        this.visibilityStrategy = initVisibilityStrategy();
        this.propertyOrder = new PropertyOrdering(determineOrderStrategy());
        this.localeSetting = initLocaleFromConfig();
        this.dateParser = createDateFormatter(this.localeSetting);
        this.allowNulls = initNullValuesConfig();
        this.throwOnUnknown = initFailOnUnknownProperties();
        this.strictJsonMode = initStrictIJson();
        this.typeMappings = loadUserTypeMapping();
        this.defaultZeroTime = enableZeroTimeDefaulting();
    }

    private boolean enableZeroTimeDefaulting() {
        return getBooleanConfigProperty(YassonProperties.ZERO_TIME_PARSE_DEFAULTING, false);
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, Class<?>> loadUserTypeMapping() {
        Optional<Object> configEntry = jsonbSettings.getProperty(YassonProperties.USER_TYPE_MAPPING);
        if (!configEntry.isPresent()) {
            return Collections.emptyMap();
        }
        Object outcome = configEntry.get();
        if (!(outcome instanceof Map)) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, YassonProperties.USER_TYPE_MAPPING, Map.class.getSimpleName()));
        }
        return (Map<Class<?>, Class<?>>) outcome;
    }

    private JsonbDateFormatter createDateFormatter(Locale localeSetting) {
        final String formatPattern = getGlobalConfigJsonbDateFormat();
        if (JsonbDateFormat.DEFAULT_FORMAT.equals(formatPattern) || JsonbDateFormat.TIME_IN_MILLIS.equals(formatPattern)) {
            return new JsonbDateFormatter(formatPattern, localeSetting.toLanguageTag());
        }
        DateTimeFormatterBuilder formatterBuilder = new DateTimeFormatterBuilder();
        formatterBuilder.appendPattern(formatPattern);
        if (isZeroTimeDefaulting()) {
            formatterBuilder.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            formatterBuilder.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            formatterBuilder.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter dateTimeFmt = formatterBuilder.toFormatter(localeSetting);
        return new JsonbDateFormatter(dateTimeFmt, formatPattern, localeSetting.toLanguageTag());
    }

    private String getGlobalConfigJsonbDateFormat() {
        final Optional<Object> formatConfig = jsonbSettings.getProperty(JsonbConfig.DATE_FORMAT);
        return formatConfig.map(func -> {
            if (!(func instanceof String)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, JsonbConfig.DATE_FORMAT, String.class.getSimpleName()));
            }
            return (String) func;
        }).orElse(JsonbDateFormat.DEFAULT_FORMAT);
    }

    private PropOrderStrategy determineOrderStrategy() {
        final Optional<Object> configEntry = jsonbSettings.getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY);
        if (configEntry.isPresent()) {
            final Object orderAlgo = configEntry.get();
            if (!(orderAlgo instanceof String)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.PROPERTY_ORDER, orderAlgo));
            }
            switch((String) orderAlgo) {
                case PropertyOrderStrategy.LEXICOGRAPHICAL:
                    return new LexicographicalOrderStrategy();
                case PropertyOrderStrategy.REVERSE:
                    return new ReverseOrderStrategy();
                case PropertyOrderStrategy.ANY:
                    return new AnyOrderStrategy();
                default:
                    throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.PROPERTY_ORDER, orderAlgo));
            }
        }
        //default by spec
        return new LexicographicalOrderStrategy();
    }

    private PropertyNamingStrategy initNamingStrategy() {
        final Optional<Object> configEntry = jsonbSettings.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY);
        if (!configEntry.isPresent()) {
            return new IdentityStrategy();
        }
        Object namingStrategy = configEntry.get();
        if (namingStrategy instanceof String) {
            String strategyName = (String) namingStrategy;
            final PropertyNamingStrategy detectedNamingPolicy = DefaultNamingStrategies.getStrategy(strategyName);
            if (null == detectedNamingPolicy) {
                throw new JsonbException("No property naming strategy was found for: " + strategyName);
            }
            return detectedNamingPolicy;
        }
        if (!(namingStrategy instanceof PropertyNamingStrategy)) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.PROPERTY_NAMING_STRATEGY_INVALID));
        }
        return (PropertyNamingStrategy) configEntry.get();
    }

    private PropertyVisibilityStrategy initVisibilityStrategy() {
        final Optional<Object> configEntry = jsonbSettings.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY);
        if (!configEntry.isPresent()) {
            return null;
        }
        final Object visibilityStrategy = configEntry.get();
        if (!(visibilityStrategy instanceof PropertyVisibilityStrategy)) {
            throw new JsonbException("JsonbConfig.PROPERTY_VISIBILITY_STRATEGY must be instance of " + PropertyVisibilityStrategy.class);
        }
        return (PropertyVisibilityStrategy) visibilityStrategy;
    }

    private String determineBinaryDataStrategy() {
        final Optional<Boolean> interopJsonOpt = jsonbSettings.getProperty(JsonbConfig.STRICT_IJSON).map((inputObj -> (Boolean) inputObj));
        if (interopJsonOpt.isPresent() && interopJsonOpt.get()) {
            return BinaryDataStrategy.BASE_64_URL;
        }
        final Optional<String> orderAlgo = jsonbSettings.getProperty(JsonbConfig.BINARY_DATA_STRATEGY).map((inputObj) -> (String) inputObj);
        return orderAlgo.orElse(BinaryDataStrategy.BYTE);
    }

    private boolean initNullValuesConfig() {
        return getBooleanConfigProperty(JsonbConfig.NULL_VALUES, false);
    }

    private boolean initFailOnUnknownProperties() {
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
        return throwOnUnknown;
    }

    private boolean getBooleanConfigProperty(String configKey, boolean defaultFlag) {
        final Optional<Object> configEntry = jsonbSettings.getProperty(configKey);
        if (configEntry.isPresent()) {
            final Object outcome = configEntry.get();
            if (!(outcome instanceof Boolean)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, configKey, Boolean.class.getSimpleName()));
            }
            return (boolean) outcome;
        }
        return defaultFlag;
    }

    /**
     * Checks for binary data strategy to use.
     *
     * @return Binary data strategy.
     */
    public String getBinaryDataStrategy() {
        return binaryEncoding;
    }

    /**
     * Converts string locale to {@link Locale}.
     *
     * @param localeSetting Locale to convert.
     * @return {@link Locale} instance.
     */
    public Locale getLocale(String localeSetting) {
        if (localeSetting.equals(JsonbDateFormat.DEFAULT_LOCALE)) {
            return this.localeSetting;
        }
        return Locale.forLanguageTag(localeSetting);
    }

    /**
     * Gets locale from {@link JsonbConfig}.
     *
     * @return Configured locale.
     */
    private Locale initLocaleFromConfig() {
        final Optional<Object> localeConfigOpt = jsonbSettings.getProperty(JsonbConfig.LOCALE);
        return localeConfigOpt.map(entry -> {
            if (!(entry instanceof Locale)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, JsonbConfig.LOCALE, Locale.class.getSimpleName()));
            }
            return (Locale) entry;
        }).orElseGet(Locale::getDefault);
    }

    private boolean initStrictIJson() {
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
        return dateParser;
    }

    /**
     * Gets property ordering component.
     *
     * @return Component for ordering properties.
     */
    public PropertyOrdering getPropertyOrdering() {
        return propertyOrder;
    }

    /**
     * If strict IJSON patterns should be used.
     *
     * @return if IJSON is enabled
     */
    public boolean isStrictIJson() {
        return strictJsonMode;
    }

    /**
     * User type mapping for map interface to implementation classes.
     *
     * @return User type mapping.
     */
    public Map<Class<?>, Class<?>> getUserTypeMapping() {
        return typeMappings;
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

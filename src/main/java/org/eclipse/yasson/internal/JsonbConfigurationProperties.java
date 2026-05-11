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

import org.eclipse.yasson.YassonConfiguration;
import org.eclipse.yasson.internal.model.ReversedTreeMap;
import org.eclipse.yasson.internal.model.customization.naming.DefaultNamingStrategyProvider;
import org.eclipse.yasson.internal.model.customization.naming.IdentityNamingStrategy;
import org.eclipse.yasson.internal.model.customization.ordering.ArbitraryOrderStrategy;
import org.eclipse.yasson.internal.model.customization.ordering.LexicographicalPropertyOrderStrategy;
import org.eclipse.yasson.internal.model.customization.ordering.PropertyOrderManager;
import org.eclipse.yasson.internal.model.customization.ordering.PropertyOrderStrategy;
import org.eclipse.yasson.internal.model.customization.ordering.ReversePropertyOrderStrategy;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;
import org.eclipse.yasson.internal.serializer.JsonbDateTimeFormatter;
import javax.json.bind.JsonbConfig;
import javax.json.bind.JsonbException;
import javax.json.bind.annotation.JsonbDateFormat;
import javax.json.bind.config.BinaryDataStrategy;
import javax.json.bind.config.PropertyNamingStrategy;
import javax.json.bind.config.PropertyVisibilityStrategy;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Resolved properties from JSONB config.
 *
 * @author Roman Grigoriadi
 */
public class JsonbConfigurationProperties {

    private final JsonbConfig jsonbSettings;

    private final PropertyVisibilityStrategy visibilityStrategy;

    private final PropertyNamingStrategy namingStrategy;

    private final PropertyOrderManager orderManager;

    private final JsonbDateTimeFormatter dateTimeFormatter;

    private final Locale userRegion;

    private final String binaryEncoding;

    private final boolean allowNull;

    private final boolean rejectUnknowns;

    private final boolean strictInternetJson;

    private final boolean useZeroTimeDefaults;

    private final Map<Class<?>, Class<?>> userTypeMap;

    private final Class<?> defaultMapImplementation;

    public JsonbConfigurationProperties(JsonbConfig jsonbSettings) {
        this.jsonbSettings = jsonbSettings;
        this.binaryEncoding = initializeBinaryDataStrategy();
        this.namingStrategy = initializePropertyNamingStrategy();
        this.visibilityStrategy = initializePropertyVisibilityStrategy();
        this.orderManager = new PropertyOrderManager(initializeOrderStrategy());
        this.userRegion = initializeConfigLocale();
        this.dateTimeFormatter = createDateFormatter(this.userRegion);
        this.allowNull = initializeConfigNullable();
        this.rejectUnknowns = initializeConfigFailOnUnknownProperties();
        this.strictInternetJson = initializeStrictJson();
        this.userTypeMap = initializeUserTypeMapping();
        this.useZeroTimeDefaults = initializeZeroTimeDefaultingForJavaTime();
        this.defaultMapImplementation = initializeDefaultMapImplType();
    }

    private Class<?> initializeDefaultMapImplType() {
        Optional<String> optionalString = getPropertyOrderStrategy();
        if (optionalString.isPresent()) {
            switch(optionalString.get()) {
                case javax.json.bind.config.PropertyOrderStrategy.LEXICOGRAPHICAL:
                    return TreeMap.class;
                case javax.json.bind.config.PropertyOrderStrategy.REVERSE:
                    return ReversedTreeMap.class;
                default:
                    return HashMap.class;
            }
        }
        return HashMap.class;
    }

    private boolean initializeZeroTimeDefaultingForJavaTime() {
        return getBooleanConfigProperty(YassonConfiguration.ZERO_TIME_PARSE_DEFAULTING, false);
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, Class<?>> initializeUserTypeMapping() {
        Optional<Object> maybeValue = jsonbSettings.getProperty(YassonConfiguration.USER_TYPE_MAPPING);
        if (!maybeValue.isPresent()) {
            return Collections.emptyMap();
        }
        Object mappingOutcome = maybeValue.get();
        if (!(mappingOutcome instanceof Map)) {
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, YassonConfiguration.USER_TYPE_MAPPING, Map.class.getSimpleName()));
        }
        return (Map<Class<?>, Class<?>>) mappingOutcome;
    }

    private JsonbDateTimeFormatter createDateFormatter(Locale userRegion) {
        final String formatPattern = getGlobalConfigJsonbDateFormat();
        if (JsonbDateFormat.DEFAULT_FORMAT.equals(formatPattern) || JsonbDateFormat.TIME_IN_MILLIS.equals(formatPattern)) {
            return new JsonbDateTimeFormatter(formatPattern, userRegion.toLanguageTag());
        }
        DateTimeFormatterBuilder formatterFactory = new DateTimeFormatterBuilder();
        formatterFactory.appendPattern(formatPattern);
        if (isZeroTimeDefaulting()) {
            formatterFactory.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            formatterFactory.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            formatterFactory.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter formatter = formatterFactory.toFormatter(userRegion);
        return new JsonbDateTimeFormatter(formatter, formatPattern, userRegion.toLanguageTag());
    }

    private String getGlobalConfigJsonbDateFormat() {
        final Optional<Object> maybeFormat = jsonbSettings.getProperty(JsonbConfig.DATE_FORMAT);
        return maybeFormat.map(fieldValue -> {
            if (!(fieldValue instanceof String)) {
                throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, JsonbConfig.DATE_FORMAT, String.class.getSimpleName()));
            }
            return (String) fieldValue;
        }).orElse(JsonbDateFormat.DEFAULT_FORMAT);
    }

    private PropertyOrderStrategy initializeOrderStrategy() {
        Optional<String> orderOption = getPropertyOrderStrategy();
        if (orderOption.isPresent()) {
            switch(orderOption.get()) {
                case javax.json.bind.config.PropertyOrderStrategy.LEXICOGRAPHICAL:
                    return new LexicographicalPropertyOrderStrategy();
                case javax.json.bind.config.PropertyOrderStrategy.REVERSE:
                    return new ReversePropertyOrderStrategy();
                case javax.json.bind.config.PropertyOrderStrategy.ANY:
                    return new ArbitraryOrderStrategy();
                default:
                    throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.PROPERTY_ORDER, orderOption));
            }
        }
        //default by spec
        return new LexicographicalPropertyOrderStrategy();
    }

    private Optional<String> getPropertyOrderStrategy() {
        final Optional<Object> maybeValue = jsonbSettings.getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY);
        if (maybeValue.isPresent()) {
            final Object orderOption = maybeValue.get();
            if (!(orderOption instanceof String)) {
                throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.PROPERTY_ORDER, orderOption));
            }
            switch((String) orderOption) {
                case javax.json.bind.config.PropertyOrderStrategy.LEXICOGRAPHICAL:
                case javax.json.bind.config.PropertyOrderStrategy.REVERSE:
                case javax.json.bind.config.PropertyOrderStrategy.ANY:
                    return Optional.of((String) orderOption);
                default:
                    throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.PROPERTY_ORDER, orderOption));
            }
        }
        return Optional.empty();
    }

    private PropertyNamingStrategy initializePropertyNamingStrategy() {
        final Optional<Object> maybeValue = jsonbSettings.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY);
        if (!maybeValue.isPresent()) {
            return new IdentityNamingStrategy();
        }
        Object namingStrategy = maybeValue.get();
        if (namingStrategy instanceof String) {
            String schemeName = (String) namingStrategy;
            final PropertyNamingStrategy resolvedNamer = DefaultNamingStrategyProvider.getStrategy(schemeName);
            if (null == resolvedNamer) {
                throw new JsonbException("No property naming strategy was found for: " + schemeName);
            }
            return resolvedNamer;
        }
        if (!(namingStrategy instanceof PropertyNamingStrategy)) {
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.PROPERTY_NAMING_STRATEGY_INVALID));
        }
        return (PropertyNamingStrategy) maybeValue.get();
    }

    private PropertyVisibilityStrategy initializePropertyVisibilityStrategy() {
        final Optional<Object> maybeValue = jsonbSettings.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY);
        if (!maybeValue.isPresent()) {
            return null;
        }
        final Object visibilityStrategy = maybeValue.get();
        if (!(visibilityStrategy instanceof PropertyVisibilityStrategy)) {
            throw new JsonbException("JsonbConfig.PROPERTY_VISIBILITY_STRATEGY must be instance of " + PropertyVisibilityStrategy.class);
        }
        return (PropertyVisibilityStrategy) visibilityStrategy;
    }

    private String initializeBinaryDataStrategy() {
        final Optional<Boolean> internetJsonFlag = jsonbSettings.getProperty(JsonbConfig.STRICT_IJSON).map((element -> (Boolean) element));
        if (internetJsonFlag.isPresent() && internetJsonFlag.get()) {
            return BinaryDataStrategy.BASE_64_URL;
        }
        final Optional<String> orderOption = jsonbSettings.getProperty(JsonbConfig.BINARY_DATA_STRATEGY).map((element) -> (String) element);
        return orderOption.orElse(BinaryDataStrategy.BYTE);
    }

    private boolean initializeConfigNullable() {
        return getBooleanConfigProperty(JsonbConfig.NULL_VALUES, false);
    }

    private boolean initializeConfigFailOnUnknownProperties() {
        return getBooleanConfigProperty(YassonConfiguration.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Gets nullable from {@link JsonbConfig}.
     * If true null values are serialized to json.
     *
     * @return Configured nullable
     */
    public boolean getConfigNullable() {
        return allowNull;
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
        return rejectUnknowns;
    }

    private boolean getBooleanConfigProperty(String configKey, boolean defaultFlag) {
        final Optional<Object> maybeValue = jsonbSettings.getProperty(configKey);
        if (maybeValue.isPresent()) {
            final Object mappingOutcome = maybeValue.get();
            if (!(mappingOutcome instanceof Boolean)) {
                throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, configKey, Boolean.class.getSimpleName()));
            }
            return (boolean) mappingOutcome;
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
     * @param userRegion Locale to convert.
     * @return {@link Locale} instance.
     */
    public Locale getLocale(String userRegion) {
        if (userRegion.equals(JsonbDateFormat.DEFAULT_LOCALE)) {
            return this.userRegion;
        }
        return Locale.forLanguageTag(userRegion);
    }

    /**
     * Gets locale from {@link JsonbConfig}.
     *
     * @return Configured locale.
     */
    private Locale initializeConfigLocale() {
        final Optional<Object> optionalConfigValue = jsonbSettings.getProperty(JsonbConfig.LOCALE);
        return optionalConfigValue.map(langTag -> {
            if (!(langTag instanceof Locale)) {
                throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, JsonbConfig.LOCALE, Locale.class.getSimpleName()));
            }
            return (Locale) langTag;
        }).orElseGet(Locale::getDefault);
    }

    private boolean initializeStrictJson() {
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
    public JsonbDateTimeFormatter getConfigDateFormatter() {
        return dateTimeFormatter;
    }

    /**
     * Gets property ordering component.
     *
     * @return Component for ordering properties.
     */
    public PropertyOrderManager getPropertyOrdering() {
        return orderManager;
    }

    /**
     * If strict IJSON patterns should be used.
     *
     * @return if IJSON is enabled
     */
    public boolean isStrictIJson() {
        return strictInternetJson;
    }

    /**
     * User type mapping for map interface to implementation classes.
     *
     * @return User type mapping.
     */
    public Map<Class<?>, Class<?>> getUserTypeMapping() {
        return userTypeMap;
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
        return useZeroTimeDefaults;
    }

    /**
     * Default {@link java.util.Map} implementation to use, based on order strategy.
     * @return map impl type
     */
    public Class<?> getDefaultMapImplType() {
        return defaultMapImplementation;
    }
}

/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2020 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.JsonbSerializer;

import org.eclipse.yasson.YassonConfig;
import org.eclipse.yasson.internal.model.PropertyModel;
import org.eclipse.yasson.internal.model.ReverseTreeMap;
import org.eclipse.yasson.internal.model.customization.PropertyOrdering;
import org.eclipse.yasson.internal.model.customization.StrategiesProvider;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.NullSerializer;

/**
 * Resolved properties from JSONB config.
 */
public class JsonbConfigurationProperties {

    private final JsonbConfig serializationConfig;

    private final PropertyVisibilityStrategy visibilityStrategy;

    private final PropertyNamingStrategy namingStrategy;

    private final PropertyOrdering orderingStrategy;

    private final JsonbDateFormatter dateTimeFormatter;

    private final Locale defaultLocale;

    private final String binaryStrategy;

    private final boolean allowNulls;

    private final boolean failOnUnknownFields;

    private final boolean strictJsonMode;

    private final boolean zeroTimeFallback;

    private final Map<Class<?>, Class<?>> userTypeMap;

    private final Class<?> defaultMapImplementation;

    private final JsonbSerializer<Object> nullValueSerializer;
    
    private final Set<Class<?>> eagerInitTypes;

    private Consumer<List<PropertyModel>> initOrderingStrategy() {
        Optional<String> orderStrategyOpt = getPropertyOrderStrategy();

        return orderStrategyOpt.map(StrategiesProvider::getOrderingFunction)
                .orElseGet(() -> StrategiesProvider
                        .getOrderingFunction(PropertyOrderStrategy.LEXICOGRAPHICAL));  //default by spec
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
     * Gets instantiated shared config date formatter.
     *
     * @return Date formatter.
     */
    public JsonbDateFormatter getConfigDateFormatter() {
        return dateTimeFormatter;
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
        return zeroTimeFallback;
    }

    /**
     * If strict IJSON patterns should be used.
     *
     * @return if IJSON is enabled
     */
    public boolean isStrictIJson() {
        return strictJsonMode;
    }

    private String getGlobalConfigJsonbDateFormat() {
        final Optional<Object> formatOption = serializationConfig.getProperty(JsonbConfig.DATE_FORMAT);
        return formatOption.map(formatProvider -> {
            if (!(formatProvider instanceof String)) {
                throw new JsonbException(Messages.getMessage(MessageKeys.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                                                             JsonbConfig.DATE_FORMAT,
                                                             String.class.getSimpleName()));
            }
            return (String) formatProvider;
        }).orElse(JsonbDateFormat.DEFAULT_FORMAT);
    }

    /**
     * Gets locale from {@link JsonbConfig}.
     *
     * @return Configured locale.
     */
    private Locale initLocaleConfig() {
        final Optional<Object> languageSetting = serializationConfig.getProperty(JsonbConfig.LOCALE);
        return languageSetting.map(langTag -> {
            if (!(langTag instanceof Locale)) {
                throw new JsonbException(Messages.getMessage(MessageKeys.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                                                             JsonbConfig.LOCALE,
                                                             Locale.class.getSimpleName()));
            }
            return (Locale) langTag;
        }).orElseGet(Locale::getDefault);
    }

    /**
     * Gets unknown properties flag from {@link JsonbConfig}.
     * If false, {@link JsonbException} is not thrown for deserialization, when json key
     * cannot be mapped to class property.
     *
     * @return {@link JsonbException} is risen on unknown property. Default is true even if
     * not set in json config.
     */
    public boolean getConfigFailOnUnknownProperties() {
        return failOnUnknownFields;
    }

    private String resolveBinaryDataStrategy() {
        final Optional<Boolean> iJsonEnabled = serializationConfig.getProperty(JsonbConfig.STRICT_IJSON).map((candidate -> (Boolean) candidate));
        if (iJsonEnabled.isPresent() && iJsonEnabled.get()) {
            return BinaryDataStrategy.BASE_64_URL;
        }
        final Optional<String> orderStrategyOpt = serializationConfig.getProperty(JsonbConfig.BINARY_DATA_STRATEGY).map((candidate) -> (String) candidate);
        return orderStrategyOpt.orElse(BinaryDataStrategy.BYTE);
    }

    private boolean initStrictIJson() {
        return getBooleanConfigProperty(JsonbConfig.STRICT_IJSON, false);
    }

    /**
     * Checks for binary data strategy to use.
     *
     * @return Binary data strategy.
     */
    public String getBinaryDataStrategy() {
        return binaryStrategy;
    }

    private Class<?> determineDefaultMapImplType() {
        Optional<String> operatingSystem = getPropertyOrderStrategy();
        if (operatingSystem.isPresent()) {
            switch (operatingSystem.get()) {
            case PropertyOrderStrategy.LEXICOGRAPHICAL:
                return TreeMap.class;
            case PropertyOrderStrategy.REVERSE:
                return ReverseTreeMap.class;
            default:
                return HashMap.class;
            }
        }
        return HashMap.class;
    }

    private JsonbDateFormatter createDateFormatter(Locale defaultLocale) {
        final String datePattern = getGlobalConfigJsonbDateFormat();
        if (JsonbDateFormat.DEFAULT_FORMAT.equals(datePattern) || JsonbDateFormat.TIME_IN_MILLIS.equals(datePattern)) {
            return new JsonbDateFormatter(datePattern, defaultLocale.toLanguageTag());
        }
        DateTimeFormatterBuilder formatterBuilder = new DateTimeFormatterBuilder();
        formatterBuilder.appendPattern(datePattern);
        if (isZeroTimeDefaulting()) {
            formatterBuilder.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            formatterBuilder.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            formatterBuilder.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter dateTimeFmt = formatterBuilder.toFormatter(defaultLocale);
        return new JsonbDateFormatter(dateTimeFmt, datePattern, defaultLocale.toLanguageTag());
    }

    private boolean initFailOnUnknownProperties() {
        return getBooleanConfigProperty(YassonConfig.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Converts string locale to {@link Locale}.
     *
     * @param defaultLocale Locale to convert.
     * @return {@link Locale} instance.
     */
    public Locale getLocale(String defaultLocale) {
        if (defaultLocale.equals(JsonbDateFormat.DEFAULT_LOCALE)) {
            return this.defaultLocale;
        }
        return Locale.forLanguageTag(defaultLocale);
    }

    /**
     * Gets property visibility strategy.
     *
     * @return Property visibility strategy.
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy() {
        return visibilityStrategy;
    }

    private boolean initNullableConfig() {
        return getBooleanConfigProperty(JsonbConfig.NULL_VALUES, false);
    }

    public Set<Class<?>> getEagerInitClasses() {
        return eagerInitTypes;
    }

    private PropertyNamingStrategy resolvePropertyNamingStrategy() {
        final Optional<Object> optionalValue = serializationConfig.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY);
        if (!optionalValue.isPresent()) {
            return StrategiesProvider.getPropertyNamingStrategy(PropertyNamingStrategy.IDENTITY);
        }
        Object namingStrategy = optionalValue.get();
        if (namingStrategy instanceof String) {
            return StrategiesProvider.getPropertyNamingStrategy((String) namingStrategy);
        }
        if (!(namingStrategy instanceof PropertyNamingStrategy)) {
            throw new JsonbException(Messages.getMessage(MessageKeys.PROPERTY_NAMING_STRATEGY_INVALID));
        }
        return (PropertyNamingStrategy) optionalValue.get();
    }

    /**
     * Default {@link java.util.Map} implementation to use, based on order strategy.
     *
     * @return map impl type
     */
    public Class<?> getDefaultMapImplType() {
        return defaultMapImplementation;
    }

    /**
     * Gets property naming strategy.
     *
     * @return Property naming strategy.
     */
    public PropertyNamingStrategy getPropertyNamingStrategy() {
        return namingStrategy;
    }

    public JsonbSerializer<Object> getNullSerializer() {
        return nullValueSerializer;
    }

    private Optional<String> getPropertyOrderStrategy() {
        final Optional<Object> optionalValue = serializationConfig.getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY);
        if (optionalValue.isPresent()) {
            final Object orderStrategyOpt = optionalValue.get();
            if (!(orderStrategyOpt instanceof String)) {
                throw new JsonbException(Messages.getMessage(MessageKeys.PROPERTY_ORDER, orderStrategyOpt));
            }
            switch ((String) orderStrategyOpt) {
            case PropertyOrderStrategy.LEXICOGRAPHICAL:
            case PropertyOrderStrategy.REVERSE:
            case PropertyOrderStrategy.ANY:
                return Optional.of((String) orderStrategyOpt);
            default:
                throw new JsonbException(Messages.getMessage(MessageKeys.PROPERTY_ORDER, orderStrategyOpt));
            }
        }
        return Optional.empty();
    }

    private PropertyVisibilityStrategy initVisibilityStrategy() {
        final Optional<Object> optionalValue = serializationConfig.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY);
        if (!optionalValue.isPresent()) {
            return null;
        }
        final Object visibilityStrategy = optionalValue.get();
        if (!(visibilityStrategy instanceof PropertyVisibilityStrategy)) {
            throw new JsonbException("JsonbConfig.PROPERTY_VISIBILITY_STRATEGY must be instance of " + PropertyVisibilityStrategy.class);
        }
        return (PropertyVisibilityStrategy) visibilityStrategy;
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, Class<?>> loadUserTypeMapping() {
        Optional<Object> optionalValue = serializationConfig.getProperty(YassonConfig.USER_TYPE_MAPPING);
        if (!optionalValue.isPresent()) {
            return Collections.emptyMap();
        }
        Object resolvedValue = optionalValue.get();
        if (!(resolvedValue instanceof Map)) {
            throw new JsonbException(Messages.getMessage(MessageKeys.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                    YassonConfig.USER_TYPE_MAPPING,
                                                         Map.class.getSimpleName()));
        }
        return (Map<Class<?>, Class<?>>) resolvedValue;
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

    private boolean isZeroTimeDefaultingForJavaTime() {
        return getBooleanConfigProperty(YassonConfig.ZERO_TIME_PARSE_DEFAULTING, false);
    }

    @SuppressWarnings("unchecked")
    private JsonbSerializer<Object> initNullRootSerializer() {
        Optional<Object> optionalValue = serializationConfig.getProperty(YassonConfig.NULL_ROOT_SERIALIZER);
        if (!optionalValue.isPresent()) {
            return new NullSerializer();
        }
        Object nullValueSerializer = optionalValue.get();
        if (!(nullValueSerializer instanceof JsonbSerializer)) {
            throw new JsonbException("YassonConfig.NULL_ROOT_SERIALIZER must be instance of " + JsonbSerializer.class
                                             + "<Object>");
        }
        return (JsonbSerializer<Object>) nullValueSerializer;
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
     * Creates new resolved JSONB config.
     *
     * @param serializationConfig jsonb config
     */
    public JsonbConfigurationProperties(JsonbConfig serializationConfig) {
        this.serializationConfig = serializationConfig;
        this.binaryStrategy = resolveBinaryDataStrategy();
        this.namingStrategy = resolvePropertyNamingStrategy();
        this.visibilityStrategy = initVisibilityStrategy();
        this.orderingStrategy = new PropertyOrdering(initOrderingStrategy());
        this.defaultLocale = initLocaleConfig();
        this.dateTimeFormatter = createDateFormatter(this.defaultLocale);
        this.allowNulls = initNullableConfig();
        this.failOnUnknownFields = initFailOnUnknownProperties();
        this.strictJsonMode = initStrictIJson();
        this.userTypeMap = loadUserTypeMapping();
        this.zeroTimeFallback = isZeroTimeDefaultingForJavaTime();
        this.defaultMapImplementation = determineDefaultMapImplType();
        this.nullValueSerializer = initNullRootSerializer();
        this.eagerInitTypes = loadEagerInitClasses();
    }

    private boolean getBooleanConfigProperty(String configKey, boolean fallbackFlag) {
        final Optional<Object> optionalValue = serializationConfig.getProperty(configKey);
        if (optionalValue.isPresent()) {
            final Object resolvedValue = optionalValue.get();
            if (!(resolvedValue instanceof Boolean)) {
                throw new JsonbException(Messages.getMessage(MessageKeys.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                        configKey,
                                                             Boolean.class.getSimpleName()));
            }
            return (boolean) resolvedValue;
        }
        return fallbackFlag;
    }

    private Set<Class<?>> loadEagerInitClasses() {
        Optional<Object> optionalValue = serializationConfig.getProperty(YassonConfig.EAGER_PARSE_CLASSES);
        if (!optionalValue.isPresent()) {
            return Collections.emptySet();
        }
        Object eagerInitTypes = optionalValue.get();
        if (!(eagerInitTypes instanceof Class<?>[])) {
            throw new JsonbException("YassonConfig.EAGER_PARSE_CLASSES must be instance of Class<?>[]");
        }
        return new HashSet<Class<?>>(Arrays.asList((Class<?>[]) eagerInitTypes));
    }

}

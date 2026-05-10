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
import org.eclipse.yasson.internal.model.BeanPropertyModel;
import org.eclipse.yasson.internal.model.ReverseTreeMap;
import org.eclipse.yasson.internal.model.customization.PropertyOrderer;
import org.eclipse.yasson.internal.model.customization.StrategiesProvider;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.NullSerializer;

/**
 * Resolved properties from JSONB config.
 */
public class JsonbConfigurationProperties {

    private final JsonbConfig configOptions;

    private final PropertyVisibilityStrategy visibilityPolicy;

    private final PropertyNamingStrategy namingPolicy;

    private final PropertyOrderer orderPolicy;

    private final JsonbDateFormatter dateFormatHandler;

    private final Locale region;

    private final String binaryEncodingPolicy;

    private final boolean allowNulls;

    private final boolean failOnUnknown;

    private final boolean strictJsonMode;

    private final boolean zeroTimeFallback;

    private final Map<Class<?>, Class<?>> userTypeMap;

    private final Class<?> defaultMapClass;

    private final JsonbSerializer<Object> nullValueSerializer;
    
    private final Set<Class<?>> eagerInitTypes;

    /**
     * Creates new resolved JSONB config.
     *
     * @param configOptions jsonb config
     */
    public JsonbConfigurationProperties(JsonbConfig configOptions) {
        this.configOptions = configOptions;
        this.binaryEncodingPolicy = resolveBinaryDataStrategy();
        this.namingPolicy = determinePropertyNamingStrategy();
        this.visibilityPolicy = resolvePropertyVisibilityStrategy();
        this.orderPolicy = new PropertyOrderer(determineOrderStrategy());
        this.region = getConfigLocale();
        this.dateFormatHandler = createDateFormatter(this.region);
        this.allowNulls = initNullableConfig();
        this.failOnUnknown = initFailOnUnknownPropertiesConfig();
        this.strictJsonMode = isStrictJsonEnabled();
        this.userTypeMap = loadUserTypeMapping();
        this.zeroTimeFallback = enableZeroTimeDefaultingForJavaTime();
        this.defaultMapClass = determineDefaultMapImplementation();
        this.nullValueSerializer = resolveNullSerializer();
        this.eagerInitTypes = loadEagerInitClasses();
    }

    private Class<?> determineDefaultMapImplementation() {
        Optional<String> operatingSystemOpt = getPropertyOrderStrategy();
        if (operatingSystemOpt.isPresent()) {
            switch (operatingSystemOpt.get()) {
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

    private boolean enableZeroTimeDefaultingForJavaTime() {
        return getBooleanConfigProperty(YassonConfig.ZERO_TIME_PARSE_DEFAULTING, false);
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, Class<?>> loadUserTypeMapping() {
        Optional<Object> propOpt = configOptions.getProperty(YassonConfig.USER_TYPE_MAPPING);
        if (!propOpt.isPresent()) {
            return Collections.emptyMap();
        }
        Object mappingResult = propOpt.get();
        if (!(mappingResult instanceof Map)) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                    YassonConfig.USER_TYPE_MAPPING,
                                                         Map.class.getSimpleName()));
        }
        return (Map<Class<?>, Class<?>>) mappingResult;
    }

    private JsonbDateFormatter createDateFormatter(Locale region) {
        final String datePattern = getGlobalConfigJsonbDateFormat();
        if (JsonbDateFormat.DEFAULT_FORMAT.equals(datePattern) || JsonbDateFormat.TIME_IN_MILLIS.equals(datePattern)) {
            return new JsonbDateFormatter(datePattern, region.toLanguageTag());
        }
        DateTimeFormatterBuilder formatterAssembler = new DateTimeFormatterBuilder();
        formatterAssembler.appendPattern(datePattern);
        if (isZeroTimeDefaulting()) {
            formatterAssembler.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            formatterAssembler.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            formatterAssembler.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter dtFormatter = formatterAssembler.toFormatter(region);
        return new JsonbDateFormatter(dtFormatter, datePattern, region.toLanguageTag());
    }

    private String getGlobalConfigJsonbDateFormat() {
        final Optional<Object> formatOpt = configOptions.getProperty(JsonbConfig.DATE_FORMAT);
        return formatOpt.map(func -> {
            if (!(func instanceof String)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                                                             JsonbConfig.DATE_FORMAT,
                                                             String.class.getSimpleName()));
            }
            return (String) func;
        }).orElse(JsonbDateFormat.DEFAULT_FORMAT);
    }

    private Consumer<List<BeanPropertyModel>> determineOrderStrategy() {
        Optional<String> orderOption = getPropertyOrderStrategy();

        return orderOption.map(StrategiesProvider::getOrderingFunction)
                .orElseGet(() -> StrategiesProvider
                        .getOrderingFunction(PropertyOrderStrategy.LEXICOGRAPHICAL));  //default by spec
    }

    private Optional<String> getPropertyOrderStrategy() {
        final Optional<Object> propOpt = configOptions.getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY);
        if (propOpt.isPresent()) {
            final Object orderOption = propOpt.get();
            if (!(orderOption instanceof String)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.PROPERTY_ORDER, orderOption));
            }
            switch ((String) orderOption) {
            case PropertyOrderStrategy.LEXICOGRAPHICAL:
            case PropertyOrderStrategy.REVERSE:
            case PropertyOrderStrategy.ANY:
                return Optional.of((String) orderOption);
            default:
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.PROPERTY_ORDER, orderOption));
            }
        }
        return Optional.empty();
    }

    private PropertyNamingStrategy determinePropertyNamingStrategy() {
        final Optional<Object> propOpt = configOptions.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY);
        if (!propOpt.isPresent()) {
            return StrategiesProvider.getPropertyNamingStrategy(PropertyNamingStrategy.IDENTITY);
        }
        Object namingPolicy = propOpt.get();
        if (namingPolicy instanceof String) {
            return StrategiesProvider.getPropertyNamingStrategy((String) namingPolicy);
        }
        if (!(namingPolicy instanceof PropertyNamingStrategy)) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.PROPERTY_NAMING_STRATEGY_INVALID));
        }
        return (PropertyNamingStrategy) propOpt.get();
    }

    private PropertyVisibilityStrategy resolvePropertyVisibilityStrategy() {
        final Optional<Object> propOpt = configOptions.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY);
        if (!propOpt.isPresent()) {
            return null;
        }
        final Object visibilityPolicy = propOpt.get();
        if (!(visibilityPolicy instanceof PropertyVisibilityStrategy)) {
            throw new JsonbException("JsonbConfig.PROPERTY_VISIBILITY_STRATEGY must be instance of " + PropertyVisibilityStrategy.class);
        }
        return (PropertyVisibilityStrategy) visibilityPolicy;
    }

    private String resolveBinaryDataStrategy() {
        final Optional<Boolean> inlineJsonOpt = configOptions.getProperty(JsonbConfig.STRICT_IJSON).map((value -> (Boolean) value));
        if (inlineJsonOpt.isPresent() && inlineJsonOpt.get()) {
            return BinaryDataStrategy.BASE_64_URL;
        }
        final Optional<String> orderOption = configOptions.getProperty(JsonbConfig.BINARY_DATA_STRATEGY).map((value) -> (String) value);
        return orderOption.orElse(BinaryDataStrategy.BYTE);
    }

    private boolean initNullableConfig() {
        return getBooleanConfigProperty(JsonbConfig.NULL_VALUES, false);
    }

    private boolean initFailOnUnknownPropertiesConfig() {
        return getBooleanConfigProperty(YassonConfig.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @SuppressWarnings("unchecked")
    private JsonbSerializer<Object> resolveNullSerializer() {
        Optional<Object> propOpt = configOptions.getProperty(YassonConfig.NULL_ROOT_SERIALIZER);
        if (!propOpt.isPresent()) {
            return new NullSerializer();
        }
        Object nullValueSerializer = propOpt.get();
        if (!(nullValueSerializer instanceof JsonbSerializer)) {
            throw new JsonbException("YassonConfig.NULL_ROOT_SERIALIZER must be instance of " + JsonbSerializer.class
                                             + "<Object>");
        }
        return (JsonbSerializer<Object>) nullValueSerializer;
    }
    
    private Set<Class<?>> loadEagerInitClasses() {
        Optional<Object> propOpt = configOptions.getProperty(YassonConfig.EAGER_PARSE_CLASSES);
        if (!propOpt.isPresent()) {
            return Collections.emptySet();
        }
        Object eagerInitTypes = propOpt.get();
        if (!(eagerInitTypes instanceof Class<?>[])) {
            throw new JsonbException("YassonConfig.EAGER_PARSE_CLASSES must be instance of Class<?>[]");
        }
        return new HashSet<Class<?>>(Arrays.asList((Class<?>[]) eagerInitTypes));
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
     * @return {@link JsonbException} is risen on unknown property. Default is true even if
     * not set in json config.
     */
    public boolean getConfigFailOnUnknownProperties() {
        return failOnUnknown;
    }

    private boolean getBooleanConfigProperty(String configKey, boolean fallbackFlag) {
        final Optional<Object> propOpt = configOptions.getProperty(configKey);
        if (propOpt.isPresent()) {
            final Object mappingResult = propOpt.get();
            if (!(mappingResult instanceof Boolean)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                        configKey,
                                                             Boolean.class.getSimpleName()));
            }
            return (boolean) mappingResult;
        }
        return fallbackFlag;
    }

    /**
     * Checks for binary data strategy to use.
     *
     * @return Binary data strategy.
     */
    public String getBinaryDataStrategy() {
        return binaryEncodingPolicy;
    }

    /**
     * Converts string locale to {@link Locale}.
     *
     * @param region Locale to convert.
     * @return {@link Locale} instance.
     */
    public Locale getLocale(String region) {
        if (region.equals(JsonbDateFormat.DEFAULT_LOCALE)) {
            return this.region;
        }
        return Locale.forLanguageTag(region);
    }

    /**
     * Gets locale from {@link JsonbConfig}.
     *
     * @return Configured locale.
     */
    private Locale getConfigLocale() {
        final Optional<Object> languageEntry = configOptions.getProperty(JsonbConfig.LOCALE);
        return languageEntry.map(langTag -> {
            if (!(langTag instanceof Locale)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                                                             JsonbConfig.LOCALE,
                                                             Locale.class.getSimpleName()));
            }
            return (Locale) langTag;
        }).orElseGet(Locale::getDefault);
    }

    private boolean isStrictJsonEnabled() {
        return getBooleanConfigProperty(JsonbConfig.STRICT_IJSON, false);
    }

    /**
     * Gets property visibility strategy.
     *
     * @return Property visibility strategy.
     */
    public PropertyVisibilityStrategy getPropertyVisibilityStrategy() {
        return visibilityPolicy;
    }

    /**
     * Gets property naming strategy.
     *
     * @return Property naming strategy.
     */
    public PropertyNamingStrategy getPropertyNamingStrategy() {
        return namingPolicy;
    }

    /**
     * Gets instantiated shared config date formatter.
     *
     * @return Date formatter.
     */
    public JsonbDateFormatter getConfigDateFormatter() {
        return dateFormatHandler;
    }

    /**
     * Gets property ordering component.
     *
     * @return Component for ordering properties.
     */
    public PropertyOrderer getPropertyOrdering() {
        return orderPolicy;
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
        return zeroTimeFallback;
    }

    /**
     * Default {@link java.util.Map} implementation to use, based on order strategy.
     *
     * @return map impl type
     */
    public Class<?> getDefaultMapImplType() {
        return defaultMapClass;
    }

    public JsonbSerializer<Object> getNullSerializer() {
        return nullValueSerializer;
    }
    
    public Set<Class<?>> getEagerInitClasses() {
        return eagerInitTypes;
    }
}

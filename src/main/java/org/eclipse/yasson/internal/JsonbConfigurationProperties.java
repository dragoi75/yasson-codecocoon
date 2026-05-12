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
import org.eclipse.yasson.internal.model.PropertyMetadata;
import org.eclipse.yasson.internal.model.ReverseTreeMap;
import org.eclipse.yasson.internal.model.customization.PropertyOrderer;
import org.eclipse.yasson.internal.model.customization.StrategiesProvider;
import org.eclipse.yasson.internal.properties.ErrorMessageKeys;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.NullSerializer;

/**
 * Resolved properties from JSONB config.
 */
public class JsonbConfigurationProperties {

    private final JsonbConfig bindingOptions;

    private final PropertyVisibilityStrategy visibilityPolicy;

    private final PropertyNamingStrategy namingConvention;

    private final PropertyOrderer fieldOrderer;

    private final JsonbDateFormatter temporalFormatter;

    private final Locale regionalSetting;

    private final String encodingStrategy;

    private final boolean isOptional;

    private final boolean abortOnUnknown;

    private final boolean strictJsonMode;

    private final boolean defaultZeroTime;

    private final Map<Class<?>, Class<?>> customTypeMap;

    private final Class<?> mapImplementationType;

    private final JsonbSerializer<Object> nullHandler;
    
    private final Set<Class<?>> preloadClasses;

    /**
     * Creates new resolved JSONB config.
     *
     * @param bindingOptions jsonb config
     */
    public JsonbConfigurationProperties(JsonbConfig bindingOptions) {
        this.bindingOptions = bindingOptions;
        this.encodingStrategy = initializeBinaryDataStrategy();
        this.namingConvention = initializePropertyNamingStrategy();
        this.visibilityPolicy = initializePropertyVisibilityStrategy();
        this.fieldOrderer = new PropertyOrderer(initPropertyOrderStrategy());
        this.regionalSetting = initializeConfigLocale();
        this.temporalFormatter = initializeDateFormatter(this.regionalSetting);
        this.isOptional = initializeConfigNullable();
        this.abortOnUnknown = initializeFailOnUnknownProperties();
        this.strictJsonMode = initializeStrictJsonMode();
        this.customTypeMap = initializeUserTypeMapping();
        this.defaultZeroTime = initZeroTimeDefaulting();
        this.mapImplementationType = determineDefaultMapImplType();
        this.nullHandler = initializeNullSerializer();
        this.preloadClasses = initializeEagerInitClasses();
    }

    private Class<?> determineDefaultMapImplType() {
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

    private boolean initZeroTimeDefaulting() {
        return getBooleanConfigProperty(YassonConfig.ZERO_TIME_PARSE_DEFAULTING, false);
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, Class<?>> initializeUserTypeMapping() {
        Optional<Object> configEntry = bindingOptions.getProperty(YassonConfig.USER_TYPE_MAPPING);
        if (!configEntry.isPresent()) {
            return Collections.emptyMap();
        }
        Object outcome = configEntry.get();
        if (!(outcome instanceof Map)) {
            throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                    YassonConfig.USER_TYPE_MAPPING,
                                                         Map.class.getSimpleName()));
        }
        return (Map<Class<?>, Class<?>>) outcome;
    }

    private JsonbDateFormatter initializeDateFormatter(Locale regionalSetting) {
        final String formatPattern = getGlobalConfigJsonbDateFormat();
        if (JsonbDateFormat.DEFAULT_FORMAT.equals(formatPattern) || JsonbDateFormat.TIME_IN_MILLIS.equals(formatPattern)) {
            return new JsonbDateFormatter(formatPattern, regionalSetting.toLanguageTag());
        }
        DateTimeFormatterBuilder formatterBuilder = new DateTimeFormatterBuilder();
        formatterBuilder.appendPattern(formatPattern);
        if (isZeroTimeDefaulting()) {
            formatterBuilder.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            formatterBuilder.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            formatterBuilder.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter dateFormatterInstance = formatterBuilder.toFormatter(regionalSetting);
        return new JsonbDateFormatter(dateFormatterInstance, formatPattern, regionalSetting.toLanguageTag());
    }

    private String getGlobalConfigJsonbDateFormat() {
        final Optional<Object> formatSettingOpt = bindingOptions.getProperty(JsonbConfig.DATE_FORMAT);
        return formatSettingOpt.map(formatSupplier -> {
            if (!(formatSupplier instanceof String)) {
                throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                                                             JsonbConfig.DATE_FORMAT,
                                                             String.class.getSimpleName()));
            }
            return (String) formatSupplier;
        }).orElse(JsonbDateFormat.DEFAULT_FORMAT);
    }

    private Consumer<List<PropertyMetadata>> initPropertyOrderStrategy() {
        Optional<String> orderPolicyOpt = getPropertyOrderStrategy();

        return orderPolicyOpt.map(StrategiesProvider::getOrderingFunction)
                .orElseGet(() -> StrategiesProvider
                        .getOrderingFunction(PropertyOrderStrategy.LEXICOGRAPHICAL));  //default by spec
    }

    private Optional<String> getPropertyOrderStrategy() {
        final Optional<Object> configEntry = bindingOptions.getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY);
        if (configEntry.isPresent()) {
            final Object orderPolicyOpt = configEntry.get();
            if (!(orderPolicyOpt instanceof String)) {
                throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.PROPERTY_ORDER, orderPolicyOpt));
            }
            switch ((String) orderPolicyOpt) {
            case PropertyOrderStrategy.LEXICOGRAPHICAL:
            case PropertyOrderStrategy.REVERSE:
            case PropertyOrderStrategy.ANY:
                return Optional.of((String) orderPolicyOpt);
            default:
                throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.PROPERTY_ORDER, orderPolicyOpt));
            }
        }
        return Optional.empty();
    }

    private PropertyNamingStrategy initializePropertyNamingStrategy() {
        final Optional<Object> configEntry = bindingOptions.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY);
        if (!configEntry.isPresent()) {
            return StrategiesProvider.getPropertyNamingStrategy(PropertyNamingStrategy.IDENTITY);
        }
        Object namingConvention = configEntry.get();
        if (namingConvention instanceof String) {
            return StrategiesProvider.getPropertyNamingStrategy((String) namingConvention);
        }
        if (!(namingConvention instanceof PropertyNamingStrategy)) {
            throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.PROPERTY_NAMING_STRATEGY_INVALID));
        }
        return (PropertyNamingStrategy) configEntry.get();
    }

    private PropertyVisibilityStrategy initializePropertyVisibilityStrategy() {
        final Optional<Object> configEntry = bindingOptions.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY);
        if (!configEntry.isPresent()) {
            return null;
        }
        final Object visibilityPolicy = configEntry.get();
        if (!(visibilityPolicy instanceof PropertyVisibilityStrategy)) {
            throw new JsonbException("JsonbConfig.PROPERTY_VISIBILITY_STRATEGY must be instance of " + PropertyVisibilityStrategy.class);
        }
        return (PropertyVisibilityStrategy) visibilityPolicy;
    }

    private String initializeBinaryDataStrategy() {
        final Optional<Boolean> jsonFlagOpt = bindingOptions.getProperty(JsonbConfig.STRICT_IJSON).map((inputValue -> (Boolean) inputValue));
        if (jsonFlagOpt.isPresent() && jsonFlagOpt.get()) {
            return BinaryDataStrategy.BASE_64_URL;
        }
        final Optional<String> orderPolicyOpt = bindingOptions.getProperty(JsonbConfig.BINARY_DATA_STRATEGY).map((inputValue) -> (String) inputValue);
        return orderPolicyOpt.orElse(BinaryDataStrategy.BYTE);
    }

    private boolean initializeConfigNullable() {
        return getBooleanConfigProperty(JsonbConfig.NULL_VALUES, false);
    }

    private boolean initializeFailOnUnknownProperties() {
        return getBooleanConfigProperty(YassonConfig.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @SuppressWarnings("unchecked")
    private JsonbSerializer<Object> initializeNullSerializer() {
        Optional<Object> configEntry = bindingOptions.getProperty(YassonConfig.NULL_ROOT_SERIALIZER);
        if (!configEntry.isPresent()) {
            return new NullSerializer();
        }
        Object nullHandler = configEntry.get();
        if (!(nullHandler instanceof JsonbSerializer)) {
            throw new JsonbException("YassonConfig.NULL_ROOT_SERIALIZER must be instance of " + JsonbSerializer.class
                                             + "<Object>");
        }
        return (JsonbSerializer<Object>) nullHandler;
    }
    
    private Set<Class<?>> initializeEagerInitClasses() {
        Optional<Object> configEntry = bindingOptions.getProperty(YassonConfig.EAGER_PARSE_CLASSES);
        if (!configEntry.isPresent()) {
            return Collections.emptySet();
        }
        Object preloadClasses = configEntry.get();
        if (!(preloadClasses instanceof Class<?>[])) {
            throw new JsonbException("YassonConfig.EAGER_PARSE_CLASSES must be instance of Class<?>[]");
        }
        return new HashSet<Class<?>>(Arrays.asList((Class<?>[]) preloadClasses));
    }

    /**
     * Gets nullable from {@link JsonbConfig}.
     * If true null values are serialized to json.
     *
     * @return Configured nullable
     */
    public boolean getConfigNullable() {
        return isOptional;
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
        return abortOnUnknown;
    }

    private boolean getBooleanConfigProperty(String configKey, boolean defaultFlag) {
        final Optional<Object> configEntry = bindingOptions.getProperty(configKey);
        if (configEntry.isPresent()) {
            final Object outcome = configEntry.get();
            if (!(outcome instanceof Boolean)) {
                throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                        configKey,
                                                             Boolean.class.getSimpleName()));
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
        return encodingStrategy;
    }

    /**
     * Converts string locale to {@link Locale}.
     *
     * @param regionalSetting Locale to convert.
     * @return {@link Locale} instance.
     */
    public Locale getLocale(String regionalSetting) {
        if (regionalSetting.equals(JsonbDateFormat.DEFAULT_LOCALE)) {
            return this.regionalSetting;
        }
        return Locale.forLanguageTag(regionalSetting);
    }

    /**
     * Gets locale from {@link JsonbConfig}.
     *
     * @return Configured locale.
     */
    private Locale initializeConfigLocale() {
        final Optional<Object> configuredLocale = bindingOptions.getProperty(JsonbConfig.LOCALE);
        return configuredLocale.map(langTag -> {
            if (!(langTag instanceof Locale)) {
                throw new JsonbException(MessageBundle.getMessage(ErrorMessageKeys.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                                                             JsonbConfig.LOCALE,
                                                             Locale.class.getSimpleName()));
            }
            return (Locale) langTag;
        }).orElseGet(Locale::getDefault);
    }

    private boolean initializeStrictJsonMode() {
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
        return namingConvention;
    }

    /**
     * Gets instantiated shared config date formatter.
     *
     * @return Date formatter.
     */
    public JsonbDateFormatter getConfigDateFormatter() {
        return temporalFormatter;
    }

    /**
     * Gets property ordering component.
     *
     * @return Component for ordering properties.
     */
    public PropertyOrderer getPropertyOrdering() {
        return fieldOrderer;
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

    /**
     * Default {@link java.util.Map} implementation to use, based on order strategy.
     *
     * @return map impl type
     */
    public Class<?> getDefaultMapImplType() {
        return mapImplementationType;
    }

    public JsonbSerializer<Object> getNullSerializer() {
        return nullHandler;
    }
    
    public Set<Class<?>> getEagerInitClasses() {
        return preloadClasses;
    }
}

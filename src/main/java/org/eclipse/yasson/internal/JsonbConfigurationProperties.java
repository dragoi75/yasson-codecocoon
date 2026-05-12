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
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.serializer.JsonbDateTimeFormatter;
import org.eclipse.yasson.internal.serializer.NullSerializer;

/**
 * Resolved properties from JSONB config.
 */
public class JsonbConfigurationProperties {

    private final JsonbConfig serializationConfig;

    private final PropertyVisibilityStrategy visibilityPolicy;

    private final PropertyNamingStrategy namingPolicy;

    private final PropertyOrdering propertyOrder;

    private final JsonbDateTimeFormatter dateTimeFormatter;

    private final Locale defaultLocale;

    private final String binaryEncodingMethod;

    private final boolean allowNulls;

    private final boolean failOnUnknownFields;

    private final boolean iJsonStrictMode;

    private final boolean zeroTimeFallback;

    private final Map<Class<?>, Class<?>> customTypeMappings;

    private final Class<?> defaultMapImplementation;

    private final JsonbSerializer<Object> nullValueSerializer;
    
    private final Set<Class<?>> eagerInitializationClasses;

    /**
     * Creates new resolved JSONB config.
     *
     * @param serializationConfig jsonb config
     */
    public JsonbConfigurationProperties(JsonbConfig serializationConfig) {
        this.serializationConfig = serializationConfig;
        this.binaryEncodingMethod = initBinaryDataHandlingStrategy();
        this.namingPolicy = resolvePropertyNamingStrategy();
        this.visibilityPolicy = initVisibilityStrategy();
        this.propertyOrder = new PropertyOrdering(initPropertyOrderStrategy());
        this.defaultLocale = initLocaleFromConfig();
        this.dateTimeFormatter = createDateFormatter(this.defaultLocale);
        this.allowNulls = initNullableConfig();
        this.failOnUnknownFields = initFailOnUnknownPropertiesConfig();
        this.iJsonStrictMode = initStrictIJson();
        this.customTypeMappings = loadUserTypeMapping();
        this.zeroTimeFallback = configureZeroTimeDefaultingForJavaTime();
        this.defaultMapImplementation = determineDefaultMapImplementation();
        this.nullValueSerializer = initNullRootSerializer();
        this.eagerInitializationClasses = initEagerClasses();
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

    private boolean configureZeroTimeDefaultingForJavaTime() {
        return getBooleanConfigProperty(YassonConfig.ZERO_TIME_PARSE_DEFAULTING, false);
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, Class<?>> loadUserTypeMapping() {
        Optional<Object> optionalProperty = serializationConfig.getProperty(YassonConfig.USER_TYPE_MAPPING);
        if (!optionalProperty.isPresent()) {
            return Collections.emptyMap();
        }
        Object mappingResult = optionalProperty.get();
        if (!(mappingResult instanceof Map)) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                    YassonConfig.USER_TYPE_MAPPING,
                                                         Map.class.getSimpleName()));
        }
        return (Map<Class<?>, Class<?>>) mappingResult;
    }

    private JsonbDateTimeFormatter createDateFormatter(Locale defaultLocale) {
        final String datePattern = getGlobalConfigJsonbDateFormat();
        if (JsonbDateFormat.DEFAULT_FORMAT.equals(datePattern) || JsonbDateFormat.TIME_IN_MILLIS.equals(datePattern)) {
            return new JsonbDateTimeFormatter(datePattern, defaultLocale.toLanguageTag());
        }
        DateTimeFormatterBuilder formatterBuilder = new DateTimeFormatterBuilder();
        formatterBuilder.appendPattern(datePattern);
        if (isZeroTimeDefaulting()) {
            formatterBuilder.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            formatterBuilder.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            formatterBuilder.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        DateTimeFormatter dateFormatterInstance = formatterBuilder.toFormatter(defaultLocale);
        return new JsonbDateTimeFormatter(dateFormatterInstance, datePattern, defaultLocale.toLanguageTag());
    }

    private String getGlobalConfigJsonbDateFormat() {
        final Optional<Object> formatPropOpt = serializationConfig.getProperty(JsonbConfig.DATE_FORMAT);
        return formatPropOpt.map(formatterFunc -> {
            if (!(formatterFunc instanceof String)) {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                                                             JsonbConfig.DATE_FORMAT,
                                                             String.class.getSimpleName()));
            }
            return (String) formatterFunc;
        }).orElse(JsonbDateFormat.DEFAULT_FORMAT);
    }

    private Consumer<List<PropertyModel>> initPropertyOrderStrategy() {
        Optional<String> orderPolicyOpt = getPropertyOrderStrategy();

        return orderPolicyOpt.map(StrategiesProvider::getOrderingFunction)
                .orElseGet(() -> StrategiesProvider
                        .getOrderingFunction(PropertyOrderStrategy.LEXICOGRAPHICAL));  //default by spec
    }

    private Optional<String> getPropertyOrderStrategy() {
        final Optional<Object> optionalProperty = serializationConfig.getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY);
        if (optionalProperty.isPresent()) {
            final Object orderPolicyOpt = optionalProperty.get();
            if (!(orderPolicyOpt instanceof String)) {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.PROPERTY_ORDER, orderPolicyOpt));
            }
            switch ((String) orderPolicyOpt) {
            case PropertyOrderStrategy.LEXICOGRAPHICAL:
            case PropertyOrderStrategy.REVERSE:
            case PropertyOrderStrategy.ANY:
                return Optional.of((String) orderPolicyOpt);
            default:
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.PROPERTY_ORDER, orderPolicyOpt));
            }
        }
        return Optional.empty();
    }

    private PropertyNamingStrategy resolvePropertyNamingStrategy() {
        final Optional<Object> optionalProperty = serializationConfig.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY);
        if (!optionalProperty.isPresent()) {
            return StrategiesProvider.getPropertyNamingStrategy(PropertyNamingStrategy.IDENTITY);
        }
        Object namingPolicy = optionalProperty.get();
        if (namingPolicy instanceof String) {
            return StrategiesProvider.getPropertyNamingStrategy((String) namingPolicy);
        }
        if (!(namingPolicy instanceof PropertyNamingStrategy)) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.PROPERTY_NAMING_STRATEGY_INVALID));
        }
        return (PropertyNamingStrategy) optionalProperty.get();
    }

    private PropertyVisibilityStrategy initVisibilityStrategy() {
        final Optional<Object> optionalProperty = serializationConfig.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY);
        if (!optionalProperty.isPresent()) {
            return null;
        }
        final Object visibilityPolicy = optionalProperty.get();
        if (!(visibilityPolicy instanceof PropertyVisibilityStrategy)) {
            throw new JsonbException("JsonbConfig.PROPERTY_VISIBILITY_STRATEGY must be instance of " + PropertyVisibilityStrategy.class);
        }
        return (PropertyVisibilityStrategy) visibilityPolicy;
    }

    private String initBinaryDataHandlingStrategy() {
        final Optional<Boolean> iJsonOpt = serializationConfig.getProperty(JsonbConfig.STRICT_IJSON).map((value -> (Boolean) value));
        if (iJsonOpt.isPresent() && iJsonOpt.get()) {
            return BinaryDataStrategy.BASE_64_URL;
        }
        final Optional<String> orderPolicyOpt = serializationConfig.getProperty(JsonbConfig.BINARY_DATA_STRATEGY).map((value) -> (String) value);
        return orderPolicyOpt.orElse(BinaryDataStrategy.BYTE);
    }

    private boolean initNullableConfig() {
        return getBooleanConfigProperty(JsonbConfig.NULL_VALUES, false);
    }

    private boolean initFailOnUnknownPropertiesConfig() {
        return getBooleanConfigProperty(YassonConfig.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @SuppressWarnings("unchecked")
    private JsonbSerializer<Object> initNullRootSerializer() {
        Optional<Object> optionalProperty = serializationConfig.getProperty(YassonConfig.NULL_ROOT_SERIALIZER);
        if (!optionalProperty.isPresent()) {
            return new NullSerializer();
        }
        Object nullValueSerializer = optionalProperty.get();
        if (!(nullValueSerializer instanceof JsonbSerializer)) {
            throw new JsonbException("YassonConfig.NULL_ROOT_SERIALIZER must be instance of " + JsonbSerializer.class
                                             + "<Object>");
        }
        return (JsonbSerializer<Object>) nullValueSerializer;
    }
    
    private Set<Class<?>> initEagerClasses() {
        Optional<Object> optionalProperty = serializationConfig.getProperty(YassonConfig.EAGER_PARSE_CLASSES);
        if (!optionalProperty.isPresent()) {
            return Collections.emptySet();
        }
        Object eagerInitializationClasses = optionalProperty.get();
        if (!(eagerInitializationClasses instanceof Class<?>[])) {
            throw new JsonbException("YassonConfig.EAGER_PARSE_CLASSES must be instance of Class<?>[]");
        }
        return new HashSet<Class<?>>(Arrays.asList((Class<?>[]) eagerInitializationClasses));
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
        return failOnUnknownFields;
    }

    private boolean getBooleanConfigProperty(String configKey, boolean fallbackFlag) {
        final Optional<Object> optionalProperty = serializationConfig.getProperty(configKey);
        if (optionalProperty.isPresent()) {
            final Object mappingResult = optionalProperty.get();
            if (!(mappingResult instanceof Boolean)) {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
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
        return binaryEncodingMethod;
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
     * Gets locale from {@link JsonbConfig}.
     *
     * @return Configured locale.
     */
    private Locale initLocaleFromConfig() {
        final Optional<Object> maybeLanguageTag = serializationConfig.getProperty(JsonbConfig.LOCALE);
        return maybeLanguageTag.map(localeCandidate -> {
            if (!(localeCandidate instanceof Locale)) {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE,
                                                             JsonbConfig.LOCALE,
                                                             Locale.class.getSimpleName()));
            }
            return (Locale) localeCandidate;
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
    public JsonbDateTimeFormatter getConfigDateFormatter() {
        return dateTimeFormatter;
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
        return iJsonStrictMode;
    }

    /**
     * User type mapping for map interface to implementation classes.
     *
     * @return User type mapping.
     */
    public Map<Class<?>, Class<?>> getUserTypeMapping() {
        return customTypeMappings;
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
        return defaultMapImplementation;
    }

    public JsonbSerializer<Object> getNullSerializer() {
        return nullValueSerializer;
    }
    
    public Set<Class<?>> getEagerInitClasses() {
        return eagerInitializationClasses;
    }
}

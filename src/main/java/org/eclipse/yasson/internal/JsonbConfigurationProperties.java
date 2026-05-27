/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates. All rights reserved.
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

import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import org.eclipse.yasson.internal.model.BeanPropertyDescriptor;
import org.eclipse.yasson.internal.model.ReverseTreeMap;
import org.eclipse.yasson.internal.model.customization.PropertyOrdering;
import org.eclipse.yasson.internal.model.customization.PropertyNamingStrategyProvider;
import org.eclipse.yasson.internal.model.customization.VisibilityStrategiesProvider;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * Resolved properties from JSONB config.
 */
@SuppressWarnings("rawtypes")
public class JsonbConfigurationProperties {

    private static final Map<String, Class<? extends Map>> PROPERTY_ORDER_MAP_TYPES = Map.of(PropertyOrderStrategy.LEXICOGRAPHICAL, TreeMap.class, PropertyOrderStrategy.REVERSE, ReverseTreeMap.class, PropertyOrderStrategy.ANY, HashMap.class);

    private final JsonbConfig jsonbSettings;

    private final PropertyVisibilityStrategy visibilityStrategy;

    private final PropertyNamingStrategy namingStrategy;

    private final PropertyOrdering orderingPolicy;

    private final JsonbDateFormatter dateFormatHandler;

    private final Locale region;

    private final String binaryEncoding;

    private final boolean allowsNull;

    private final boolean failOnUnknownFields;

    private final boolean strictJson;

    private final boolean zeroTimeFallback;

    private final boolean requireCreatorParams;

    private final Map<Class<?>, Class<?>> customTypeMapping;

    private final Class<?> mapImplementationType;

    private final JsonbSerializer<Object> missingValueSerializer;

    private final Set<Class<?>> preloadClasses;

    private final boolean forceArraySerializerForNullKeys;

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
        return dateFormatHandler;
    }

    private boolean resolveZeroTimeDefaultingForJavaTime() {
        return getConfigProperty(YassonConfig.ZERO_TIME_PARSE_DEFAULTING, Boolean.class, false);
    }

    private Set<Class<?>> initEagerParseClasses() {
        Optional<Object> optValue = jsonbSettings.getProperty(YassonConfig.EAGER_PARSE_CLASSES);
        if (optValue.isEmpty()) {
            return Collections.emptySet();
        }
        Object preloadClasses = optValue.get();
        if (!(preloadClasses instanceof Class<?>[])) {
            throw new JsonbException("YassonConfig.EAGER_PARSE_CLASSES must be instance of Class<?>[]");
        }
        return new HashSet<>(Arrays.asList((Class<?>[]) preloadClasses));
    }

    private boolean initFailOnUnknownPropertiesConfig() {
        return getConfigProperty(YassonConfig.FAIL_ON_UNKNOWN_PROPERTIES, Boolean.class, false);
    }

    /**
     * Gets nullable from {@link JsonbConfig}.
     * If true null values are serialized to json.
     *
     * @return Configured nullable
     */
    public boolean getConfigNullable() {
        return allowsNull;
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

    private <T> T getConfigProperty(String configKey, Class<T> typeClass, T fallbackValue) {
        Objects.requireNonNull(fallbackValue, "Default value cannot be null");
        return jsonbSettings.getProperty(configKey).or(() -> Optional.of(fallbackValue)).filter(typeClass::isInstance).map(typeClass::cast).orElseThrow(() -> new JsonbException(MessageProvider.getMessage(MessageConstants.JSONB_CONFIG_PROPERTY_INVALID_TYPE, configKey, typeClass.getSimpleName())));
    }

    /**
     * User type mapping for map interface to implementation classes.
     *
     * @return User type mapping.
     */
    public Map<Class<?>, Class<?>> getUserTypeMapping() {
        return customTypeMapping;
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
     * Gets locale from {@link JsonbConfig}.
     *
     * @return Configured locale.
     */
    private Locale initLocaleFromConfig() {
        return getConfigProperty(JsonbConfig.LOCALE, Locale.class, Locale.getDefault());
    }

    private boolean initNullableConfig() {
        return getConfigProperty(JsonbConfig.NULL_VALUES, Boolean.class, false);
    }

    private String resolveBinaryDataStrategy() {
        if (getConfigProperty(JsonbConfig.STRICT_IJSON, Boolean.class, false)) {
            return BinaryDataStrategy.BASE_64_URL;
        }
        return getConfigProperty(JsonbConfig.BINARY_DATA_STRATEGY, String.class, BinaryDataStrategy.BYTE);
    }

    public Set<Class<?>> getEagerInitClasses() {
        return preloadClasses;
    }

    /**
     * Checks for binary data strategy to use.
     *
     * @return Binary data strategy.
     */
    public String getBinaryDataStrategy() {
        return binaryEncoding;
    }

    public JsonbSerializer<Object> getNullSerializer() {
        return missingValueSerializer;
    }

    private PropertyNamingStrategy resolvePropertyNamingStrategy() {
        final Optional<Object> optValue = jsonbSettings.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY);
        if (optValue.isEmpty()) {
            return PropertyNamingStrategyProvider.getPropertyNamingStrategy(PropertyNamingStrategy.IDENTITY);
        }
        Object namingStrategy = optValue.get();
        if (!(namingStrategy instanceof String)) {
            if (!(namingStrategy instanceof PropertyNamingStrategy)) {
                throw new JsonbException(MessageProvider.getMessage(MessageConstants.PROPERTY_NAMING_STRATEGY_INVALID));
            }
        } else {
            return PropertyNamingStrategyProvider.getPropertyNamingStrategy((String) namingStrategy);
        }
        return (PropertyNamingStrategy) optValue.get();
    }

    private String getPropertyOrderStrategy() {
        return getConfigProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY, String.class, PropertyOrderStrategy.LEXICOGRAPHICAL);
    }

    private String getGlobalConfigJsonbDateFormat() {
        return getConfigProperty(JsonbConfig.DATE_FORMAT, String.class, JsonbDateFormat.DEFAULT_FORMAT);
    }

    private Consumer<List<BeanPropertyDescriptor>> initOrderingStrategy() {
        return PropertyNamingStrategyProvider.getOrderingFunction(getPropertyOrderStrategy());
    }

    private Class<? extends Map> resolveDefaultMapImplType() {
        //We need to get PropertyOrderStrategy again. This time, if was not set, use ANY to get proper map implementation.
        //This is intentional!
        String orderKey = getConfigProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY, String.class, PropertyOrderStrategy.ANY);
        return PROPERTY_ORDER_MAP_TYPES.getOrDefault(orderKey, HashMap.class);
    }

    /**
     * Gets property ordering component.
     *
     * @return Component for ordering properties.
     */
    public PropertyOrdering getPropertyOrdering() {
        return orderingPolicy;
    }

    @SuppressWarnings("unchecked")
    private JsonbSerializer<Object> initNullRootSerializer() {
        return jsonbSettings.getProperty(YassonConfig.NULL_ROOT_SERIALIZER).map(obj -> {
            if (!(obj instanceof JsonbSerializer)) {
                throw new JsonbException("YassonConfig.NULL_ROOT_SERIALIZER must be instance of " + JsonbSerializer.class + "<Object>");
            }
            return (JsonbSerializer<Object>) obj;
        }).orElse(null);
    }

    public boolean isRequiredCreatorParametersPresent() {
        return requireCreatorParams;
    }

    /**
     * Creates new resolved JSONB config.
     *
     * @param jsonbSettings jsonb config
     */
    public JsonbConfigurationProperties(JsonbConfig jsonbSettings) {
        this.jsonbSettings = jsonbSettings;
        this.binaryEncoding = resolveBinaryDataStrategy();
        this.namingStrategy = resolvePropertyNamingStrategy();
        this.visibilityStrategy = resolvePropertyVisibilityStrategy();
        this.orderingPolicy = new PropertyOrdering(initOrderingStrategy());
        this.region = initLocaleFromConfig();
        this.dateFormatHandler = createDateFormatter(this.region);
        this.allowsNull = initNullableConfig();
        this.failOnUnknownFields = initFailOnUnknownPropertiesConfig();
        this.strictJson = initStrictIJson();
        this.customTypeMapping = loadUserTypeMapping();
        this.zeroTimeFallback = resolveZeroTimeDefaultingForJavaTime();
        this.mapImplementationType = resolveDefaultMapImplType();
        this.missingValueSerializer = initNullRootSerializer();
        this.preloadClasses = initEagerParseClasses();
        this.requireCreatorParams = resolveRequiredCreatorParameters();
        this.forceArraySerializerForNullKeys = resolveForceMapArraySerializerForNullKeys();
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, Class<?>> loadUserTypeMapping() {
        return getConfigProperty(YassonConfig.USER_TYPE_MAPPING, Map.class, Collections.emptyMap());
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
     * Whether the MapToEntriesArraySerializer is selected when a null key
     * is detected in a map.
     *
     * @return false or true
     */
    public boolean isForceMapArraySerializerForNullKeys() {
        return forceArraySerializerForNullKeys;
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
     * Default {@link java.util.Map} implementation to use, based on order strategy.
     *
     * @return map impl type
     */
    public Class<?> getDefaultMapImplType() {
        return mapImplementationType;
    }

    private JsonbDateFormatter createDateFormatter(Locale region) {
        final String formatPattern = getGlobalConfigJsonbDateFormat();
        if (JsonbDateFormat.DEFAULT_FORMAT.equals(formatPattern) || JsonbDateFormat.TIME_IN_MILLIS.equals(formatPattern)) {
            return new JsonbDateFormatter(formatPattern, region.toLanguageTag());
        }
        DateTimeFormatterBuilder formatComposer = new DateTimeFormatterBuilder().appendPattern(formatPattern);
        if (isZeroTimeDefaulting()) {
            formatComposer.parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0);
            formatComposer.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0);
            formatComposer.parseDefaulting(ChronoField.HOUR_OF_DAY, 0);
        }
        return new JsonbDateFormatter(formatComposer.toFormatter(region), formatPattern, region.toLanguageTag());
    }

    private PropertyVisibilityStrategy resolvePropertyVisibilityStrategy() {
        final Optional<Object> optValue = jsonbSettings.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY);
        if (optValue.isEmpty()) {
            return null;
        }
        final Object visibilityStrategy = optValue.get();
        if (!(visibilityStrategy instanceof String)) {
            if (!(visibilityStrategy instanceof PropertyVisibilityStrategy)) {
                throw new JsonbException("JsonbConfig.PROPERTY_VISIBILITY_STRATEGY must be instance of " + PropertyVisibilityStrategy.class);
            }
        } else {
            return VisibilityStrategiesProvider.getStrategy((String) visibilityStrategy);
        }
        return (PropertyVisibilityStrategy) visibilityStrategy;
    }

    private boolean initStrictIJson() {
        return getConfigProperty(JsonbConfig.STRICT_IJSON, Boolean.class, false);
    }

    /**
     * If strict IJSON patterns should be used.
     *
     * @return if IJSON is enabled
     */
    public boolean isStrictIJson() {
        return strictJson;
    }

    private boolean resolveForceMapArraySerializerForNullKeys() {
        return getConfigProperty(YassonConfig.FORCE_MAP_ARRAY_SERIALIZER_FOR_NULL_KEYS, Boolean.class, false);
    }

    private boolean resolveRequiredCreatorParameters() {
        if (null != System.getProperty(JsonbConfig.CREATOR_PARAMETERS_REQUIRED)) {
            return Boolean.parseBoolean(System.getProperty(YassonConfig.CREATOR_PARAMETERS_REQUIRED));
        }
        return getConfigProperty(YassonConfig.CREATOR_PARAMETERS_REQUIRED, Boolean.class, false);
    }

}

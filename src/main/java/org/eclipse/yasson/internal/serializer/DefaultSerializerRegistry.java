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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TimeZone;
import java.util.UUID;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import javax.xml.datatype.XMLGregorianCalendar;

/**
 * Cache of default serializers.
 */
public class DefaultSerializerRegistry {

    private static final Map<Class<?>, SerializerProviderWrapper> SERIALIZER_MAP = initializeSerializers();

    private static final SerializerProviderWrapper ENUM_TYPE_WRAPPER = new SerializerProviderWrapper(EnumTypeSerializer::new, EnumTypeDeserializer::new);

    private DefaultSerializerRegistry() {
    }

    private static Map<Class<?>, SerializerProviderWrapper> initializeSerializers() {
        final Map<Class<?>, SerializerProviderWrapper> serializerMap = new HashMap<>();

        serializerMap.put(Boolean.class, new SerializerProviderWrapper(BooleanTypeSerializer::new, BooleanTypeDeserializer::new));
        serializerMap.put(Boolean.TYPE, new SerializerProviderWrapper(BooleanTypeSerializer::new, BooleanTypeDeserializer::new));
        serializerMap.put(Byte.class, new SerializerProviderWrapper(ByteTypeSerializer::new, ByteTypeDeserializer::new));
        serializerMap.put(Byte.TYPE, new SerializerProviderWrapper(ByteTypeSerializer::new, ByteTypeDeserializer::new));
        serializerMap
                .put(Calendar.class, new SerializerProviderWrapper(CalendarTypeSerializer::new, CalendarTypeDeserializer::new));
        serializerMap.put(GregorianCalendar.class,
                        new SerializerProviderWrapper(CalendarTypeSerializer::new, CalendarTypeDeserializer::new));
        serializerMap.put(Character.class,
                        new SerializerProviderWrapper(CharacterTypeSerializer::new, CharacterTypeDeserializer::new));
        serializerMap
                .put(Character.TYPE, new SerializerProviderWrapper(CharacterTypeSerializer::new, CharacterTypeDeserializer::new));
        
        if (isClassAvailable("java.sql.Date")) {
            serializerMap.put(Date.class, new SerializerProviderWrapper(SqlDateTypeSerializer::new, DateTypeDeserializer::new));
            serializerMap.put(java.sql.Date.class,
                    new SerializerProviderWrapper(SqlDateTypeSerializer::new, SqlDateTypeDeserializer::new));
            serializerMap.put(java.sql.Timestamp.class,
                    new SerializerProviderWrapper(SqlTimestampTypeSerializer::new, SqlTimestampTypeDeserializer::new));
        } else {
            serializerMap.put(Date.class, new SerializerProviderWrapper(DateTypeSerializer::new, DateTypeDeserializer::new));
        }
        
        serializerMap.put(Double.class, new SerializerProviderWrapper(DoubleTypeSerializer::new, DoubleTypeDeserializer::new));
        serializerMap.put(Double.TYPE, new SerializerProviderWrapper(DoubleTypeSerializer::new, DoubleTypeDeserializer::new));
        serializerMap.put(Float.class, new SerializerProviderWrapper(FloatTypeSerializer::new, FloatTypeDeserializer::new));
        serializerMap.put(Float.TYPE, new SerializerProviderWrapper(FloatTypeSerializer::new, FloatTypeDeserializer::new));
        serializerMap.put(Instant.class, new SerializerProviderWrapper(InstantTypeSerializer::new, InstantTypeDeserializer::new));
        serializerMap.put(Integer.class, new SerializerProviderWrapper(IntegerTypeSerializer::new, IntegerTypeDeserializer::new));
        serializerMap.put(Integer.TYPE, new SerializerProviderWrapper(IntegerTypeSerializer::new, IntegerTypeDeserializer::new));
        serializerMap
                .put(JsonNumber.class, new SerializerProviderWrapper(JsonValueSerializer::new, JsonNumberTypeDeserializer::new));
        serializerMap
                .put(JsonString.class, new SerializerProviderWrapper(JsonValueSerializer::new, JsonStringTypeDeserializer::new));
        serializerMap.put(JsonValue.class, new SerializerProviderWrapper(JsonValueSerializer::new, JsonValueDeserializer::new));
        serializerMap.put(LocalDateTime.class,
                        new SerializerProviderWrapper(LocalDateTimeTypeSerializer::new, LocalDateTimeTypeDeserializer::new));
        serializerMap.put(LocalDate.class,
                        new SerializerProviderWrapper(LocalDateTypeSerializer::new, LocalDateTypeDeserializer::new));
        serializerMap.put(LocalTime.class,
                        new SerializerProviderWrapper(LocalTimeTypeSerializer::new, LocalTimeTypeDeserializer::new));
        serializerMap.put(Long.class, new SerializerProviderWrapper(LongTypeSerializer::new, LongTypeDeserializer::new));
        serializerMap.put(Long.TYPE, new SerializerProviderWrapper(LongTypeSerializer::new, LongTypeDeserializer::new));
        serializerMap.put(Number.class, new SerializerProviderWrapper(NumberTypeSerializer::new, NumberTypeDeserializer::new));
        serializerMap.put(OffsetDateTime.class,
                        new SerializerProviderWrapper(OffsetDateTimeTypeSerializer::new, OffsetDateTimeTypeDeserializer::new));
        serializerMap.put(OffsetTime.class,
                        new SerializerProviderWrapper(OffsetTimeTypeSerializer::new, OffsetTimeTypeDeserializer::new));
        serializerMap.put(OptionalDouble.class,
                        new SerializerProviderWrapper(OptionalDoubleTypeSerializer::new, OptionalDoubleTypeDeserializer::new));
        serializerMap.put(OptionalInt.class,
                        new SerializerProviderWrapper(OptionalIntTypeSerializer::new, OptionalIntTypeDeserializer::new));
        serializerMap.put(OptionalLong.class,
                        new SerializerProviderWrapper(OptionalLongTypeSerializer::new, OptionalLongTypeDeserializer::new));
        serializerMap.put(Path.class,
                        new SerializerProviderWrapper(PathTypeSerializer::new, PathTypeDeserializer::new));
        serializerMap.put(Short.class, new SerializerProviderWrapper(ShortTypeSerializer::new, ShortTypeDeserializer::new));
        serializerMap.put(Short.TYPE, new SerializerProviderWrapper(ShortTypeSerializer::new, ShortTypeDeserializer::new));
        serializerMap.put(String.class, new SerializerProviderWrapper(StringTypeSerializer::new, StringTypeDeserializer::new));
        serializerMap
                .put(TimeZone.class, new SerializerProviderWrapper(TimeZoneTypeSerializer::new, TimeZoneTypeDeserializer::new));
        serializerMap.put(URI.class, new SerializerProviderWrapper(URITypeSerializer::new, URITypeDeserializer::new));
        serializerMap.put(URL.class, new SerializerProviderWrapper(URLTypeSerializer::new, URLTypeDeserializer::new));
        serializerMap.put(UUID.class, new SerializerProviderWrapper(UUIDTypeSerializer::new, UUIDTypeDeserializer::new));
        serializerMap.put(ZonedDateTime.class,
                        new SerializerProviderWrapper(ZonedDateTimeTypeSerializer::new, ZonedDateTimeTypeDeserializer::new));
        serializerMap
                .put(Duration.class, new SerializerProviderWrapper(DurationTypeSerializer::new, DurationTypeDeserializer::new));
        serializerMap.put(Period.class, new SerializerProviderWrapper(PeriodTypeSerializer::new, PeriodTypeDeserializer::new));
        serializerMap.put(ZoneId.class, new SerializerProviderWrapper(ZoneIdTypeSerializer::new, ZoneIdTypeDeserializer::new));
        serializerMap.put(BigInteger.class,
                        new SerializerProviderWrapper(BigIntegerTypeSerializer::new, BigIntegerTypeDeserializer::new));
        serializerMap.put(BigDecimal.class,
                        new SerializerProviderWrapper(BigDecimalTypeSerializer::new, BigDecimalTypeDeserializer::new));
        serializerMap.put(ZoneOffset.class,
                        new SerializerProviderWrapper(ZoneOffsetTypeSerializer::new, ZoneOffsetTypeDeserializer::new));
        serializerMap.put(XMLGregorianCalendar.class,
                        new SerializerProviderWrapper(XMLGregorianCalendarTypeSerializer::new,
                                                      XMLGregorianCalendarTypeDeserializer::new));

        return serializerMap;
    }

    /**
     * Look for a provider for a supported value type. These serializers are basically singleton stateless shared instances.
     *
     * @param targetType supported type class
     * @param <T>   Type of serializer
     * @return serializer if found
     */
    public static <T> Optional<SerializerProviderWrapper> lookupValueSerializerProvider(Class<T> targetType) {
        Class<?> contenderType = targetType;
        do {
            final SerializerProviderWrapper serializerWrapper = SERIALIZER_MAP.get(contenderType);
            if (serializerWrapper != null) {
                return Optional.of(serializerWrapper);
            }
            contenderType = contenderType.getSuperclass();
        } while (contenderType != null);

        return findSerializerForType(targetType);
    }

    private static <T> Optional<SerializerProviderWrapper> findSerializerForType(Class<T> targetType) {
        if (Enum.class.isAssignableFrom(targetType)) {
            return Optional.of(ENUM_TYPE_WRAPPER);
        } else if (JsonString.class.isAssignableFrom(targetType)) {
            return Optional.of(SERIALIZER_MAP.get(JsonString.class));
        } else if (JsonNumber.class.isAssignableFrom(targetType)) {
            return Optional.of(SERIALIZER_MAP.get(JsonNumber.class));
        } else if (JsonValue.class.isAssignableFrom(targetType) && !(
                JsonObject.class.isAssignableFrom(targetType) || JsonArray.class.isAssignableFrom(targetType))) {
            return Optional.of(SERIALIZER_MAP.get(JsonValue.class));
        }
        return Optional.empty();
    }

    /**
     * Checks a class if it is supported by Yasson builtin serializers/deserializers in order to decide if it
     * should be introspected with reflection.
     *
     * @param targetType class to check
     * @return true if supported
     */
    public static boolean isKnownType(Class<?> targetType) {
        boolean containerValueKnown = Collection.class.isAssignableFrom(targetType)
                || Map.class.isAssignableFrom(targetType)
                || JsonValue.class.isAssignableFrom(targetType)
                || Optional.class.isAssignableFrom(targetType)
                || targetType.isArray();

        return containerValueKnown || lookupValueSerializerProvider(targetType).isPresent();
    }
    
    private static boolean isClassAvailable(String fullyQualifiedClassName) {
        try {
            Class.forName(fullyQualifiedClassName);
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }
}

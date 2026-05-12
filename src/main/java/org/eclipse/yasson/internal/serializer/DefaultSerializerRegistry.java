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
import java.util.Collections;
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

    private static final DefaultSerializerRegistry DEFAULT_SERIALIZER_REGISTRY = new DefaultSerializerRegistry();

    private final Map<Class<?>, SerializerProviderWrapper> providerMap;

    private final SerializerProviderWrapper enumerationProvider;

    private DefaultSerializerRegistry() {
        if (null != DEFAULT_SERIALIZER_REGISTRY) {
            throw new IllegalStateException("Only one instance of this class can be created!");
        }
        this.providerMap = initializeSerializers();
        enumerationProvider = new SerializerProviderWrapper(EnumTypeSerializer::new, EnumTypeDeserializer::new);
    }

    private Map<Class<?>, SerializerProviderWrapper> initializeSerializers() {
        final Map<Class<?>, SerializerProviderWrapper> providerMap = new HashMap<>();
        providerMap.put(Boolean.class, new SerializerProviderWrapper(BooleanTypeSerializer::new, BooleanTypeDeserializer::new));
        providerMap.put(Boolean.TYPE, new SerializerProviderWrapper(BooleanTypeSerializer::new, BooleanTypeDeserializer::new));
        providerMap.put(Byte.class, new SerializerProviderWrapper(ByteTypeSerializer::new, ByteTypeDeserializer::new));
        providerMap.put(Byte.TYPE, new SerializerProviderWrapper(ByteTypeSerializer::new, ByteTypeDeserializer::new));
        providerMap.put(Calendar.class, new SerializerProviderWrapper(CalendarTypeSerializer::new, CalendarTypeDeserializer::new));
        providerMap.put(GregorianCalendar.class, new SerializerProviderWrapper(CalendarTypeSerializer::new, CalendarTypeDeserializer::new));
        providerMap.put(Character.class, new SerializerProviderWrapper(CharacterTypeSerializer::new, CharacterTypeDeserializer::new));
        providerMap.put(Character.TYPE, new SerializerProviderWrapper(CharacterTypeSerializer::new, CharacterTypeDeserializer::new));
        providerMap.put(Date.class, new SerializerProviderWrapper(DateTypeSerializer::new, DateTypeDeserializer::new));
        providerMap.put(java.sql.Date.class, new SerializerProviderWrapper(DateTypeSerializer::new, SqlDateTypeDeserializer::new));
        providerMap.put(java.sql.Timestamp.class, new SerializerProviderWrapper(SqlTimestampTypeSerializer::new, SqlTimestampTypeDeserializer::new));
        providerMap.put(Double.class, new SerializerProviderWrapper(DoubleTypeSerializer::new, DoubleTypeDeserializer::new));
        providerMap.put(Double.TYPE, new SerializerProviderWrapper(DoubleTypeSerializer::new, DoubleTypeDeserializer::new));
        providerMap.put(Float.class, new SerializerProviderWrapper(FloatTypeSerializer::new, FloatTypeDeserializer::new));
        providerMap.put(Float.TYPE, new SerializerProviderWrapper(FloatTypeSerializer::new, FloatTypeDeserializer::new));
        providerMap.put(Instant.class, new SerializerProviderWrapper(InstantTypeSerializer::new, InstantTypeDeserializer::new));
        providerMap.put(Integer.class, new SerializerProviderWrapper(IntegerTypeSerializer::new, IntegerTypeDeserializer::new));
        providerMap.put(Integer.TYPE, new SerializerProviderWrapper(IntegerTypeSerializer::new, IntegerTypeDeserializer::new));
        providerMap.put(JsonNumber.class, new SerializerProviderWrapper(JsonValueSerializer::new, JsonNumberTypeDeserializer::new));
        providerMap.put(JsonString.class, new SerializerProviderWrapper(JsonValueSerializer::new, JsonStringTypeDeserializer::new));
        providerMap.put(JsonValue.class, new SerializerProviderWrapper(JsonValueSerializer::new, JsonValueDeserializer::new));
        providerMap.put(LocalDateTime.class, new SerializerProviderWrapper(LocalDateTimeTypeSerializer::new, LocalDateTimeTypeDeserializer::new));
        providerMap.put(LocalDate.class, new SerializerProviderWrapper(LocalDateTypeSerializer::new, LocalDateTypeDeserializer::new));
        providerMap.put(LocalTime.class, new SerializerProviderWrapper(LocalTimeTypeSerializer::new, LocalTimeTypeDeserializer::new));
        providerMap.put(Long.class, new SerializerProviderWrapper(LongTypeSerializer::new, LongTypeDeserializer::new));
        providerMap.put(Long.TYPE, new SerializerProviderWrapper(LongTypeSerializer::new, LongTypeDeserializer::new));
        providerMap.put(Number.class, new SerializerProviderWrapper(NumberTypeSerializer::new, NumberTypeDeserializer::new));
        providerMap.put(OffsetDateTime.class, new SerializerProviderWrapper(OffsetDateTimeTypeSerializer::new, OffsetDateTimeTypeDeserializer::new));
        providerMap.put(OffsetTime.class, new SerializerProviderWrapper(OffsetTimeTypeSerializer::new, OffsetTimeTypeDeserializer::new));
        providerMap.put(OptionalDouble.class, new SerializerProviderWrapper(OptionalDoubleTypeSerializer::new, OptionalDoubleTypeDeserializer::new));
        providerMap.put(OptionalInt.class, new SerializerProviderWrapper(OptionalIntTypeSerializer::new, OptionalIntTypeDeserializer::new));
        providerMap.put(OptionalLong.class, new SerializerProviderWrapper(OptionalLongTypeSerializer::new, OptionalLongTypeDeserializer::new));
        providerMap.put(Short.class, new SerializerProviderWrapper(ShortTypeSerializer::new, ShortTypeDeserializer::new));
        providerMap.put(Short.TYPE, new SerializerProviderWrapper(ShortTypeSerializer::new, ShortTypeDeserializer::new));
        providerMap.put(String.class, new SerializerProviderWrapper(StringTypeSerializer::new, StringTypeDeserializer::new));
        providerMap.put(TimeZone.class, new SerializerProviderWrapper(TimeZoneTypeSerializer::new, TimeZoneTypeDeserializer::new));
        providerMap.put(URI.class, new SerializerProviderWrapper(URITypeSerializer::new, URITypeDeserializer::new));
        providerMap.put(URL.class, new SerializerProviderWrapper(URLTypeSerializer::new, URLTypeDeserializer::new));
        providerMap.put(UUID.class, new SerializerProviderWrapper(UUIDTypeSerializer::new, UUIDTypeDeserializer::new));
        providerMap.put(ZonedDateTime.class, new SerializerProviderWrapper(ZonedDateTimeTypeSerializer::new, ZonedDateTimeTypeDeserializer::new));
        providerMap.put(Duration.class, new SerializerProviderWrapper(DurationTypeSerializer::new, DurationTypeDeserializer::new));
        providerMap.put(Period.class, new SerializerProviderWrapper(PeriodTypeSerializer::new, PeriodTypeDeserializer::new));
        providerMap.put(ZoneId.class, new SerializerProviderWrapper(ZoneIdTypeSerializer::new, ZoneIdTypeDeserializer::new));
        providerMap.put(BigInteger.class, new SerializerProviderWrapper(BigIntegerTypeSerializer::new, BigIntegerTypeDeserializer::new));
        providerMap.put(BigDecimal.class, new SerializerProviderWrapper(BigDecimalTypeSerializer::new, BigDecimalTypeDeserializer::new));
        providerMap.put(ZoneOffset.class, new SerializerProviderWrapper(ZoneOffsetTypeSerializer::new, ZoneOffsetTypeDeserializer::new));
        providerMap.put(XMLGregorianCalendar.class, new SerializerProviderWrapper(XMLGregorianCalendarTypeSerializer::new, XMLGregorianCalendarTypeDeserializer::new));
        return Collections.unmodifiableMap(providerMap);
    }

    /**
     * Look for a provider for a supported value type. These serializers are basically singleton stateless shared instances.
     *
     * @param requestedType supported type class
     * @param <T>   Type of serializer
     * @return serializer if found
     */
    public <T> Optional<SerializerProviderWrapper> findSerializerProvider(Class<T> requestedType) {
        Class<?> possibleType = requestedType;
        do {
            final SerializerProviderWrapper providerWrapper = providerMap.get(possibleType);
            if (null != providerWrapper) {
                return Optional.of(providerWrapper);
            }
            possibleType = possibleType.getSuperclass();
        } while (null != possibleType);
        return findSerializerByCondition(requestedType);
    }

    private <T> Optional<SerializerProviderWrapper> findSerializerByCondition(Class<T> requestedType) {
        if (!Enum.class.isAssignableFrom(requestedType)) {
            if (!JsonString.class.isAssignableFrom(requestedType)) {
                if (!JsonNumber.class.isAssignableFrom(requestedType)) {
                    if (JsonValue.class.isAssignableFrom(requestedType) && !(JsonObject.class.isAssignableFrom(requestedType) || JsonArray.class.isAssignableFrom(requestedType))) {
                        return Optional.of(providerMap.get(JsonValue.class));
                    }
                } else {
                    return Optional.of(providerMap.get(JsonNumber.class));
                }
            } else {
                return Optional.of(providerMap.get(JsonString.class));
            }
        } else {
            return Optional.of(enumerationProvider);
        }
        return Optional.empty();
    }

    /**
     * Checks a class if it is supported by Yasson builtin serializers/deserializers in order to decide if it
     * should be introspected with reflection.
     *
     * @param requestedType class to check
     * @return true if supported
     */
    public boolean isKnownType(Class<?> requestedType) {
        boolean isContainerValueKnown = Collection.class.isAssignableFrom(requestedType) || Map.class.isAssignableFrom(requestedType) || JsonValue.class.isAssignableFrom(requestedType) || Optional.class.isAssignableFrom(requestedType) || requestedType.isArray();
        return isContainerValueKnown || findSerializerProvider(requestedType).isPresent();
    }

    /**
     * Singleton instance.
     *
     * @return instance
     */
    public static DefaultSerializerRegistry getInstance() {
        return DEFAULT_SERIALIZER_REGISTRY;
    }
}

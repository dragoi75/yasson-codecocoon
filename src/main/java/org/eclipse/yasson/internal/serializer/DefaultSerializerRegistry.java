/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
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

package org.eclipse.yasson.internal.serializer;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.xml.datatype.XMLGregorianCalendar;
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
import java.util.*;

/**
 * @author Roman Grigoriadi
 */
public class DefaultSerializerRegistry {

    private static final DefaultSerializerRegistry DEFAULT_SERIALIZER_REGISTRY = new DefaultSerializerRegistry();

    private final Map<Class<?>, SerializerProviderWrapper> serializerProviderMap;

    private final SerializerProviderWrapper enumSerializerProvider;

    private DefaultSerializerRegistry() {
        this.serializerProviderMap = initializeSerializers();
        enumSerializerProvider = new SerializerProviderWrapper(EnumTypeSerializer::new, EnumTypeDeserializer::new);
    }

    private Map<Class<?>, SerializerProviderWrapper> initializeSerializers() {
        final Map<Class<?>, SerializerProviderWrapper> serializerProviderMap = new HashMap<>();

        serializerProviderMap.put(Boolean.class, new SerializerProviderWrapper(BooleanTypeSerializer::new, BooleanTypeDeserializer::new));
        serializerProviderMap.put(Boolean.TYPE, new SerializerProviderWrapper(BooleanTypeSerializer::new, BooleanTypeDeserializer::new));
        serializerProviderMap.put(Byte.class, new SerializerProviderWrapper(ByteTypeSerializer::new, ByteTypeDeserializer::new));
        serializerProviderMap.put(Byte.TYPE, new SerializerProviderWrapper(ByteTypeSerializer::new, ByteTypeDeserializer::new));
        serializerProviderMap.put(Calendar.class, new SerializerProviderWrapper(CalendarTypeSerializer::new, CalendarTypeDeserializer::new));
        serializerProviderMap.put(GregorianCalendar.class, new SerializerProviderWrapper(CalendarTypeSerializer::new, CalendarTypeDeserializer::new));
        serializerProviderMap.put(Character.class, new SerializerProviderWrapper(CharacterTypeSerializer::new, CharacterTypeDeserializer::new));
        serializerProviderMap.put(Character.TYPE, new SerializerProviderWrapper(CharacterTypeSerializer::new, CharacterTypeDeserializer::new));
        serializerProviderMap.put(Date.class, new SerializerProviderWrapper(DateTypeSerializer::new, DateTypeDeserializer::new));
        serializerProviderMap.put(java.sql.Date.class, new SerializerProviderWrapper(SqlDateTypeSerializer::new, SqlDateTypeDeserializer::new));
        serializerProviderMap.put(java.sql.Timestamp.class, new SerializerProviderWrapper(SqlTimestampTypeSerializer::new, SqlTimestampTypeDeserializer::new));
        serializerProviderMap.put(Double.class, new SerializerProviderWrapper(DoubleTypeSerializer::new, DoubleTypeDeserializer::new));
        serializerProviderMap.put(Double.TYPE, new SerializerProviderWrapper(DoubleTypeSerializer::new, DoubleTypeDeserializer::new));
        serializerProviderMap.put(Float.class, new SerializerProviderWrapper(FloatTypeSerializer::new, FloatTypeDeserializer::new));
        serializerProviderMap.put(Float.TYPE, new SerializerProviderWrapper(FloatTypeSerializer::new, FloatTypeDeserializer::new));
        serializerProviderMap.put(Instant.class, new SerializerProviderWrapper(InstantTypeSerializer::new, InstantTypeDeserializer::new));
        serializerProviderMap.put(Integer.class, new SerializerProviderWrapper(IntegerTypeSerializer::new, IntegerTypeDeserializer::new));
        serializerProviderMap.put(Integer.TYPE, new SerializerProviderWrapper(IntegerTypeSerializer::new, IntegerTypeDeserializer::new));
        serializerProviderMap.put(JsonNumber.class, new SerializerProviderWrapper(JsonValueSerializer::new, JsonNumberTypeDeserializer::new));
        serializerProviderMap.put(JsonString.class, new SerializerProviderWrapper(JsonValueSerializer::new, JsonStringTypeDeserializer::new));
        serializerProviderMap.put(JsonValue.class, new SerializerProviderWrapper(JsonValueSerializer::new, JsonValueDeserializer::new));
        serializerProviderMap.put(LocalDateTime.class, new SerializerProviderWrapper(LocalDateTimeTypeSerializer::new, LocalDateTimeTypeDeserializer::new));
        serializerProviderMap.put(LocalDate.class, new SerializerProviderWrapper(LocalDateTypeSerializer::new, LocalDateTypeDeserializer::new));
        serializerProviderMap.put(LocalTime.class, new SerializerProviderWrapper(LocalTimeTypeSerializer::new, LocalTimeTypeDeserializer::new));
        serializerProviderMap.put(Long.class, new SerializerProviderWrapper(LongTypeSerializer::new, LongTypeDeserializer::new));
        serializerProviderMap.put(Long.TYPE, new SerializerProviderWrapper(LongTypeSerializer::new, LongTypeDeserializer::new));
        serializerProviderMap.put(Number.class, new SerializerProviderWrapper(NumberTypeSerializer::new, NumberTypeDeserializer::new));
        serializerProviderMap.put(OffsetDateTime.class, new SerializerProviderWrapper(OffsetDateTimeTypeSerializer::new, OffsetDateTimeTypeDeserializer::new));
        serializerProviderMap.put(OffsetTime.class, new SerializerProviderWrapper(OffsetTimeTypeSerializer::new, OffsetTimeTypeDeserializer::new));
        serializerProviderMap.put(OptionalDouble.class, new SerializerProviderWrapper(OptionalDoubleTypeSerializer::new, OptionalDoubleTypeDeserializer::new));
        serializerProviderMap.put(OptionalInt.class, new SerializerProviderWrapper(OptionalIntTypeSerializer::new, OptionalIntTypeDeserializer::new));
        serializerProviderMap.put(OptionalLong.class, new SerializerProviderWrapper(OptionalLongTypeSerializer::new, OptionalLongTypeDeserializer::new));
        serializerProviderMap.put(Short.class, new SerializerProviderWrapper(ShortTypeSerializer::new, ShortTypeDeserializer::new));
        serializerProviderMap.put(Short.TYPE, new SerializerProviderWrapper(ShortTypeSerializer::new, ShortTypeDeserializer::new));
        serializerProviderMap.put(String.class, new SerializerProviderWrapper(StringTypeSerializer::new, StringTypeDeserializer::new));
        serializerProviderMap.put(TimeZone.class, new SerializerProviderWrapper(TimeZoneTypeSerializer::new, TimeZoneTypeDeserializer::new));
        serializerProviderMap.put(URI.class, new SerializerProviderWrapper(URITypeSerializer::new, URITypeDeserializer::new));
        serializerProviderMap.put(URL.class, new SerializerProviderWrapper(URLTypeSerializer::new, URLTypeDeserializer::new));
        serializerProviderMap.put(UUID.class, new SerializerProviderWrapper(UUIDTypeSerializer::new, UUIDTypeDeserializer::new));
        serializerProviderMap.put(ZonedDateTime.class, new SerializerProviderWrapper(ZonedDateTimeTypeSerializer::new, ZonedDateTimeTypeDeserializer::new));
        serializerProviderMap.put(Duration.class, new SerializerProviderWrapper(DurationTypeSerializer::new, DurationTypeDeserializer::new));
        serializerProviderMap.put(Period.class, new SerializerProviderWrapper(PeriodTypeSerializer::new, PeriodTypeDeserializer::new));
        serializerProviderMap.put(ZoneId.class, new SerializerProviderWrapper(ZoneIdTypeSerializer::new, ZoneIdTypeDeserializer::new));
        serializerProviderMap.put(BigInteger.class, new SerializerProviderWrapper(BigIntegerTypeSerializer::new, BigIntegerTypeDeserializer::new));
        serializerProviderMap.put(BigDecimal.class, new SerializerProviderWrapper(BigDecimalTypeSerializer::new, BigDecimalTypeDeserializer::new));
        serializerProviderMap.put(ZoneOffset.class, new SerializerProviderWrapper(ZoneOffsetTypeSerializer::new, ZoneOffsetTypeDeserializer::new));
        serializerProviderMap.put(XMLGregorianCalendar.class, new SerializerProviderWrapper(XMLGregorianCalendarTypeSerializer::new, XMLGregorianCalendarTypeDeserializer::new));

        return Collections.unmodifiableMap(serializerProviderMap);
    }

    /**
     * Look for a provider for a supported value type. These serializers are basically singleton stateless shared instances.
     *
     * @param targetType supported type class
     * @param <T> Type of serializer
     * @return serializer if found
     */
    public <T> Optional<SerializerProviderWrapper> getValueSerializerProvider(Class<T> targetType) {
        Class<?> potentialType = targetType;
        do {
            final SerializerProviderWrapper serializerWrapper = serializerProviderMap.get(potentialType);
            if (serializerWrapper != null) {
                return Optional.of(serializerWrapper);
            }
            potentialType = potentialType.getSuperclass();
        } while (potentialType != null);

        return findProviderByCondition(targetType);
    }

    private <T> Optional<SerializerProviderWrapper> findProviderByCondition(Class<T> targetType) {
        if (Enum.class.isAssignableFrom(targetType)) {
            return Optional.of(enumSerializerProvider);
        } else if (JsonString.class.isAssignableFrom(targetType)) {
            return Optional.of(serializerProviderMap.get(JsonString.class));
        } else if (JsonNumber.class.isAssignableFrom(targetType)) {
            return Optional.of(serializerProviderMap.get(JsonNumber.class));
        } else if (JsonValue.class.isAssignableFrom(targetType) && !(JsonObject.class.isAssignableFrom(targetType) || JsonArray.class.isAssignableFrom(targetType))) {
            return Optional.of(serializerProviderMap.get(JsonValue.class));
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
    public boolean isKnownType(Class<?> targetType) {
        boolean isContainerValueKnown = Collection.class.isAssignableFrom(targetType)
                || Map.class.isAssignableFrom(targetType)
                || JsonValue.class.isAssignableFrom(targetType)
                || Optional.class.isAssignableFrom(targetType)
                || targetType.isArray();

        return isContainerValueKnown || getValueSerializerProvider(targetType).isPresent();
    }


    /**
     * Singleton instance.
     * @return instance
     */
    public static DefaultSerializerRegistry getInstance() {
        return DEFAULT_SERIALIZER_REGISTRY;
    }
}

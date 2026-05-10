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

    private final Map<Class<?>, SerializationProviderAdapter> providerMap;

    private final SerializationProviderAdapter enumAdapter;

    private DefaultSerializerRegistry() {
        this.providerMap = initializeSerializers();
        enumAdapter = new SerializationProviderAdapter(EnumTypeSerializer::new, EnumTypeDeserializer::new);
    }

    private Map<Class<?>, SerializationProviderAdapter> initializeSerializers() {
        final Map<Class<?>, SerializationProviderAdapter> providerMap = new HashMap<>();

        providerMap.put(Boolean.class, new SerializationProviderAdapter(BooleanTypeSerializer::new, BooleanTypeDeserializer::new));
        providerMap.put(Boolean.TYPE, new SerializationProviderAdapter(BooleanTypeSerializer::new, BooleanTypeDeserializer::new));
        providerMap.put(Byte.class, new SerializationProviderAdapter(ByteTypeSerializer::new, ByteTypeDeserializer::new));
        providerMap.put(Byte.TYPE, new SerializationProviderAdapter(ByteTypeSerializer::new, ByteTypeDeserializer::new));
        providerMap.put(Calendar.class, new SerializationProviderAdapter(CalendarTypeSerializer::new, CalendarTypeDeserializer::new));
        providerMap.put(GregorianCalendar.class, new SerializationProviderAdapter(CalendarTypeSerializer::new, CalendarTypeDeserializer::new));
        providerMap.put(Character.class, new SerializationProviderAdapter(CharacterTypeSerializer::new, CharacterTypeDeserializer::new));
        providerMap.put(Character.TYPE, new SerializationProviderAdapter(CharacterTypeSerializer::new, CharacterTypeDeserializer::new));
        providerMap.put(Date.class, new SerializationProviderAdapter(DateTypeSerializer::new, DateTypeDeserializer::new));
        providerMap.put(java.sql.Date.class, new SerializationProviderAdapter(SqlDateTypeSerializer::new, SqlDateTypeDeserializer::new));
        providerMap.put(java.sql.Timestamp.class, new SerializationProviderAdapter(SqlTimestampTypeSerializer::new, SqlTimestampTypeDeserializer::new));
        providerMap.put(Double.class, new SerializationProviderAdapter(DoubleTypeSerializer::new, DoubleTypeDeserializer::new));
        providerMap.put(Double.TYPE, new SerializationProviderAdapter(DoubleTypeSerializer::new, DoubleTypeDeserializer::new));
        providerMap.put(Float.class, new SerializationProviderAdapter(FloatTypeSerializer::new, FloatTypeDeserializer::new));
        providerMap.put(Float.TYPE, new SerializationProviderAdapter(FloatTypeSerializer::new, FloatTypeDeserializer::new));
        providerMap.put(Instant.class, new SerializationProviderAdapter(InstantTypeSerializer::new, InstantTypeDeserializer::new));
        providerMap.put(Integer.class, new SerializationProviderAdapter(IntegerTypeSerializer::new, IntegerTypeDeserializer::new));
        providerMap.put(Integer.TYPE, new SerializationProviderAdapter(IntegerTypeSerializer::new, IntegerTypeDeserializer::new));
        providerMap.put(JsonNumber.class, new SerializationProviderAdapter(JsonValueSerializer::new, JsonNumberTypeDeserializer::new));
        providerMap.put(JsonString.class, new SerializationProviderAdapter(JsonValueSerializer::new, JsonStringTypeDeserializer::new));
        providerMap.put(JsonValue.class, new SerializationProviderAdapter(JsonValueSerializer::new, JsonValueDeserializer::new));
        providerMap.put(LocalDateTime.class, new SerializationProviderAdapter(LocalDateTimeTypeSerializer::new, LocalDateTimeTypeDeserializer::new));
        providerMap.put(LocalDate.class, new SerializationProviderAdapter(LocalDateTypeSerializer::new, LocalDateTypeDeserializer::new));
        providerMap.put(LocalTime.class, new SerializationProviderAdapter(LocalTimeTypeSerializer::new, LocalTimeTypeDeserializer::new));
        providerMap.put(Long.class, new SerializationProviderAdapter(LongTypeSerializer::new, LongTypeDeserializer::new));
        providerMap.put(Long.TYPE, new SerializationProviderAdapter(LongTypeSerializer::new, LongTypeDeserializer::new));
        providerMap.put(Number.class, new SerializationProviderAdapter(NumberTypeSerializer::new, NumberTypeDeserializer::new));
        providerMap.put(OffsetDateTime.class, new SerializationProviderAdapter(OffsetDateTimeTypeSerializer::new, OffsetDateTimeTypeDeserializer::new));
        providerMap.put(OffsetTime.class, new SerializationProviderAdapter(OffsetTimeTypeSerializer::new, OffsetTimeTypeDeserializer::new));
        providerMap.put(OptionalDouble.class, new SerializationProviderAdapter(OptionalDoubleSerializer::new, OptionalDoubleDeserializer::new));
        providerMap.put(OptionalInt.class, new SerializationProviderAdapter(OptionalIntSerializer::new, OptionalIntTypeDeserializer::new));
        providerMap.put(OptionalLong.class, new SerializationProviderAdapter(OptionalLongSerializer::new, OptionalLongTypeDeserializer::new));
        providerMap.put(Short.class, new SerializationProviderAdapter(ShortTypeSerializer::new, ShortTypeDeserializer::new));
        providerMap.put(Short.TYPE, new SerializationProviderAdapter(ShortTypeSerializer::new, ShortTypeDeserializer::new));
        providerMap.put(String.class, new SerializationProviderAdapter(StringTypeSerializer::new, StringTypeDeserializer::new));
        providerMap.put(TimeZone.class, new SerializationProviderAdapter(TimeZoneTypeSerializer::new, TimeZoneTypeDeserializer::new));
        providerMap.put(URI.class, new SerializationProviderAdapter(URITypeSerializer::new, URITypeDeserializer::new));
        providerMap.put(URL.class, new SerializationProviderAdapter(URLTypeSerializer::new, URLTypeDeserializer::new));
        providerMap.put(UUID.class, new SerializationProviderAdapter(UUIDTypeSerializer::new, UUIDTypeDeserializer::new));
        providerMap.put(ZonedDateTime.class, new SerializationProviderAdapter(ZonedDateTimeTypeSerializer::new, ZonedDateTimeTypeDeserializer::new));
        providerMap.put(Duration.class, new SerializationProviderAdapter(DurationTypeSerializer::new, DurationTypeDeserializer::new));
        providerMap.put(Period.class, new SerializationProviderAdapter(PeriodTypeSerializer::new, PeriodTypeDeserializer::new));
        providerMap.put(ZoneId.class, new SerializationProviderAdapter(ZoneIdTypeSerializer::new, ZoneIdTypeDeserializer::new));
        providerMap.put(BigInteger.class, new SerializationProviderAdapter(BigIntegerTypeSerializer::new, BigIntegerTypeDeserializer::new));
        providerMap.put(BigDecimal.class, new SerializationProviderAdapter(BigDecimalTypeSerializer::new, BigDecimalTypeDeserializer::new));
        providerMap.put(ZoneOffset.class, new SerializationProviderAdapter(ZoneOffsetTypeSerializer::new, ZoneOffsetTypeDeserializer::new));
        providerMap.put(XMLGregorianCalendar.class, new SerializationProviderAdapter(XMLGregorianCalendarTypeSerializer::new, XMLGregorianCalendarTypeDeserializer::new));

        return Collections.unmodifiableMap(providerMap);
    }

    /**
     * Look for a provider for a supported value type. These serializers are basically singleton stateless shared instances.
     *
     * @param targetType supported type class
     * @param <T> Type of serializer
     * @return serializer if found
     */
    public <T> Optional<SerializationProviderAdapter> findSerializerProvider(Class<T> targetType) {
        Class<?> matchType = targetType;
        do {
            final SerializationProviderAdapter adapter = providerMap.get(matchType);
            if (adapter != null) {
                return Optional.of(adapter);
            }
            matchType = matchType.getSuperclass();
        } while (matchType != null);

        return findProviderByCondition(targetType);
    }

    private <T> Optional<SerializationProviderAdapter> findProviderByCondition(Class<T> targetType) {
        if (Enum.class.isAssignableFrom(targetType)) {
            return Optional.of(enumAdapter);
        } else if (JsonString.class.isAssignableFrom(targetType)) {
            return Optional.of(providerMap.get(JsonString.class));
        } else if (JsonNumber.class.isAssignableFrom(targetType)) {
            return Optional.of(providerMap.get(JsonNumber.class));
        } else if (JsonValue.class.isAssignableFrom(targetType) && !(JsonObject.class.isAssignableFrom(targetType) || JsonArray.class.isAssignableFrom(targetType))) {
            return Optional.of(providerMap.get(JsonValue.class));
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

        return isContainerValueKnown || findSerializerProvider(targetType).isPresent();
    }


    /**
     * Singleton instance.
     * @return instance
     */
    public static DefaultSerializerRegistry getInstance() {
        return DEFAULT_SERIALIZER_REGISTRY;
    }
}

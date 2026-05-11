/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
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
public class DefaultSerializerProvider {

    private static final DefaultSerializerProvider DEFAULT_SERIALIZER_PROVIDER = new DefaultSerializerProvider();

    private final Map<Class<?>, SerializerProviderWrapper> providerMap;

    private final SerializerProviderWrapper enumerationWrapper;

    private DefaultSerializerProvider() {
        this.providerMap = initializeSerializers();
        enumerationWrapper = new SerializerProviderWrapper(EnumTypeSerializer::new, EnumTypeDeserializer::new);
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
        providerMap.put(java.sql.Date.class, new SerializerProviderWrapper(SqlDateTypeSerializer::new, SqlDateTypeDeserializer::new));
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
     * @param targetType supported type class
     * @param <T> Type of serializer
     * @return serializer if found
     */
    public <T> Optional<SerializerProviderWrapper> locateValueSerializerProvider(Class<T> targetType) {
        Class<?> potentialMatchClass = targetType;
        do {
            final SerializerProviderWrapper serializerWrapper = providerMap.get(potentialMatchClass);
            if (null != serializerWrapper) {
                return Optional.of(serializerWrapper);
            }
            potentialMatchClass = potentialMatchClass.getSuperclass();
        } while (null != potentialMatchClass);
        return findProviderByCondition(targetType);
    }

    private <T> Optional<SerializerProviderWrapper> findProviderByCondition(Class<T> targetType) {
        if (!Enum.class.isAssignableFrom(targetType)) {
            if (!JsonString.class.isAssignableFrom(targetType)) {
                if (!JsonNumber.class.isAssignableFrom(targetType)) {
                    if (JsonValue.class.isAssignableFrom(targetType) && !(JsonObject.class.isAssignableFrom(targetType) || JsonArray.class.isAssignableFrom(targetType))) {
                        return Optional.of(providerMap.get(JsonValue.class));
                    }
                } else {
                    return Optional.of(providerMap.get(JsonNumber.class));
                }
            } else {
                return Optional.of(providerMap.get(JsonString.class));
            }
        } else {
            return Optional.of(enumerationWrapper);
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
        boolean isContainerValueKnown = Collection.class.isAssignableFrom(targetType) || Map.class.isAssignableFrom(targetType) || JsonValue.class.isAssignableFrom(targetType) || Optional.class.isAssignableFrom(targetType) || targetType.isArray();
        return isContainerValueKnown || locateValueSerializerProvider(targetType).isPresent();
    }

    /**
     * Singleton instance.
     * @return instance
     */
    public static DefaultSerializerProvider getInstance() {
        return DEFAULT_SERIALIZER_PROVIDER;
    }
}

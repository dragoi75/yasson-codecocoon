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
public class DefaultSerializerRegistry {

    private static final DefaultSerializerRegistry DEFAULT_SERIALIZER_REGISTRY = new DefaultSerializerRegistry();

    private final Map<Class<?>, SerializerProviderAdapter> providerRegistry;

    private final SerializerProviderAdapter enumSerializerAdapter;

    /**
     * Checks a class if it is supported by Yasson builtin serializers/deserializers in order to decide if it
     * should be introspected with reflection.
     *
     * @param targetType class to check
     * @return true if supported
     */
    public boolean isKnownType(Class<?> targetType) {
        boolean containerValueKnown = Collection.class.isAssignableFrom(targetType) || Map.class.isAssignableFrom(targetType) || JsonValue.class.isAssignableFrom(targetType) || Optional.class.isAssignableFrom(targetType) || targetType.isArray();
        return containerValueKnown || lookupValueSerializerProvider(targetType).isPresent();
    }

    private <T> Optional<SerializerProviderAdapter> findSerializerByCondition(Class<T> targetType) {
        if (!Enum.class.isAssignableFrom(targetType)) {
            if (!JsonString.class.isAssignableFrom(targetType)) {
                if (!JsonNumber.class.isAssignableFrom(targetType)) {
                    if (JsonValue.class.isAssignableFrom(targetType) && !(JsonObject.class.isAssignableFrom(targetType) || JsonArray.class.isAssignableFrom(targetType))) {
                        return Optional.of(providerRegistry.get(JsonValue.class));
                    }
                } else {
                    return Optional.of(providerRegistry.get(JsonNumber.class));
                }
            } else {
                return Optional.of(providerRegistry.get(JsonString.class));
            }
        } else {
            return Optional.of(enumSerializerAdapter);
        }
        return Optional.empty();
    }

    /**
     * Look for a provider for a supported value type. These serializers are basically singleton stateless shared instances.
     *
     * @param targetType supported type class
     * @param <T> Type of serializer
     * @return serializer if found
     */
    public <T> Optional<SerializerProviderAdapter> lookupValueSerializerProvider(Class<T> targetType) {
        Class<?> potentialClass = targetType;
        do {
            final SerializerProviderAdapter serializerAdapter = providerRegistry.get(potentialClass);
            if (null != serializerAdapter) {
                return Optional.of(serializerAdapter);
            }
            potentialClass = potentialClass.getSuperclass();
        } while (null != potentialClass);
        return findSerializerByCondition(targetType);
    }

    private Map<Class<?>, SerializerProviderAdapter> initializeSerializers() {
        final Map<Class<?>, SerializerProviderAdapter> providerRegistry = new HashMap<>();
        providerRegistry.put(Boolean.class, new SerializerProviderAdapter(BooleanTypeSerializer::new, BooleanTypeDeserializer::new));
        providerRegistry.put(Boolean.TYPE, new SerializerProviderAdapter(BooleanTypeSerializer::new, BooleanTypeDeserializer::new));
        providerRegistry.put(Byte.class, new SerializerProviderAdapter(ByteTypeSerializer::new, ByteTypeDeserializer::new));
        providerRegistry.put(Byte.TYPE, new SerializerProviderAdapter(ByteTypeSerializer::new, ByteTypeDeserializer::new));
        providerRegistry.put(Calendar.class, new SerializerProviderAdapter(CalendarTypeSerializer::new, CalendarTypeDeserializer::new));
        providerRegistry.put(GregorianCalendar.class, new SerializerProviderAdapter(CalendarTypeSerializer::new, CalendarTypeDeserializer::new));
        providerRegistry.put(Character.class, new SerializerProviderAdapter(CharacterTypeSerializer::new, CharacterTypeDeserializer::new));
        providerRegistry.put(Character.TYPE, new SerializerProviderAdapter(CharacterTypeSerializer::new, CharacterTypeDeserializer::new));
        providerRegistry.put(Date.class, new SerializerProviderAdapter(DateTypeSerializer::new, DateTypeDeserializer::new));
        providerRegistry.put(java.sql.Date.class, new SerializerProviderAdapter(SqlDateTypeSerializer::new, SqlDateTypeDeserializer::new));
        providerRegistry.put(java.sql.Timestamp.class, new SerializerProviderAdapter(SqlTimestampTypeSerializer::new, SqlTimestampTypeDeserializer::new));
        providerRegistry.put(Double.class, new SerializerProviderAdapter(DoubleTypeSerializer::new, DoubleTypeDeserializer::new));
        providerRegistry.put(Double.TYPE, new SerializerProviderAdapter(DoubleTypeSerializer::new, DoubleTypeDeserializer::new));
        providerRegistry.put(Float.class, new SerializerProviderAdapter(FloatTypeSerializer::new, FloatTypeDeserializer::new));
        providerRegistry.put(Float.TYPE, new SerializerProviderAdapter(FloatTypeSerializer::new, FloatTypeDeserializer::new));
        providerRegistry.put(Instant.class, new SerializerProviderAdapter(InstantTypeSerializer::new, InstantTypeDeserializer::new));
        providerRegistry.put(Integer.class, new SerializerProviderAdapter(IntegerTypeSerializer::new, IntegerTypeDeserializer::new));
        providerRegistry.put(Integer.TYPE, new SerializerProviderAdapter(IntegerTypeSerializer::new, IntegerTypeDeserializer::new));
        providerRegistry.put(JsonNumber.class, new SerializerProviderAdapter(JsonValueSerializer::new, JsonNumberTypeDeserializer::new));
        providerRegistry.put(JsonString.class, new SerializerProviderAdapter(JsonValueSerializer::new, JsonStringTypeDeserializer::new));
        providerRegistry.put(JsonValue.class, new SerializerProviderAdapter(JsonValueSerializer::new, JsonValueDeserializer::new));
        providerRegistry.put(LocalDateTime.class, new SerializerProviderAdapter(LocalDateTimeTypeSerializer::new, LocalDateTimeTypeDeserializer::new));
        providerRegistry.put(LocalDate.class, new SerializerProviderAdapter(LocalDateTypeSerializer::new, LocalDateTypeDeserializer::new));
        providerRegistry.put(LocalTime.class, new SerializerProviderAdapter(LocalTimeTypeSerializer::new, LocalTimeTypeDeserializer::new));
        providerRegistry.put(Long.class, new SerializerProviderAdapter(LongTypeSerializer::new, LongTypeDeserializer::new));
        providerRegistry.put(Long.TYPE, new SerializerProviderAdapter(LongTypeSerializer::new, LongTypeDeserializer::new));
        providerRegistry.put(Number.class, new SerializerProviderAdapter(NumberTypeSerializer::new, NumberTypeDeserializer::new));
        providerRegistry.put(OffsetDateTime.class, new SerializerProviderAdapter(OffsetDateTimeTypeSerializer::new, OffsetDateTimeTypeDeserializer::new));
        providerRegistry.put(OffsetTime.class, new SerializerProviderAdapter(OffsetTimeTypeSerializer::new, OffsetTimeTypeDeserializer::new));
        providerRegistry.put(OptionalDouble.class, new SerializerProviderAdapter(OptionalDoubleTypeSerializer::new, OptionalDoubleTypeDeserializer::new));
        providerRegistry.put(OptionalInt.class, new SerializerProviderAdapter(OptionalIntTypeSerializer::new, OptionalIntTypeDeserializer::new));
        providerRegistry.put(OptionalLong.class, new SerializerProviderAdapter(OptionalLongTypeSerializer::new, OptionalLongTypeDeserializer::new));
        providerRegistry.put(Short.class, new SerializerProviderAdapter(ShortTypeSerializer::new, ShortTypeDeserializer::new));
        providerRegistry.put(Short.TYPE, new SerializerProviderAdapter(ShortTypeSerializer::new, ShortTypeDeserializer::new));
        providerRegistry.put(String.class, new SerializerProviderAdapter(StringTypeSerializer::new, StringTypeDeserializer::new));
        providerRegistry.put(TimeZone.class, new SerializerProviderAdapter(TimeZoneTypeSerializer::new, TimeZoneTypeDeserializer::new));
        providerRegistry.put(URI.class, new SerializerProviderAdapter(URITypeSerializer::new, URITypeDeserializer::new));
        providerRegistry.put(URL.class, new SerializerProviderAdapter(URLTypeSerializer::new, URLTypeDeserializer::new));
        providerRegistry.put(UUID.class, new SerializerProviderAdapter(UUIDTypeSerializer::new, UUIDTypeDeserializer::new));
        providerRegistry.put(ZonedDateTime.class, new SerializerProviderAdapter(ZonedDateTimeTypeSerializer::new, ZonedDateTimeTypeDeserializer::new));
        providerRegistry.put(Duration.class, new SerializerProviderAdapter(DurationTypeSerializer::new, DurationTypeDeserializer::new));
        providerRegistry.put(Period.class, new SerializerProviderAdapter(PeriodTypeSerializer::new, PeriodTypeDeserializer::new));
        providerRegistry.put(ZoneId.class, new SerializerProviderAdapter(ZoneIdTypeSerializer::new, ZoneIdTypeDeserializer::new));
        providerRegistry.put(BigInteger.class, new SerializerProviderAdapter(BigIntegerTypeSerializer::new, BigIntegerTypeDeserializer::new));
        providerRegistry.put(BigDecimal.class, new SerializerProviderAdapter(BigDecimalTypeSerializer::new, BigDecimalTypeDeserializer::new));
        providerRegistry.put(ZoneOffset.class, new SerializerProviderAdapter(ZoneOffsetTypeSerializer::new, ZoneOffsetTypeDeserializer::new));
        providerRegistry.put(XMLGregorianCalendar.class, new SerializerProviderAdapter(XMLGregorianCalendarTypeSerializer::new, XMLGregorianCalendarTypeDeserializer::new));
        return Collections.unmodifiableMap(providerRegistry);
    }

    /**
     * Singleton instance.
     * @return instance
     */
    public static DefaultSerializerRegistry getInstance() {
        return DEFAULT_SERIALIZER_REGISTRY;
    }

    private DefaultSerializerRegistry() {
        this.providerRegistry = initializeSerializers();
        enumSerializerAdapter = new SerializerProviderAdapter(EnumTypeSerializer::new, EnumTypeDeserializer::new);
    }

}

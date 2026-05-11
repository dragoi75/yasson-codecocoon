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
public class StandardSerializerRegistry {

    private static final Map<Class<?>, SerializerProviderAdapter> SERIALIZER_PROVIDERS = initializeSerializers();

    private static final SerializerProviderAdapter ENUM_SERIALIZER_ADAPTER = new SerializerProviderAdapter(EnumValueSerializer::new, EnumValueDeserializer::new);

    private StandardSerializerRegistry() {
    }

    private static Map<Class<?>, SerializerProviderAdapter> initializeSerializers() {
        final Map<Class<?>, SerializerProviderAdapter> serializerMap = new HashMap<>();
        serializerMap.put(Boolean.class, new SerializerProviderAdapter(BooleanValueSerializer::new, BooleanValueDeserializer::new));
        serializerMap.put(Boolean.TYPE, new SerializerProviderAdapter(BooleanValueSerializer::new, BooleanValueDeserializer::new));
        serializerMap.put(Byte.class, new SerializerProviderAdapter(ByteSerializer::new, ByteValueDeserializer::new));
        serializerMap.put(Byte.TYPE, new SerializerProviderAdapter(ByteSerializer::new, ByteValueDeserializer::new));
        serializerMap.put(Calendar.class, new SerializerProviderAdapter(CalendarTypeSerializer::new, CalendarTypeParser::new));
        serializerMap.put(GregorianCalendar.class, new SerializerProviderAdapter(CalendarTypeSerializer::new, CalendarTypeParser::new));
        serializerMap.put(Character.class, new SerializerProviderAdapter(CharacterSerializer::new, CharacterDeserializer::new));
        serializerMap.put(Character.TYPE, new SerializerProviderAdapter(CharacterSerializer::new, CharacterDeserializer::new));
        if (!isClassAvailable("java.sql.Date")) {
            serializerMap.put(Date.class, new SerializerProviderAdapter(DateSerializer::new, DateTimeTypeDeserializer::new));
        } else {
            serializerMap.put(Date.class, new SerializerProviderAdapter(SqlDateSerializer::new, DateTimeTypeDeserializer::new));
            serializerMap.put(java.sql.Date.class, new SerializerProviderAdapter(SqlDateSerializer::new, SqlDateDeserializer::new));
            serializerMap.put(java.sql.Timestamp.class, new SerializerProviderAdapter(SqlTimestampSerializer::new, SqlTimestampTypeParser::new));
        }
        serializerMap.put(Double.class, new SerializerProviderAdapter(DoubleValueSerializer::new, DoubleValueDeserializer::new));
        serializerMap.put(Double.TYPE, new SerializerProviderAdapter(DoubleValueSerializer::new, DoubleValueDeserializer::new));
        serializerMap.put(Float.class, new SerializerProviderAdapter(FloatValueSerializer::new, FloatValueDeserializer::new));
        serializerMap.put(Float.TYPE, new SerializerProviderAdapter(FloatValueSerializer::new, FloatValueDeserializer::new));
        serializerMap.put(Instant.class, new SerializerProviderAdapter(InstantSerializer::new, InstantValueDeserializer::new));
        serializerMap.put(Integer.class, new SerializerProviderAdapter(IntegerValueSerializer::new, IntegerValueDeserializer::new));
        serializerMap.put(Integer.TYPE, new SerializerProviderAdapter(IntegerValueSerializer::new, IntegerValueDeserializer::new));
        serializerMap.put(JsonNumber.class, new SerializerProviderAdapter(JsonValueEncoder::new, JsonNumericTypeDeserializer::new));
        serializerMap.put(JsonString.class, new SerializerProviderAdapter(JsonValueEncoder::new, JsonStringToTypeDeserializer::new));
        serializerMap.put(JsonValue.class, new SerializerProviderAdapter(JsonValueEncoder::new, JsonValueDecoder::new));
        serializerMap.put(LocalDateTime.class, new SerializerProviderAdapter(LocalDateTimeJsonSerializer::new, LocalDateTimeDeserializer::new));
        serializerMap.put(LocalDate.class, new SerializerProviderAdapter(LocalDateSerializer::new, LocalDateDeserializer::new));
        serializerMap.put(LocalTime.class, new SerializerProviderAdapter(LocalTimeInstantSerializer::new, LocalTimeValueDeserializer::new));
        serializerMap.put(Long.class, new SerializerProviderAdapter(LongValueSerializer::new, LongValueDeserializer::new));
        serializerMap.put(Long.TYPE, new SerializerProviderAdapter(LongValueSerializer::new, LongValueDeserializer::new));
        serializerMap.put(Number.class, new SerializerProviderAdapter(NumericTypeSerializer::new, NumberTypeDeserializerImpl::new));
        serializerMap.put(OffsetDateTime.class, new SerializerProviderAdapter(OffsetDateTimeSerializer::new, OffsetDateTimeDeserializer::new));
        serializerMap.put(OffsetTime.class, new SerializerProviderAdapter(OffsetTimeSerializer::new, OffsetTimeDeserializer::new));
        serializerMap.put(OptionalDouble.class, new SerializerProviderAdapter(OptionalDoubleSerializer::new, OptionalDoubleDeserializer::new));
        serializerMap.put(OptionalInt.class, new SerializerProviderAdapter(OptionalIntSerializer::new, OptionalIntValueDeserializer::new));
        serializerMap.put(OptionalLong.class, new SerializerProviderAdapter(OptionalLongSerializer::new, OptionalLongValueDeserializer::new));
        serializerMap.put(Path.class, new SerializerProviderAdapter(PathSerializer::new, PathTypeConverter::new));
        serializerMap.put(Short.class, new SerializerProviderAdapter(ShortSerializer::new, ShortDeserializer::new));
        serializerMap.put(Short.TYPE, new SerializerProviderAdapter(ShortSerializer::new, ShortDeserializer::new));
        serializerMap.put(String.class, new SerializerProviderAdapter(StringTypeJsonSerializer::new, StringToTypeDeserializer::new));
        serializerMap.put(TimeZone.class, new SerializerProviderAdapter(TimeZoneSerializer::new, TimeZoneTypeConverter::new));
        serializerMap.put(URI.class, new SerializerProviderAdapter(URIValueSerializer::new, URITypeConverter::new));
        serializerMap.put(URL.class, new SerializerProviderAdapter(URLSerializer::new, UrlTypeDeserializerImpl::new));
        serializerMap.put(UUID.class, new SerializerProviderAdapter(UUIDSerializer::new, UUIDValueDeserializer::new));
        serializerMap.put(ZonedDateTime.class, new SerializerProviderAdapter(ZonedDateTimeSerializer::new, ZonedDateTimeDeserializer::new));
        serializerMap.put(Duration.class, new SerializerProviderAdapter(CustomizableDurationSerializer::new, DurationValueDeserializer::new));
        serializerMap.put(Period.class, new SerializerProviderAdapter(PeriodTypeConverter::new, ConfigurablePeriodTypeDeserializer::new));
        serializerMap.put(ZoneId.class, new SerializerProviderAdapter(ZoneIdSerializer::new, ZoneIdDeserializer::new));
        serializerMap.put(BigInteger.class, new SerializerProviderAdapter(BigIntegerSerializer::new, BigIntegerDeserializer::new));
        serializerMap.put(BigDecimal.class, new SerializerProviderAdapter(BigDecimalSerializer::new, BigDecimalValueDeserializer::new));
        serializerMap.put(ZoneOffset.class, new SerializerProviderAdapter(ZoneOffsetSerializer::new, ZoneOffsetDeserializer::new));
        serializerMap.put(XMLGregorianCalendar.class, new SerializerProviderAdapter(XMLGregorianCalendarSerializer::new, XMLGregorianCalendarDeserializer::new));
        return serializerMap;
    }

    /**
     * Look for a provider for a supported value type. These serializers are basically singleton stateless shared instances.
     *
     * @param targetType supported type class
     * @param <T>   Type of serializer
     * @return serializer if found
     */
    public static <T> Optional<SerializerProviderAdapter> locateValueSerializerProvider(Class<T> targetType) {
        Class<?> potentialClass = targetType;
        do {
            final SerializerProviderAdapter serializerAdapter = SERIALIZER_PROVIDERS.get(potentialClass);
            if (null != serializerAdapter) {
                return Optional.of(serializerAdapter);
            }
            potentialClass = potentialClass.getSuperclass();
        } while (null != potentialClass);
        return findProviderByCondition(targetType);
    }

    private static <T> Optional<SerializerProviderAdapter> findProviderByCondition(Class<T> targetType) {
        if (!Enum.class.isAssignableFrom(targetType)) {
            if (!JsonString.class.isAssignableFrom(targetType)) {
                if (!JsonNumber.class.isAssignableFrom(targetType)) {
                    if (JsonValue.class.isAssignableFrom(targetType) && !(JsonObject.class.isAssignableFrom(targetType) || JsonArray.class.isAssignableFrom(targetType))) {
                        return Optional.of(SERIALIZER_PROVIDERS.get(JsonValue.class));
                    }
                } else {
                    return Optional.of(SERIALIZER_PROVIDERS.get(JsonNumber.class));
                }
            } else {
                return Optional.of(SERIALIZER_PROVIDERS.get(JsonString.class));
            }
        } else {
            return Optional.of(ENUM_SERIALIZER_ADAPTER);
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
        boolean isContainerValueKnown = Collection.class.isAssignableFrom(targetType) || Map.class.isAssignableFrom(targetType) || JsonValue.class.isAssignableFrom(targetType) || Optional.class.isAssignableFrom(targetType) || targetType.isArray();
        return isContainerValueKnown || locateValueSerializerProvider(targetType).isPresent();
    }

    private static boolean isClassAvailable(String typeName) {
        try {
            Class.forName(typeName);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}

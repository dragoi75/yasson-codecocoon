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

    private final Map<Class<?>, SerializerDeserializerProviderWrapper> serializerMap;

    private final SerializerDeserializerProviderWrapper enumerationProvider;

    private DefaultSerializerRegistry() {
        this.serializerMap = initializeSerializers();
        enumerationProvider = new SerializerDeserializerProviderWrapper(CustomizableEnumSerializer::new, EnumDeserializer::new);
    }

    private Map<Class<?>, SerializerDeserializerProviderWrapper> initializeSerializers() {
        final Map<Class<?>, SerializerDeserializerProviderWrapper> serializerMap = new HashMap<>();

        serializerMap.put(Boolean.class, new SerializerDeserializerProviderWrapper(BooleanSerializer::new, BooleanValueDeserializer::new));
        serializerMap.put(Boolean.TYPE, new SerializerDeserializerProviderWrapper(BooleanSerializer::new, BooleanValueDeserializer::new));
        serializerMap.put(Byte.class, new SerializerDeserializerProviderWrapper(ByteValueSerializer::new, ByteDeserializer::new));
        serializerMap.put(Byte.TYPE, new SerializerDeserializerProviderWrapper(ByteValueSerializer::new, ByteDeserializer::new));
        serializerMap.put(Calendar.class, new SerializerDeserializerProviderWrapper(CalendarTemporalSerializer::new, CalendarTypeParser::new));
        serializerMap.put(GregorianCalendar.class, new SerializerDeserializerProviderWrapper(CalendarTemporalSerializer::new, CalendarTypeParser::new));
        serializerMap.put(Character.class, new SerializerDeserializerProviderWrapper(CharacterSerializer::new, CharacterValueDeserializer::new));
        serializerMap.put(Character.TYPE, new SerializerDeserializerProviderWrapper(CharacterSerializer::new, CharacterValueDeserializer::new));
        serializerMap.put(Date.class, new SerializerDeserializerProviderWrapper(DateSerializer::new, DateValueDeserializer::new));
        serializerMap.put(java.sql.Date.class, new SerializerDeserializerProviderWrapper(SqlDateSerializer::new, SqlDateDeserializer::new));
        serializerMap.put(Double.class, new SerializerDeserializerProviderWrapper(DoubleSerializer::new, DoubleValueDeserializer::new));
        serializerMap.put(Double.TYPE, new SerializerDeserializerProviderWrapper(DoubleSerializer::new, DoubleValueDeserializer::new));
        serializerMap.put(Float.class, new SerializerDeserializerProviderWrapper(FloatSerializer::new, FloatValueDeserializer::new));
        serializerMap.put(Float.TYPE, new SerializerDeserializerProviderWrapper(FloatSerializer::new, FloatValueDeserializer::new));
        serializerMap.put(Instant.class, new SerializerDeserializerProviderWrapper(InstantSerializer::new, InstantDeserializer::new));
        serializerMap.put(Integer.class, new SerializerDeserializerProviderWrapper(FormattedIntegerSerializer::new, IntegerValueDeserializer::new));
        serializerMap.put(Integer.TYPE, new SerializerDeserializerProviderWrapper(FormattedIntegerSerializer::new, IntegerValueDeserializer::new));
        serializerMap.put(JsonNumber.class, new SerializerDeserializerProviderWrapper(JsonValueWriter::new, JsonNumericTypeDeserializer::new));
        serializerMap.put(JsonString.class, new SerializerDeserializerProviderWrapper(JsonValueWriter::new, JsonStringTypeObjectDeserializer::new));
        serializerMap.put(JsonValue.class, new SerializerDeserializerProviderWrapper(JsonValueWriter::new, ConfigurableJsonValueDeserializer::new));
        serializerMap.put(LocalDateTime.class, new SerializerDeserializerProviderWrapper(LocalDateTimeSerializer::new, LocalDateTimeDeserializer::new));
        serializerMap.put(LocalDate.class, new SerializerDeserializerProviderWrapper(LocalDateSerializer::new, LocalDateDeserializer::new));
        serializerMap.put(LocalTime.class, new SerializerDeserializerProviderWrapper(LocalTimeSerializer::new, LocalTimeDeserializer::new));
        serializerMap.put(Long.class, new SerializerDeserializerProviderWrapper(LongSerializer::new, ConfigurableLongDeserializer::new));
        serializerMap.put(Long.TYPE, new SerializerDeserializerProviderWrapper(LongSerializer::new, ConfigurableLongDeserializer::new));
        serializerMap.put(Number.class, new SerializerDeserializerProviderWrapper(NumericTypeSerializer::new, NumericTypeDeserializer::new));
        serializerMap.put(OffsetDateTime.class, new SerializerDeserializerProviderWrapper(OffsetDateTimeSerializer::new, OffsetDateTimeDeserializer::new));
        serializerMap.put(OffsetTime.class, new SerializerDeserializerProviderWrapper(OffsetTimeSerializer::new, OffsetTimeDeserializer::new));
        serializerMap.put(OptionalDouble.class, new SerializerDeserializerProviderWrapper(OptionalDoubleSerializer::new, OptionalDoubleValueDeserializer::new));
        serializerMap.put(OptionalInt.class, new SerializerDeserializerProviderWrapper(OptionalIntSerializer::new, OptionalIntValueDeserializer::new));
        serializerMap.put(OptionalLong.class, new SerializerDeserializerProviderWrapper(OptionalLongValueSerializer::new, OptionalLongDeserializer::new));
        serializerMap.put(Short.class, new SerializerDeserializerProviderWrapper(ShortSerializer::new, ShortDeserializer::new));
        serializerMap.put(Short.TYPE, new SerializerDeserializerProviderWrapper(ShortSerializer::new, ShortDeserializer::new));
        serializerMap.put(String.class, new SerializerDeserializerProviderWrapper(CustomizedStringSerializer::new, StringToTypeDeserializer::new));
        serializerMap.put(TimeZone.class, new SerializerDeserializerProviderWrapper(CustomizableTimeZoneSerializer::new, TimeZoneTypeDeserializerImpl::new));
        serializerMap.put(URI.class, new SerializerDeserializerProviderWrapper(UriValueSerializer::new, URITypeConverter::new));
        serializerMap.put(URL.class, new SerializerDeserializerProviderWrapper(URLSerializer::new, URLDeserializer::new));
        serializerMap.put(UUID.class, new SerializerDeserializerProviderWrapper(UUIDValueSerializer::new, UUIDValueDeserializer::new));
        serializerMap.put(ZonedDateTime.class, new SerializerDeserializerProviderWrapper(ZonedDateTimeSerializer::new, ZonedDateTimeDeserializer::new));
        serializerMap.put(Duration.class, new SerializerDeserializerProviderWrapper(DurationSerializer::new, DurationDeserializer::new));
        serializerMap.put(Period.class, new SerializerDeserializerProviderWrapper(CustomizablePeriodTypeSerializer::new, PeriodTypeParser::new));
        serializerMap.put(ZoneId.class, new SerializerDeserializerProviderWrapper(ZoneIdSerializer::new, ZoneIdDeserializer::new));
        serializerMap.put(BigInteger.class, new SerializerDeserializerProviderWrapper(BigIntegerSerializer::new, BigIntegerValueDeserializer::new));
        serializerMap.put(BigDecimal.class, new SerializerDeserializerProviderWrapper(BigDecimalSerializer::new, BigDecimalDeserializer::new));
        serializerMap.put(ZoneOffset.class, new SerializerDeserializerProviderWrapper(ZoneOffsetSerializer::new, ZoneOffsetDeserializer::new));
        serializerMap.put(XMLGregorianCalendar.class, new SerializerDeserializerProviderWrapper(XMLGregorianCalendarSerializer::new, XMLGregorianCalendarDeserializer::new));

        return Collections.unmodifiableMap(serializerMap);
    }

    /**
     * Look for a provider for a supported value type. These serializers are basically singleton stateless shared instances.
     *
     * @param targetType supported type class
     * @param <T> Type of serializer
     * @return serializer if found
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<SerializerDeserializerProviderWrapper> locateValueSerializerProvider(Class<T> targetType) {
        Class<?> possibleType = targetType;
        do {
            final SerializerDeserializerProviderWrapper serializerWrapper = serializerMap.get(possibleType);
            if (serializerWrapper != null) {
                return Optional.of(serializerWrapper);
            }
            possibleType = possibleType.getSuperclass();
        } while (possibleType != null);

        return findProviderByCondition(targetType);
    }

    private <T> Optional<SerializerDeserializerProviderWrapper> findProviderByCondition(Class<T> targetType) {
        if (Enum.class.isAssignableFrom(targetType)) {
            return Optional.of(enumerationProvider);
        } else if (JsonString.class.isAssignableFrom(targetType)) {
            return Optional.of(serializerMap.get(JsonString.class));
        } else if (JsonNumber.class.isAssignableFrom(targetType)) {
            return Optional.of(serializerMap.get(JsonNumber.class));
        } else if (JsonValue.class.isAssignableFrom(targetType) && !(JsonObject.class.isAssignableFrom(targetType) || JsonArray.class.isAssignableFrom(targetType))) {
            return Optional.of(serializerMap.get(JsonValue.class));
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
        boolean containerValueKnown = Collection.class.isAssignableFrom(targetType)
                || Map.class.isAssignableFrom(targetType)
                || JsonValue.class.isAssignableFrom(targetType)
                || Optional.class.isAssignableFrom(targetType)
                || targetType.isArray();

        return containerValueKnown || locateValueSerializerProvider(targetType).isPresent();
    }


    /**
     * Singleton instance.
     * @return instance
     */
    public static DefaultSerializerRegistry getInstance() {
        return DEFAULT_SERIALIZER_REGISTRY;
    }
}

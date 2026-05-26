/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.serializer.types;

import java.lang.reflect.Type;
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
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Function;

import javax.xml.datatype.XMLGregorianCalendar;

import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;

import org.eclipse.yasson.internal.JsonBindingContext;
import org.eclipse.yasson.internal.model.customization.SerializationCustomizer;
import org.eclipse.yasson.internal.serializer.ModelMarshaller;
import org.eclipse.yasson.internal.serializer.SerializationModelBuilder;

import static org.eclipse.yasson.internal.BuiltInTypes.isClassAvailable;

/**
 * Specific type serializers.
 */
public class TypeSerializerRegistry {

    private static final Map<Class<?>, Function<TypeSerializerBuilder, ModelMarshaller>> TYPE_MARSHALLER_FACTORIES;
    private static final Set<Class<?>> ALLOWED_KEY_TYPES;

    private static final Map<Class<?>, Class<?>> WRAPPER_TYPE_MAP;

    static {
        Map<Class<?>, Function<TypeSerializerBuilder, ModelMarshaller>> cache = new HashMap<>();
        cache.put(Byte.class, ByteSerializer::new);
        cache.put(Byte.TYPE, ByteSerializer::new);
        cache.put(BigDecimal.class, BigDecimalSerializer::new);
        cache.put(BigInteger.class, BigIntegerSerializer::new);
        cache.put(Boolean.class, BooleanSerializer::new);
        cache.put(Boolean.TYPE, BooleanSerializer::new);
        cache.put(Calendar.class, CalendarSerializer::new);
        cache.put(Character.class, CharSerializer::new);
        cache.put(Character.TYPE, CharSerializer::new);
        cache.put(Date.class, DateSerializer::new);
        cache.put(Double.class, DoubleSerializer::new);
        cache.put(Double.TYPE, DoubleSerializer::new);
        cache.put(Duration.class, DurationSerializer::new);
        cache.put(Float.class, FloatSerializer::new);
        cache.put(Float.TYPE, FloatSerializer::new);
        cache.put(Integer.class, IntegerSerializer::new);
        cache.put(Integer.TYPE, IntegerSerializer::new);
        cache.put(Instant.class, InstantSerializer::new);
        cache.put(LocalDateTime.class, LocalDateTimeSerializer::new);
        cache.put(LocalDate.class, LocalDateSerializer::new);
        cache.put(LocalTime.class, LocalTimeSerializer::new);
        cache.put(Long.class, LongSerializer::new);
        cache.put(Long.TYPE, LongSerializer::new);
        cache.put(MonthDay.class, MonthDayTypeSerializer::new);
        cache.put(Number.class, NumberSerializer::new);
        cache.put(Object.class, TypeBasedObjectSerializer::new);
        cache.put(OffsetDateTime.class, OffsetDateTimeSerializer::new);
        cache.put(OffsetTime.class, OffsetTimeSerializer::new);
        cache.put(Path.class, PathSerializer::new);
        cache.put(Period.class, PeriodSerializer::new);
        cache.put(Short.class, ShortSerializer::new);
        cache.put(Short.TYPE, ShortSerializer::new);
        cache.put(String.class, StringSerializer::new);
        cache.put(TimeZone.class, TimeZoneSerializer::new);
        cache.put(URI.class, UriSerializer::new);
        cache.put(URL.class, UrlSerializer::new);
        cache.put(UUID.class, UuidSerializer::new);
        if (isClassAvailable("javax.xml.datatype.XMLGregorianCalendar")) {
            cache.put(XMLGregorianCalendar.class, XmlGregorianCalendarSerializer::new);
        }
        cache.put(YearMonth.class, YearMonthTypeSerializer::new);
        cache.put(ZonedDateTime.class, ZonedDateTimeSerializer::new);
        cache.put(ZoneId.class, ZoneIdSerializer::new);
        cache.put(ZoneOffset.class, ZoneOffsetSerializer::new);
        if (isClassAvailable("java.sql.Date")) {
            cache.put(Date.class, SqlDateSerializer::new);
            cache.put(java.sql.Date.class, SqlDateSerializer::new);
            cache.put(java.sql.Timestamp.class, SqlTimestampSerializer::new);
        }
        TYPE_MARSHALLER_FACTORIES = Map.copyOf(cache);

        Map<Class<?>, Class<?>> optionals = new HashMap<>();
        optionals.put(OptionalDouble.class, Double.class);
        optionals.put(OptionalInt.class, Integer.class);
        optionals.put(OptionalLong.class, Long.class);
        WRAPPER_TYPE_MAP = Map.copyOf(optionals);

        Set<Class<?>> mapKeys = new HashSet<>(TYPE_MARSHALLER_FACTORIES.keySet());
        mapKeys.addAll(optionals.keySet());
        mapKeys.add(JsonNumber.class);
        mapKeys.add(JsonString.class);
        mapKeys.remove(Object.class);
        ALLOWED_KEY_TYPES = Set.copyOf(mapKeys);

    }

    /**
     * Create new type serializer.
     *
     * @param keyType         type of the serializer
     * @param customizer serializer customization
     * @param bindingContext  jsonb context
     * @return new type serializer
     */
    public static ModelMarshaller getTypeSerializer(Class<?> keyType, SerializationCustomizer customizer, JsonBindingContext bindingContext) {
        return getTypeSerializer(Collections.emptyList(), keyType, customizer, bindingContext, false);
    }

    /**
     * Create new type serializer.
     *
     * @param typeSequence         chain of the type predecessors
     * @param keyType         type of the serializer
     * @param customizer serializer customization
     * @param bindingContext  jsonb context
     * @param isMapEntry           whether serializer is a key
     * @return new type serializer
     */
    public static ModelMarshaller getTypeSerializer(List<Type> typeSequence,
                                                    Class<?> keyType,
                                                    SerializationCustomizer customizer,
                                                    JsonBindingContext bindingContext,
                                                    boolean isMapEntry) {
        Class<?> activeClass = keyType;
        List<Type> typeQueue = new LinkedList<>(typeSequence);
        TypeSerializerBuilder serializerMaker = new TypeSerializerBuilder(typeQueue, keyType, customizer, bindingContext, isMapEntry);
        ModelMarshaller marshaller = null;
        if (Object.class.equals(activeClass)) {
            return TYPE_MARSHALLER_FACTORIES.get(activeClass).apply(serializerMaker);
        }
        if (WRAPPER_TYPE_MAP.containsKey(activeClass)) {
            Class<?> containedType = WRAPPER_TYPE_MAP.get(activeClass);
            ModelMarshaller marshallerImpl = getTypeSerializer(typeQueue, containedType, customizer, bindingContext, isMapEntry);
            if (OptionalInt.class.equals(activeClass)) {
                return new OptionalIntSerializer(marshallerImpl);
            } else if (OptionalLong.class.equals(activeClass)) {
                return new OptionalLongSerializer(marshallerImpl);
            } else if (OptionalDouble.class.equals(activeClass)) {
                return new OptionalDoubleSerializer(marshallerImpl);
            } else {
                throw new JsonbException("Unsupported Optional type for serialization: " + keyType);
            }
        }

        if (Enum.class.isAssignableFrom(keyType)) {
            marshaller = new EnumSerializer(serializerMaker);
        } else if (JsonValue.class.isAssignableFrom(keyType)) {
            marshaller = new JsonValueSerializer(serializerMaker);
        }
        if (marshaller == null) {
            do {
                if (TYPE_MARSHALLER_FACTORIES.containsKey(activeClass)) {
                    marshaller = TYPE_MARSHALLER_FACTORIES.get(activeClass).apply(serializerMaker);
                    break;
                }
                activeClass = activeClass.getSuperclass();
            } while (!Object.class.equals(activeClass) && activeClass != null);
        }

        if (isMapEntry) {
            //We do not want any other special serializers around our type serializer if it will be used as a key
            return marshaller;
        }
        return marshaller == null
                ? null
                : SerializationModelBuilder.wrapWithCommonSet(marshaller, customizer, bindingContext);
    }

    private TypeSerializerRegistry() {
        throw new IllegalStateException("Util class cannot be instantiated");
    }

    /**
     * Whether type is the supported key type.
     *
     * @param keyType key type
     * @return whether type is supported key type
     */
    public static boolean isSupportedMapKey(Class<?> keyType) {
        return Enum.class.isAssignableFrom(keyType) || ALLOWED_KEY_TYPES.contains(keyType);
    }

}

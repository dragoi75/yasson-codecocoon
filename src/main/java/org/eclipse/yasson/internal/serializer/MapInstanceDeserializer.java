/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.JsonbDeserializer;
import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbRiEventParser;
import org.eclipse.yasson.internal.ReflectionHelper;

/**
 * Item implementation for {@link java.util.Map} fields.
 * According to JSON specification object can have only string keys, given that maps could only be parsed
 * from JSON objects, implementation is bound to String type.
 *
 * @param <T> map type
 */
public class MapInstanceDeserializer<T extends Map<?, ?>> extends ContainerDeserializerBase<T> implements EmbeddedElement {

    /**
     * Type of value in the map. (Keys must always be Strings, because of JSON spec)
     */
    private final Type entryValueType;

    private final T element;

    /**
     * Create instance of current item with its builder.
     *
     * @param deserializerFactory {@link JsonDeserializerBuilder} used to build this instance
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected MapInstanceDeserializer(JsonDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
        entryValueType = getRuntimeType() instanceof ParameterizedType
                ? ReflectionHelper.resolveActualType(this, ((ParameterizedType) getRuntimeType()).getActualTypeArguments()[1])
                : Object.class;

        this.element = createMapInstance(deserializerFactory);
    }

    @SuppressWarnings("unchecked")
    private T createMapInstance(JsonDeserializerBuilder deserializerFactory) {
        Class<?> baseClass = ReflectionHelper.getRawType(getRuntimeType());
        return baseClass.isInterface()
                ? (T) getMapImpl(baseClass, deserializerFactory)
                : (T) deserializerFactory.getJsonbContext().getInstanceCreator().newInstance(baseClass);
    }

    private Map getMapImpl(Class interfaceClass, JsonDeserializerBuilder deserializerFactory) {
        // SortedMap, NavigableMap
        if (SortedMap.class.isAssignableFrom(interfaceClass)) {
            Class<?> fallbackMapClass = deserializerFactory.getJsonbContext().getConfigProperties().getDefaultMapImplType();
            return SortedMap.class.isAssignableFrom(fallbackMapClass)
                    ? (Map) deserializerFactory.getJsonbContext().getInstanceCreator().newInstance(fallbackMapClass)
                    : new TreeMap<>();
        }
        return new HashMap<>();
    }

    @Override
    public T getInstance(JsonbDeserializer unmarshaller) {
        return element;
    }

    @Override
    public void addResult(Object output) {
        putEntry(getParserContext().getLastKeyName(), convertNullToOptional(entryValueType, output));
    }

    @SuppressWarnings("unchecked")
    private <V> void putEntry(String entryName, V mappedElement) {
        ((Map<String, V>) getInstance(null)).put(entryName, mappedElement);
    }

    @Override
    protected void deserializeNextValue(JsonParser jsonReader, JsonbDeserializer deserializationState) {
        final jakarta.json.bind.serializer.JsonbDeserializer<?> resolvedAdapter = createCollectionOrMapItem(entryValueType, deserializationState.getJsonbContext());
        addResult(resolvedAdapter.deserialize(jsonReader, deserializationState, entryValueType));
    }

    @Override
    protected JsonbRiEventParser.ParsingLevelContext moveToStart(JsonbNavigator jsonReader) {
        jsonReader.moveTo(JsonParser.Event.START_OBJECT);
        return jsonReader.getCurrentLevel();
    }
}

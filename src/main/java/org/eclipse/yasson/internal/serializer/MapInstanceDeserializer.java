/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 *  Sebastien Rius
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.serializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.JsonbRiParser;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.Unmarshaller;

/**
 * Item implementation for {@link java.util.Map} fields.
 * According to JSON specification object can have only string keys, given that maps could only be parsed
 * from JSON objects, implementation is bound to String type.
 *
 * @author Roman Grigoriadi
 */
public class MapInstanceDeserializer<T extends Map<?, ?>> extends BaseContainerDeserializer<T> implements EmbeddedItem {

    /**
     * Sorted map runtime type to use according to ordering strategy set in associated JSONB configuration
     */
    @SuppressWarnings("rawtypes")
    private final Class<? extends SortedMap> sortedMapClass;

    /**
     * Type of value in the map. (Keys must always be Strings, because of JSON spec)
     */
    private final Type valueRuntimeType;

    private final T createdInstance;

    /**
     * Create instance of current item with its builder.
     *
     * @param deserializerFactory {@link JsonbDeserializerBuilder} used to build this instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected MapInstanceDeserializer(JsonbDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
        Class<? extends Map> mapClass = deserializerFactory.getMapImplType();
        if (!SortedMap.class.isAssignableFrom(mapClass)) {
            // if deser. builder decided not to deal with sorted maps by default : defaulting sorted maps to lex order
            sortedMapClass = TreeMap.class;
        } else {
            // if deser. builder decided to deal with sorted maps by default : using its choice for lex or reverse order
            sortedMapClass = (Class<SortedMap>) mapClass;
        }
        valueRuntimeType = getRuntimeType() instanceof ParameterizedType ? ReflectionTypeResolver.resolveActualType(this, ((ParameterizedType) getRuntimeType()).getActualTypeArguments()[1]) : Object.class;
        this.createdInstance = newInstance();
    }

    @SuppressWarnings("unchecked")
    private T newInstance() {
        Class<T> rawClassType = (Class<T>) ReflectionTypeResolver.getRawType(getRuntimeType());
        return rawClassType.isInterface() ? (T) getMapImpl(rawClassType) : ReflectionTypeResolver.createInstanceWithNoArgs(rawClassType);
    }

    private Map<?, ?> getMapImpl(Class interfaceClass) {
        // SortedMap, NavigableMap
        if (SortedMap.class.isAssignableFrom(interfaceClass)) {
            return ReflectionTypeResolver.createInstanceWithNoArgs(sortedMapClass);
        }
        return new HashMap<>();
    }

    @Override
    public T getInstance(Unmarshaller unmarshaller) {
        return createdInstance;
    }

    @Override
    public void addResult(Object addedResult) {
        putEntry(parserContext.getLastKeyName(), convertNullToEmptyOptional(valueRuntimeType, addedResult));
    }

    @SuppressWarnings("unchecked")
    private <V> void putEntry(String entryKey, V mappedValue) {
        ((Map<String, V>) getInstance(null)).put(entryKey, mappedValue);
    }

    @Override
    protected void deserializeNextValue(JsonParser jsonStreamParser, Unmarshaller unmarshalContext) {
        final JsonbDeserializer<?> valueDeserializer = createCollectionOrMapItemDeserializer(valueRuntimeType, unmarshalContext.getJsonbContext());
        addResult(valueDeserializer.deserialize(jsonStreamParser, unmarshalContext, valueRuntimeType));
    }

    @Override
    protected JsonbRiParser.LevelContext advanceToFirst(JsonbParser jsonStreamParser) {
        jsonStreamParser.moveTo(JsonParser.Event.START_OBJECT);
        return jsonStreamParser.getCurrentLevel();
    }
}

/*******************************************************************************
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 * Sebastien Rius
 ******************************************************************************/
package org.eclipse.yasson.internal.serializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.json.stream.JsonParser;

import org.eclipse.yasson.internal.JsonbDeserializer;
import org.eclipse.yasson.internal.JsonbRiStreamParser;
import org.eclipse.yasson.internal.JsonbStructureNavigator;
import org.eclipse.yasson.internal.ReflectionTypeResolver;

/**
 * Item implementation for {@link java.util.Map} fields.
 * According to JSON specification object can have only string keys, given that maps could only be parsed
 * from JSON objects, implementation is bound to String type.
 *
 * @author Roman Grigoriadi
 */
public class MapDeserializer<T extends Map<?,?>> extends AbstractCollectionDeserializer<T> implements EmbeddedItem {

    /**
     * Type of value in the map. (Keys must always be Strings, because of JSON spec)
     */
    private final Type mapValueRuntimeType;

    private final T instance;

    /**
     * Create instance of current item with its builder.
     *
     * @param builder {@link JsonDeserializerBuilder} used to build this instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected MapDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
        mapValueRuntimeType = getRuntimeType() instanceof ParameterizedType ?
                ReflectionTypeResolver.resolveActualType(this, ((ParameterizedType) getRuntimeType()).getActualTypeArguments()[1])
                : Object.class;

        this.instance = createInstance(builder);
    }

    @SuppressWarnings("unchecked")
    private T createInstance(JsonDeserializerBuilder builder) {
        Class<?> rawType = ReflectionTypeResolver.getRawType(getRuntimeType());
        return rawType.isInterface() ? (T) getMapImpl(rawType, builder)
                : (T) builder.getJsonbContext().getInstanceCreator().createInstance(rawType);
    }

    private Map getMapImpl(Class ifcType, JsonDeserializerBuilder builder) {
        // SortedMap, NavigableMap
        if (SortedMap.class.isAssignableFrom(ifcType)) {
            Class<?> defaultMapImplType = builder.getJsonbContext().getConfigProperties().getDefaultMapImplType();
            return SortedMap.class.isAssignableFrom(defaultMapImplType) ?
                    (Map) builder.getJsonbContext().getInstanceCreator().createInstance(defaultMapImplType) :
                    new TreeMap<>();
        }
        return new HashMap<>();
    }

    @Override
    public T getInstance(JsonbDeserializer unmarshaller) {
        return instance;
    }

    @Override
    public void appendValueToResult(Object result) {
        appendCaptor(parserContext.getLastKeyName(), convertNullToEmptyOptional(mapValueRuntimeType, result));
    }

    @SuppressWarnings("unchecked")
    private <V> void appendCaptor(String key, V value) {
        ((Map<String, V>) getInstance(null)).put(key, value);
    }

    @Override
    protected void deserializeItem(JsonParser parser, JsonbDeserializer context) {
        final javax.json.bind.serializer.JsonbDeserializer<?> deserializer = createCollectionOrMapItemDeserializer(mapValueRuntimeType, context.getJsonbContext());
        appendValueToResult(deserializer.deserialize(parser, context, mapValueRuntimeType));
    }

    @Override
    protected JsonbRiStreamParser.ParsingLevelContext moveToFirstElement(JsonbStructureNavigator parser) {
        parser.moveTo(JsonParser.Event.START_OBJECT);
        return parser.getCurrentLevel();
    }
}

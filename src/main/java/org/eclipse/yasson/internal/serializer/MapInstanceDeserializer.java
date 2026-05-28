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

import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;

import org.eclipse.yasson.internal.JsonbCursor;
import org.eclipse.yasson.internal.JsonbStreamingParser;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.ReflectiveTypeUtils;

/**
 * Item implementation for {@link java.util.Map} fields.
 * According to JSON specification object can have only string keys, given that maps could only be parsed
 * from JSON objects, implementation is bound to String type.
 *
 * @author Roman Grigoriadi
 */
public class MapInstanceDeserializer<T extends Map<?,?>> extends BaseContainerDeserializer<T> implements EmbeddedItem {

    /**
     * Type of value in the map. (Keys must always be Strings, because of JSON spec)
     */
    private final Type valueRuntimeType;

    private final T valueObject;

    @SuppressWarnings("unchecked")
    private <V> void putEntry(String entryName, V entryObj) {
        ((Map<String, V>) getInstance(null)).put(entryName, entryObj);
    }

    @Override
    protected void deserializeNextValue(JsonParser tokenReader, JsonbUnmarshaller unmarshalHelper) {
        final JsonbDeserializer<?> valueHandler = createCollectionOrMapItemDeserializer(valueRuntimeType, unmarshalHelper.getJsonbContext());
        addResult(valueHandler.deserialize(tokenReader, unmarshalHelper, valueRuntimeType));
    }

    @Override
    protected JsonbStreamingParser.LevelParseContext moveToFirstToken(JsonbCursor tokenReader) {
        tokenReader.moveTo(JsonParser.Event.START_OBJECT);
        return tokenReader.getCurrentLevel();
    }

    /**
     * Create instance of current item with its builder.
     *
     * @param deserializerFactory {@link JsonValueDeserializerBuilder} used to build this instance
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected MapInstanceDeserializer(JsonValueDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
        valueRuntimeType = getRuntimeType() instanceof ParameterizedType ?
                ReflectiveTypeUtils.resolveGenericType(this, ((ParameterizedType) getRuntimeType()).getActualTypeArguments()[1])
                : Object.class;

        this.valueObject = createMapInstance(deserializerFactory);
    }

    private Map getMapImpl(Class interfaceClass, JsonValueDeserializerBuilder deserializerFactory) {
        // SortedMap, NavigableMap
        if (SortedMap.class.isAssignableFrom(interfaceClass)) {
            Class<?> defaultMapClass = deserializerFactory.getJsonbContext().getConfigProperties().getDefaultMapImplType();
            return SortedMap.class.isAssignableFrom(defaultMapClass) ?
                    (Map) deserializerFactory.getJsonbContext().getInstanceCreator().createInstance(defaultMapClass) :
                    new TreeMap<>();
        }
        return new HashMap<>();
    }

    @Override
    public T getInstance(JsonbUnmarshaller unmarshaller) {
        return valueObject;
    }

    @SuppressWarnings("unchecked")
    private T createMapInstance(JsonValueDeserializerBuilder deserializerFactory) {
        Class<?> concreteClass = ReflectiveTypeUtils.getRawType(getRuntimeType());
        return concreteClass.isInterface() ? (T) getMapImpl(concreteClass, deserializerFactory)
                : (T) deserializerFactory.getJsonbContext().getInstanceCreator().createInstance(concreteClass);
    }

    @Override
    public void addResult(Object outputValue) {
        putEntry(parserContext.getLastKeyName(), nullToOptionalEmpty(valueRuntimeType, outputValue));
    }

}

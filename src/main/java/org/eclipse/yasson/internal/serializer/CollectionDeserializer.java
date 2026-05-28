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
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonUnmarshaller;
import org.eclipse.yasson.internal.JsonbStreamParser;
import org.eclipse.yasson.internal.JsonbRiStreamParser;
import org.eclipse.yasson.internal.ReflectionHelper;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Item implementation for {@link java.util.List} fields
 *
 * @author Roman Grigoriadi
 */
class CollectionDeserializer<T extends Collection<?>> extends AbstractContainerDeserializer<T> implements EmbeddedItem {

    /**
     * Generic bound parameter of List.
     */
    private final Type collectionValueType;

    private T instance;

    @Override
    public T getInstance(JsonUnmarshaller unmarshaller) {
        return instance;
    }

    @Override
    protected JsonbRiStreamParser.LevelParseState moveToFirst(JsonbStreamParser parser) {
        parser.moveTo(JsonParser.Event.START_ARRAY);
        return parser.getCurrentLevel();
    }

    @Override
    protected void deserializeNext(JsonParser parser, JsonUnmarshaller context) {
        final JsonbDeserializer<?> deserializer = newCollectionOrMapItem(collectionValueType, context.getJsonbContext());
        appendResult(deserializer.deserialize(parser, context, collectionValueType));
    }

    @SuppressWarnings("unchecked")
    private <T> void appendCaptor(T object) {
        ((Collection<T>) instance).add(object);
    }

    @SuppressWarnings("unchecked")
    private T createInstance() {
        Class<T> rawType = (Class<T>) ReflectionHelper.getRawType(getRuntimeType());
        assert Collection.class.isAssignableFrom(rawType);
        if (rawType.isInterface()) {
            final T x = createInterfaceInstance(rawType);
            if (null != x)
                return x;
        }
        return ReflectionHelper.createInstanceNoArgs(rawType);
    }

    /**
     * @param builder {@link DeserializationBuilder ) used to build this instance
     */
    protected CollectionDeserializer(DeserializationBuilder builder) {
        super(builder);
        collectionValueType = getRuntimeType() instanceof ParameterizedType ? ReflectionHelper.resolveGenericType(this, ((ParameterizedType) getRuntimeType()).getActualTypeArguments()[0]) : Object.class;
        instance = createInstance();
    }

    @Override
    public void appendResult(Object result) {
        appendCaptor(convertNullToOptionalEmpty(collectionValueType, result));
    }

    @SuppressWarnings("unchecked")
    private T createInterfaceInstance(Class<?> ifcType) {
        if (List.class.isAssignableFrom(ifcType)) {
            if (ifcType == LinkedList.class) {
                return (T) new LinkedList();
            }
            return (T) new ArrayList<>();
        }
        if (Set.class.isAssignableFrom(ifcType)) {
            if (SortedSet.class.isAssignableFrom(ifcType)) {
                return (T) new TreeSet<>();
            }
            return (T) new HashSet<>();
        }
        if (Queue.class.isAssignableFrom(ifcType)) {
            return (T) new ArrayDeque<>();
        }
        if (ifcType == Collection.class) {
            return (T) new ArrayList();
        }
        return null;
    }

}

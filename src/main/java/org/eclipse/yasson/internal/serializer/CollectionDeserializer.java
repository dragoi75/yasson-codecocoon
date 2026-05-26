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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbStreamingParser;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.ReflectionTypeResolver;

/**
 * Item implementation for {@link java.util.List} fields.
 */
class CollectionDeserializer<T extends Collection<?>> extends AbstractContainerDeserializer<T> implements EmbeddedElement {

    /**
     * Generic bound parameter of List.
     */
    private final Type collectionValueType;

    private T instance;

    @SuppressWarnings("unchecked")
    private <T> void appendCaptor(T object) {
        ((Collection<T>) instance).add(object);
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

    @Override
    protected JsonbStreamingParser.LevelParseContext moveToFirst(JsonbNavigator parser) {
        parser.moveTo(JsonParser.Event.START_ARRAY);
        return parser.getCurrentLevel();
    }

    @Override
    protected void deserializeNext(JsonParser parser, JsonbUnmarshaller context) {
        final JsonbDeserializer<?> deserializer = newCollectionOrMapItem(collectionValueType, context.getJsonbContext());
        appendResult(deserializer.deserialize(parser, context, collectionValueType));
    }

    @SuppressWarnings("unchecked")
    private T createInstance(JsonDeserializerBuilder builder) {
        Class<T> rawType = (Class<T>) ReflectionTypeResolver.getRawType(getRuntimeType());
        if (!rawType.isInterface()) {
            if (EnumSet.class.isAssignableFrom(rawType)) {
                return (T) EnumSet.noneOf((Class<Enum>) collectionValueType);
            }
        } else {
            final T x = createInterfaceInstance(rawType);
            if (null != x) {
                return x;
            }
        }
        return builder.getJsonbContext().getInstanceCreator().createInstance(rawType);
    }

    @Override
    public T getInstance(JsonbUnmarshaller unmarshaller) {
        return instance;
    }

    /**
     * @param builder {@link JsonDeserializerBuilder ) used to build this instance
     */
    protected CollectionDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
        collectionValueType = getRuntimeType() instanceof ParameterizedType ? ReflectionTypeResolver.resolveTypeDefault(this, ((ParameterizedType) getRuntimeType()).getActualTypeArguments()[0]) : Object.class;
        instance = createInstance(builder);
    }

}

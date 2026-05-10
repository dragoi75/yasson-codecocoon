/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.ReflectionHelper;
import org.eclipse.yasson.internal.RuntimeTypeInfo;
import org.eclipse.yasson.internal.model.ClassModel;

/**
 * Internal container de-serializing interface.
 *
 * @param <T> container type
 */
class ContainerDeserializerUtils {

    private ContainerDeserializerUtils() {
        throw new IllegalStateException("Util classes cannot be instantiated!");
    }

    /**
     * Resolve {@code Map} key type.
     *
     * @param item    item containing wrapper class of a type field, shall not be {@code null}
     * @param mapType type to resolve, typically field type or generic bound, shall not be {@code null}
     * @return resolved {@code Map} key type
     */
    public static Type mapKeyType(RuntimeTypeInfo item, Type mapType) {
        return mapType instanceof ParameterizedType
                ? ReflectionHelper.resolveActualType(item, ((ParameterizedType) mapType).getActualTypeArguments()[0])
                : Object.class;
    }

    /**
     * Resolve {@code Map} value type.
     *
     * @param item    item containing wrapper class of a type field, shall not be {@code null}
     * @param mapType type to resolve, typically field type or generic bound, shall not be {@code null}
     * @return resolved {@code Map} value type
     */
    public static Type mapValueType(RuntimeTypeInfo item, Type mapType) {
        return mapType instanceof ParameterizedType
                ? ReflectionHelper.resolveActualType(item, ((ParameterizedType) mapType).getActualTypeArguments()[1])
                : Object.class;
    }

    /**
     * Creates an instance of {@code Map} being de-serialized.
     *
     * @param <T>     type of {@code Map} instance to be returned
     * @param builder de-serializer builder
     * @param mapType type of returned {@code Map} instance
     * @return created {@code Map} instance
     */
    @SuppressWarnings("unchecked")
    public static <T extends Map<?, ?>> T createMapInstance(JsonDeserializerBuilder builder, Type mapType) {
        Class<?> rawType = ReflectionHelper.getRawType(mapType);
        if (rawType.isInterface()) {
            if (SortedMap.class.isAssignableFrom(rawType)) {
                Class<?> defaultMapImplType = builder.getJsonbContext().getConfigProperties().getDefaultMapImplType();
                return SortedMap.class.isAssignableFrom(defaultMapImplType)
                        ? (T) builder.getJsonbContext().getInstanceCreator().newInstance(defaultMapImplType)
                        : (T) new TreeMap<>();
            } else {
                return (T) new HashMap<>();
            }
        } else {
            return (T) builder.getJsonbContext().getInstanceCreator().newInstance(rawType);
        }
    }

    /**
     * Builds new de-serializer for {@code Collection} or {@code Map} item (key or value).
     *
     * @param wrapper   item wrapper. {@code Collection} or {@code Map} instance.
     * @param valueType type of deserialized value
     * @param ctx       JSON-B parser context
     * @param event     JSON parser event
     * @return de-serializer for {@code Collection} or {@code Map} item
     */
    public static JsonbDeserializer<?> newCollectionOrMapItem(CurrentItem<?> wrapper,
                                                              Type valueType,
                                                              JsonbRuntimeContext ctx,
                                                              JsonParser.Event event) {
        //TODO needs performance optimization on not to create deserializer each time
        //TODO In contrast to serialization value type cannot change here
        Type actualValueType = ReflectionHelper.resolveActualType(wrapper, valueType);
        JsonDeserializerBuilder deserializerBuilder = newUnmarshallerItemBuilder(wrapper, ctx, event).setType(actualValueType);
        if (!DefaultSerializers.getInstance().isKnownType(ReflectionHelper.getRawType(actualValueType))) {
            ClassModel classModel = ctx.getMappingContext().getOrCreateClassModel(ReflectionHelper.getRawType(actualValueType));
            deserializerBuilder.setCustomization(classModel == null ? null : classModel.getClassCustomization());
        }
        return deserializerBuilder.buildDeserializer();
    }

    /**
     * Creates new instance of {@code DeserializerBuilder}.
     *
     * @param wrapper item wrapper. {@code Collection} or {@code Map} instance.
     * @param ctx     JSON-P parser context
     * @param event   JSON parser event
     * @return new instance of {@code DeserializerBuilder}
     */
    public static JsonDeserializerBuilder newUnmarshallerItemBuilder(CurrentItem<?> wrapper,
                                                                     JsonbRuntimeContext ctx,
                                                                     JsonParser.Event event) {
        return new JsonDeserializerBuilder(ctx).setWrapper(wrapper).setJsonValueType(event);
    }

}

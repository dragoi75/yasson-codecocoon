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

package org.eclipse.yasson.internal.serializer;

import java.util.Map;

import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.DefaultSerializationContext;
import org.eclipse.yasson.internal.serializer.types.TypeSerializers;

/**
 * Map container serializer.
 */
abstract class MapSerializer implements ModelMarshaller {

    private final ModelMarshaller keySerializer;
    private final ModelMarshaller valueSerializer;

    private static final class DynamicMapSerializer extends MapSerializer {

        private final StringKeyMapSerializer stringMap;
        private final ObjectKeyMapSerializer objectMap;
        private MapSerializer serializer;

        @SuppressWarnings("unchecked")
        @Override
        public void marshal(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            if (serializer == null) {
                //We have to be sure that Map with Object as a key contains only supported values for key:value format map.
                Map<Object, Object> map = (Map<Object, Object>) value;
                boolean suitable = true;
                for (Object key : map.keySet()) {
                    if (key == null) {
                        if (context.getJsonbContext().getConfigProperties().isForceMapArraySerializerForNullKeys()) {
                            suitable = false;
                            break;
                        }
                        continue;
                    }
                    Class<?> keyClass = key.getClass();
                    if (TypeSerializers.isSupportedMapKey(keyClass)) {
                        continue;
                    }
                    //No other checks needed. Map is not suitable for normal key:value map. Wrapping object needs to be used.
                    suitable = false;
                    break;
                }
                serializer = suitable ? stringMap : objectMap;
            }
            serializer.marshal(value, generator, context);
        }

        DynamicMapSerializer(ModelMarshaller keySerializer,
                             ModelMarshaller valueSerializer) {
            super(keySerializer, valueSerializer);
            stringMap = new StringKeyMapSerializer(keySerializer, valueSerializer);
            objectMap = new ObjectKeyMapSerializer(keySerializer, valueSerializer);
        }

    }

    private static final class StringKeyMapSerializer extends MapSerializer {

        @SuppressWarnings("unchecked")
        @Override
        public void marshal(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            Map<Object, Object> map = (Map<Object, Object>) value;
            generator.writeStartObject();
            map.forEach((key, val) -> {
                getKeySerializer().marshal(key, generator, context);
                getValueSerializer().marshal(val, generator, context);
            });
            generator.writeEnd();
        }

        StringKeyMapSerializer(ModelMarshaller keySerializer,
                               ModelMarshaller valueSerializer) {
            super(keySerializer, valueSerializer);
        }

    }

    private static final class ObjectKeyMapSerializer extends MapSerializer {

        @SuppressWarnings("unchecked")
        @Override
        public void marshal(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            Map<Object, Object> map = (Map<Object, Object>) value;
            generator.writeStartArray();
            map.forEach((key, val) -> {
                generator.writeStartObject();
                generator.writeKey("key");
                if (key == null) {
                    generator.writeNull();
                } else {
                    getKeySerializer().marshal(key, generator, context);
                }
                generator.writeKey("value");
                getValueSerializer().marshal(val, generator, context);
                generator.writeEnd();
            });
            generator.writeEnd();
        }

        ObjectKeyMapSerializer(ModelMarshaller keySerializer,
                               ModelMarshaller valueSerializer) {
            super(keySerializer, valueSerializer);
        }

    }

    static MapSerializer create(Class<?> keyClass, ModelMarshaller keySerializer, ModelMarshaller valueSerializer) {
        if (TypeSerializers.isSupportedMapKey(keyClass)) {
            return new StringKeyMapSerializer(keySerializer, valueSerializer);
        } else if (Object.class.equals(keyClass)) {
            return new DynamicMapSerializer(keySerializer, valueSerializer);
        }
        return new ObjectKeyMapSerializer(keySerializer, valueSerializer);
    }

    ModelMarshaller getValueSerializer() {
        return valueSerializer;
    }

    MapSerializer(ModelMarshaller keySerializer, ModelMarshaller valueSerializer) {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
    }

    ModelMarshaller getKeySerializer() {
        return keySerializer;
    }

}

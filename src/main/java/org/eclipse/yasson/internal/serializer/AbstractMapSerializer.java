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

import org.eclipse.yasson.internal.SerializationContextImpl;
import org.eclipse.yasson.internal.serializer.types.TypeSerializerRegistry;

/**
 * Map container serializer.
 */
abstract class AbstractMapSerializer implements ModelMarshaller {

    private final ModelMarshaller keyMarshaller;
    private final ModelMarshaller valueMarshaller;

    AbstractMapSerializer(ModelMarshaller keyMarshaller, ModelMarshaller valueMarshaller) {
        this.keyMarshaller = keyMarshaller;
        this.valueMarshaller = valueMarshaller;
    }

    ModelMarshaller getKeySerializer() {
        return keyMarshaller;
    }

    ModelMarshaller getValueSerializer() {
        return valueMarshaller;
    }

    static AbstractMapSerializer createMapSerializer(Class<?> keyType, ModelMarshaller keyMarshaller, ModelMarshaller valueMarshaller) {
        if (TypeSerializerRegistry.isSupportedMapKey(keyType)) {
            return new StringKeyedMapSerializer(keyMarshaller, valueMarshaller);
        } else if (Object.class.equals(keyType)) {
            return new RuntimeMapSerializer(keyMarshaller, valueMarshaller);
        }
        return new ObjectKeyedMapSerializer(keyMarshaller, valueMarshaller);
    }

    private static final class RuntimeMapSerializer extends AbstractMapSerializer {

        private final StringKeyedMapSerializer stringKeyedMap;
        private final ObjectKeyedMapSerializer objectKeyedMap;
        private AbstractMapSerializer mapHandler;

        RuntimeMapSerializer(ModelMarshaller keyMarshaller,
                             ModelMarshaller valueMarshaller) {
            super(keyMarshaller, valueMarshaller);
            stringKeyedMap = new StringKeyedMapSerializer(keyMarshaller, valueMarshaller);
            objectKeyedMap = new ObjectKeyedMapSerializer(keyMarshaller, valueMarshaller);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void marshal(Object obj, JsonGenerator jsonGen, SerializationContextImpl serializationCtx) {
            if (mapHandler == null) {
                //We have to be sure that Map with Object as a key contains only supported values for key:value format map.
                Map<Object, Object> entries = (Map<Object, Object>) obj;
                boolean isApplicable = true;
                for (Object identifier : entries.keySet()) {
                    if (identifier == null) {
                        if (serializationCtx.getJsonbContext().getConfigProperties().isForceMapArraySerializerForNullKeys()) {
                            isApplicable = false;
                            break;
                        }
                        continue;
                    }
                    Class<?> keyType = identifier.getClass();
                    if (TypeSerializerRegistry.isSupportedMapKey(keyType)) {
                        continue;
                    }
                    //No other checks needed. Map is not suitable for normal key:value map. Wrapping object needs to be used.
                    isApplicable = false;
                    break;
                }
                mapHandler = isApplicable ? stringKeyedMap : objectKeyedMap;
            }
            mapHandler.marshal(obj, jsonGen, serializationCtx);
        }

    }

    private static final class StringKeyedMapSerializer extends AbstractMapSerializer {

        StringKeyedMapSerializer(ModelMarshaller keyMarshaller,
                                 ModelMarshaller valueMarshaller) {
            super(keyMarshaller, valueMarshaller);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void marshal(Object obj, JsonGenerator jsonGen, SerializationContextImpl serializationCtx) {
            Map<Object, Object> entries = (Map<Object, Object>) obj;
            jsonGen.writeStartObject();
            entries.forEach((identifier, mappedObj) -> {
                getKeySerializer().marshal(identifier, jsonGen, serializationCtx);
                getValueSerializer().marshal(mappedObj, jsonGen, serializationCtx);
            });
            jsonGen.writeEnd();
        }

    }

    private static final class ObjectKeyedMapSerializer extends AbstractMapSerializer {

        ObjectKeyedMapSerializer(ModelMarshaller keyMarshaller,
                                 ModelMarshaller valueMarshaller) {
            super(keyMarshaller, valueMarshaller);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void marshal(Object obj, JsonGenerator jsonGen, SerializationContextImpl serializationCtx) {
            Map<Object, Object> entries = (Map<Object, Object>) obj;
            jsonGen.writeStartArray();
            entries.forEach((identifier, mappedObj) -> {
                jsonGen.writeStartObject();
                jsonGen.writeKey("key");
                if (identifier == null) {
                    jsonGen.writeNull();
                } else {
                    getKeySerializer().marshal(identifier, jsonGen, serializationCtx);
                }
                jsonGen.writeKey("value");
                getValueSerializer().marshal(mappedObj, jsonGen, serializationCtx);
                jsonGen.writeEnd();
            });
            jsonGen.writeEnd();
        }

    }

}

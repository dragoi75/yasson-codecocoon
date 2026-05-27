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

    private static final class RuntimeMapSerializer extends AbstractMapSerializer {

        private final StringKeyedMapSerializer stringKeyedMap;

        private final ObjectKeyedMapSerializer objectKeyedMap;

        private AbstractMapSerializer mapHandler;

        @SuppressWarnings("unchecked")
        @Override
        public void marshal(Object obj, JsonGenerator jsonGen, SerializationContextImpl serializationCtx) {
            if (null == mapHandler) {
                //We have to be sure that Map with Object as a key contains only supported values for key:value format map.
                Map<Object, Object> entries = (Map<Object, Object>) obj;
                boolean isApplicable = true;
                for (Object identifier : entries.keySet()) {
                    if (null == identifier) {
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

        RuntimeMapSerializer(ModelMarshaller keyMarshaller, ModelMarshaller valueMarshaller) {
            super(keyMarshaller, valueMarshaller);
            stringKeyedMap = new StringKeyedMapSerializer(keyMarshaller, valueMarshaller);
            objectKeyedMap = new ObjectKeyedMapSerializer(keyMarshaller, valueMarshaller);
        }

    }

    private static final class StringKeyedMapSerializer extends AbstractMapSerializer {

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

        StringKeyedMapSerializer(ModelMarshaller keyMarshaller, ModelMarshaller valueMarshaller) {
            super(keyMarshaller, valueMarshaller);
        }

    }

    private static final class ObjectKeyedMapSerializer extends AbstractMapSerializer {

        @SuppressWarnings("unchecked")
        @Override
        public void marshal(Object obj, JsonGenerator jsonGen, SerializationContextImpl serializationCtx) {
            Map<Object, Object> entries = (Map<Object, Object>) obj;
            jsonGen.writeStartArray();
            entries.forEach((identifier, mappedObj) -> {
                jsonGen.writeStartObject();
                jsonGen.writeKey("key");
                if (null != identifier) {
                    getKeySerializer().marshal(identifier, jsonGen, serializationCtx);
                } else {
                    jsonGen.writeNull();
                }
                jsonGen.writeKey("value");
                getValueSerializer().marshal(mappedObj, jsonGen, serializationCtx);
                jsonGen.writeEnd();
            });
            jsonGen.writeEnd();
        }

        ObjectKeyedMapSerializer(ModelMarshaller keyMarshaller, ModelMarshaller valueMarshaller) {
            super(keyMarshaller, valueMarshaller);
        }

    }

    static AbstractMapSerializer createMapSerializer(Class<?> keyType, ModelMarshaller keyMarshaller, ModelMarshaller valueMarshaller) {
        if (!TypeSerializerRegistry.isSupportedMapKey(keyType)) {
            if (Object.class.equals(keyType)) {
                return new RuntimeMapSerializer(keyMarshaller, valueMarshaller);
            }
        } else {
            return new StringKeyedMapSerializer(keyMarshaller, valueMarshaller);
        }
        return new ObjectKeyedMapSerializer(keyMarshaller, valueMarshaller);
    }

    ModelMarshaller getValueSerializer() {
        return valueMarshaller;
    }

    ModelMarshaller getKeySerializer() {
        return keyMarshaller;
    }

    AbstractMapSerializer(ModelMarshaller keyMarshaller, ModelMarshaller valueMarshaller) {
        this.keyMarshaller = keyMarshaller;
        this.valueMarshaller = valueMarshaller;
    }

}

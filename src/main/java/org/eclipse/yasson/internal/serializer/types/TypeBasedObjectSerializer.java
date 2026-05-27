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
package org.eclipse.yasson.internal.serializer.types;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.yasson.internal.SerializationContextImpl;
import org.eclipse.yasson.internal.model.customization.SerializationCustomizer;
import org.eclipse.yasson.internal.serializer.ModelMarshaller;
import org.eclipse.yasson.internal.serializer.SerializationModelBuilder;

/**
 * Object type serializer. Dynamically resolves the serialized type based on the serialized instance class.
 */
public class TypeBasedObjectSerializer extends TypeSerializer<Object> {

    private final SerializationCustomizer serializationCustomizer;

    private final Map<Class<?>, ModelMarshaller> serializerMap;

    private final List<Type> typeSequence;

    private final boolean keyFlag;

    /**
     * Add serializer to the cache.
     *
     * @param targetClass           class of the serializer
     * @param marshallerInstance model serializer bound to the class
     */
    public void registerSpecificSerializer(Class<?> targetClass, ModelMarshaller marshallerInstance) {
        serializerMap.put(targetClass, marshallerInstance);
    }

    @Override
    void serializeKey(Object identifier, JsonGenerator jsonOut, SerializationContextImpl ctx) {
        if (null == identifier) {
            super.serializeKey(null, jsonOut, ctx);
            return;
        }
        //Dynamically resolved type during runtime. Cached in SerializationModelCreator.
        resolveSerializer(identifier, jsonOut, ctx);
    }

    private void resolveSerializer(Object identifier, JsonGenerator jsonOut, SerializationContextImpl ctx) {
        Class<?> targetClass = identifier.getClass();
        serializerMap.computeIfAbsent(targetClass, aClass -> {
            SerializationModelBuilder modelBuilder = ctx.getJsonbContext().getSerializationModelCreator();
            return modelBuilder.resolveSerializerChainRuntime(new LinkedList<>(typeSequence), targetClass, serializationCustomizer, false, keyFlag);
        }).marshal(identifier, jsonOut, ctx);
    }

    TypeBasedObjectSerializer(TypeSerializerBuilder typeBuilder) {
        super(typeBuilder);
        this.serializationCustomizer = typeBuilder.getCustomization();
        this.serializerMap = new ConcurrentHashMap<>();
        this.typeSequence = new LinkedList<>(typeBuilder.getChain());
        this.keyFlag = typeBuilder.isKey();
    }

    @Override
    void serializeValue(Object obj, JsonGenerator jsonOut, SerializationContextImpl ctx) {
        //Dynamically resolved type during runtime. Cached in SerializationModelCreator.
        resolveSerializer(obj, jsonOut, ctx);
    }

}

/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Type;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.yasson.internal.JsonbMarshaller;
import org.eclipse.yasson.internal.ObjectProcessingContext;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.JsonbPropertyMetadata;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Serializer for adapted object.
 * Converts object using components first, than serializes result with standard process.
 *
 * @param <T> source type
 * @param <A> adapted type
 */
public class AdaptedObjectSerializer<T, A> implements CurrentItem<T>, JsonbSerializer<T> {

    private final ClassDescriptor classModel;

    private final TypeAdapterBinding adapterInfo;

    @Override
    public Type getRuntimeType() {
        return null;
    }

    @Override
    public ClassDescriptor getClassModel() {
        return null;
    }

    @Override
    public CurrentItem<?> getWrapper() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
        ObjectProcessingContext context = (ObjectProcessingContext) ctx;
        try {
            if (!context.addToProcessedObjects(obj)) {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.RECURSIVE_REFERENCE, obj.getClass()));
            } else {
                final JsonbAdapter<T, A> adapter = (JsonbAdapter<T, A>) adapterInfo.getAdapter();
                A adapted = adapter.adaptToJson(obj);
                if (null == adapted) {
                    generator.writeNull();
                    return;
                }
                final JsonbSerializer<A> serializer = resolveSerializer((JsonbMarshaller) ctx, adapted);
                serializer.serialize(adapted, generator, ctx);
            }
        } catch (Exception e) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.ADAPTER_EXCEPTION, adapterInfo.getBindingType(), adapterInfo.getToType(), adapterInfo.getAdapter().getClass()), e);
        } finally {
            context.removeFromProcessedObjects(obj);
        }
    }

    /**
     * Creates AdapterObjectSerializer.
     *
     * @param classModel Class model.
     * @param adapter    Adapter.
     */
    public AdaptedObjectSerializer(ClassDescriptor classModel, TypeAdapterBinding adapter) {
        this.classModel = classModel;
        this.adapterInfo = adapter;
    }

    @SuppressWarnings("unchecked")
    private JsonbSerializer<A> resolveSerializer(JsonbMarshaller ctx, A adapted) {
        final ContainerSerializerFactory cached = ctx.getMappingContext().getSerializerProvider(adapted.getClass());
        if (null != cached) {
            return (JsonbSerializer<A>) cached.createSerializer(new JsonbPropertyMetadata().setWrapper(this).setRuntimeType(null == classModel ? null : classModel.getType()));
        }
        return (JsonbSerializer<A>) new TypeSerializerBuilder(ctx.getJsonbContext()).setObjectClass(adapted.getClass()).setCustomization(null == classModel ? null : classModel.getClassCustomization()).setWrapper(this).buildSerializer();
    }

}

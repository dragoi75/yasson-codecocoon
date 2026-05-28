/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.ObjectMarshaller;
import org.eclipse.yasson.internal.ProcessingContextManager;
import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.JsonbPropertyDescriptor;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import javax.json.bind.JsonbException;
import javax.json.bind.adapter.JsonbAdapter;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import java.lang.reflect.Type;

/**
 * Serializer for adapted object.
 * Converts object using components first, than serializes result with standard process.
 *
 * @author Roman Grigoriadi
 */
public class AdaptedObjectSerializer<T, A> implements CurrentItemProvider<T>, JsonbSerializer<T> {

    private final ClassDescriptor classModel;

    private final AdapterBinding adapterInfo;

    @Override
    public ClassDescriptor getClassModel() {
        return null;
    }

    @Override
    public CurrentItemProvider<?> getWrapper() {
        return null;
    }

    @Override
    public Type getRuntimeType() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
        ProcessingContextManager context = (ProcessingContextManager) ctx;
        try {
            if (!context.registerProcessedObject(obj)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.RECURSIVE_REFERENCE, obj.getClass()));
            } else {
                final JsonbAdapter<T, A> adapter = (JsonbAdapter<T, A>) adapterInfo.getAdapter();
                A adapted = adapter.adaptToJson(obj);
                if (null == adapted) {
                    generator.writeNull();
                    return;
                }
                final JsonbSerializer<A> serializer = resolveSerializer((ObjectMarshaller) ctx, adapted);
                serializer.serialize(adapted, generator, ctx);
            }
        } catch (Exception e) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.ADAPTER_EXCEPTION, adapterInfo.getBindingType(), adapterInfo.getToType(), adapterInfo.getAdapter().getClass()), e);
        } finally {
            context.unregisterProcessedObject(obj);
        }
    }

    @SuppressWarnings("unchecked")
    private JsonbSerializer<A> resolveSerializer(ObjectMarshaller ctx, A adapted) {
        final ContainerSerializerFactory cached = ctx.getMappingContext().getSerializerProvider(adapted.getClass());
        if (null != cached) {
            return (JsonbSerializer<A>) cached.getSerializer(new JsonbPropertyDescriptor().setWrapper(this).setRuntimeType(null == classModel ? null : classModel.getType()));
        }
        return (JsonbSerializer<A>) new TypeSerializerBuilder(ctx.getJsonbContext()).setObjectClass(adapted.getClass()).setCustomization(null == classModel ? null : classModel.getCustomization()).setWrapper(this).buildSerializer();
    }

    /**
     * Creates AdapterObjectSerializer.
     *
     * @param classModel Class model.
     * @param adapter    Adapter.
     */
    public AdaptedObjectSerializer(ClassDescriptor classModel, AdapterBinding adapter) {
        this.classModel = classModel;
        this.adapterInfo = adapter;
    }

}

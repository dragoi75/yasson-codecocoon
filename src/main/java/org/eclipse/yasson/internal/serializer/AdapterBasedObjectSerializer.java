/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.ProcessingContext;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.model.JsonbPropertyInfo;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageConstants;

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
public class AdapterBasedObjectSerializer<T, A> implements CurrentItem<T>, JsonbSerializer<T> {

    private final ClassModel classDescriptor;

    private final TypeAdapterBinding binding;

    /**
     * Creates AdapterObjectSerializer.
     *
     * @param classDescriptor Class model.
     * @param binding    Adapter.
     */
    public AdapterBasedObjectSerializer(ClassModel classDescriptor, TypeAdapterBinding binding) {
        this.classDescriptor = classDescriptor;
        this.binding = binding;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(T value, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        ProcessingContext processingState = (ProcessingContext) serializationContext;
        try {
            if (processingState.addProcessedObject(value)) {
                final JsonbAdapter<T, A> binding = (JsonbAdapter<T, A>) this.binding.getAdapter();
                A converted = binding.adaptToJson(value);
                if (converted == null) {
                    jsonWriter.writeNull();
                    return;
                }
                final JsonbSerializer<A> handler = findSerializer((Marshaller) serializationContext, converted);
                handler.serialize(converted, jsonWriter, serializationContext);
            } else {
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.RECURSIVE_REFERENCE, value.getClass()));
            }
        } catch (Exception ex) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.ADAPTER_EXCEPTION, binding.getBindingType(), binding.getToType(), binding.getAdapter().getClass()), ex);
        } finally {
            processingState.removeProcessedObject(value);
        }
    }

    @SuppressWarnings("unchecked")
    private JsonbSerializer<A> findSerializer(Marshaller serializationContext, A converted) {
        final ContainerSerializerProvider providerCache = serializationContext.getMappingContext().getSerializerProvider(converted.getClass());
        if (providerCache != null) {
            return (JsonbSerializer<A>) providerCache.provideSerializer(new JsonbPropertyInfo()
                    .withWrapper(this)
                    .withRuntimeType(classDescriptor == null ? null : classDescriptor.getType()));
        }
        return (JsonbSerializer<A>) new TypeSerializerBuilder(serializationContext.getJsonbContext())
                .setObjectClass(converted.getClass())
                .setCustomization(classDescriptor == null ? null : classDescriptor.getCustomization())
                .setWrapper(this)
                .buildSerializer();
    }

    @Override
    public ClassModel getClassModel() {
        return null;
    }

    @Override
    public CurrentItem<?> getWrapper() {
        return null;
    }

    @Override
    public Type getRuntimeType() {
        return null;
    }
}

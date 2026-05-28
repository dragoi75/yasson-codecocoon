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

import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.model.ClassModel;

import javax.json.bind.JsonbException;
import javax.json.bind.adapter.JsonbAdapter;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;

/**
 * Decorator for an item which builds adapted type instance by a {@link JsonbAdapter}.
 * After adapted item is finished building its instance is converted to field type object by calling components.
 *
 * @param <A> adapted type, type to deserialize JSON into
 * @param <T> required type, typically type of the field, which is adapted to another type
 */
public class AdapterAwareObjectDeserializer<A, T> implements CurrentItem<T>, JsonbDeserializer<T> {

    private JsonbDeserializer<A> adaptedDeserializer;

    private final TypeAdapterBinding adapterBinding;

    private final BaseContainerDeserializer<?> containerWrapper;

    @Override
    public Type getRuntimeType() {
        if (adaptedDeserializer instanceof BaseContainerDeserializer) {
            return ((BaseContainerDeserializer) adaptedDeserializer).getRuntimeType();
        }
        throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.INTERNAL_ERROR, "Deserialization propagation is not allowed for:" + adaptedDeserializer));
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(JsonParser jsonReader, DeserializationContext deserializationState, Type runtimeType) {
        try {
            final A deserializedValue =  adaptedDeserializer.deserialize(jsonReader, deserializationState, runtimeType);
            final T convertedValue = ((JsonbAdapter<T, A>) adapterBinding.getAdapter()).adaptFromJson(deserializedValue);
            return convertedValue;
        } catch (Exception ex) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.ADAPTER_EXCEPTION, adapterBinding.getBindingType(), adapterBinding.getToType(), adapterBinding.getAdapter().getClass()), ex);
        }
    }

    /**
     * Sets adapted item.
     *
     * @param adaptedDeserializer Adapted item to set.
     */
    public void setAdaptedTypeDeserializer(JsonbDeserializer<A> adaptedDeserializer) {
        this.adaptedDeserializer = adaptedDeserializer;
    }

    @Override
    public CurrentItem<?> getWrapper() {
        return containerWrapper;
    }

    @Override
    public ClassModel getClassModel() {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates decoration instance wrapping real adapted object item.
     *
     * @param adapterBinding components type info
     * @param containerWrapper wrapper item to get instance from
     */
    public AdapterAwareObjectDeserializer(TypeAdapterBinding adapterBinding, BaseContainerDeserializer<?> containerWrapper) {
        this.adapterBinding = adapterBinding;
        this.containerWrapper = containerWrapper;
    }

}

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

import org.eclipse.yasson.internal.components.AdapterBindingDescriptor;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;
import org.eclipse.yasson.internal.model.ClassDescriptor;

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
public class AdaptedTypeDeserializer<A, T> implements CurrentItemWrapper<T>, JsonbDeserializer<T> {

    private JsonbDeserializer<A> targetDeserializer;

    private final AdapterBindingDescriptor bindingDescriptor;

    private final BaseContainerDeserializer<?> containerDeserializer;

    /**
     * Sets adapted item.
     *
     * @param targetDeserializer Adapted item to set.
     */
    public void setAdaptedTypeDeserializer(JsonbDeserializer<A> targetDeserializer) {
        this.targetDeserializer = targetDeserializer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(JsonParser jsonReader, DeserializationContext deserializationCtx, Type runtimeType) {
        try {
            final A outputValue =  targetDeserializer.deserialize(jsonReader, deserializationCtx, runtimeType);
            final T convertedValue = ((JsonbAdapter<T, A>) bindingDescriptor.getAdapter()).adaptFromJson(outputValue);
            return convertedValue;
        } catch (Exception ex) {
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.ADAPTER_EXCEPTION, bindingDescriptor.getBindingType(), bindingDescriptor.getToType(), bindingDescriptor.getAdapter().getClass()), ex);
        }
    }

    @Override
    public Type getRuntimeType() {
        if (targetDeserializer instanceof BaseContainerDeserializer) {
            return ((BaseContainerDeserializer) targetDeserializer).getRuntimeType();
        }
        throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.INTERNAL_ERROR, "Deserialization propagation is not allowed for:" + targetDeserializer));
    }

    @Override
    public CurrentItemWrapper<?> getWrapper() {
        return containerDeserializer;
    }

    @Override
    public ClassDescriptor getClassModel() {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates decoration instance wrapping real adapted object item.
     *
     * @param bindingDescriptor components type info
     * @param containerDeserializer wrapper item to get instance from
     */
    public AdaptedTypeDeserializer(AdapterBindingDescriptor bindingDescriptor, BaseContainerDeserializer<?> containerDeserializer) {
        this.bindingDescriptor = bindingDescriptor;
        this.containerDeserializer = containerDeserializer;
    }

}

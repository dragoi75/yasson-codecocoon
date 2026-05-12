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

package org.eclipse.yasson.internal;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.deserializer.ModelUnmarshaller;
import org.eclipse.yasson.internal.model.customization.ClassCustomization;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Deserialization context implementation.
 */
public class DeserializationContextImplementation extends ProcessingContext implements DeserializationContext {
    private final List<Runnable> pendingActions = new ArrayList<>();
    private JsonParser.Event previousEvent;
    private Customization customConfig = ClassCustomization.empty();
    private Object currentObject;

    /**
     * Parent instance for marshaller and unmarshaller.
     *
     * @param jsonbCtx context of Jsonb
     */
    public DeserializationContextImplementation(JsonbContext jsonbCtx) {
        super(jsonbCtx);
    }

    /**
     * Create new instance based on previous context.
     *
     * @param deserState previous deserialization context
     */
    public DeserializationContextImplementation(DeserializationContextImplementation deserState) {
        super(deserState.getJsonbContext());
        this.previousEvent = deserState.previousEvent;
    }

    /**
     * Return instance of currently deserialized type.
     *
     * @return null if instance has not been created yet
     */
    public Object getInstance() {
        return currentObject;
    }

    /**
     * Set currently deserialized type instance.
     *
     * @param currentObject deserialized type instance
     */
    public void setInstance(Object currentObject) {
        this.currentObject = currentObject;
    }

    /**
     * Return the list of deferred deserializers.
     *
     * @return list of deferred deserializers
     */
    public List<Runnable> getDeferredDeserializers() {
        return pendingActions;
    }

    /**
     * Return last obtained {@link JsonParser.Event} event.
     *
     * @return last obtained event
     */
    public JsonParser.Event getLastValueEvent() {
        return previousEvent;
    }

    /**
     * Set last obtained {@link JsonParser.Event} event.
     *
     * @param previousEvent last obtained event
     */
    public void setLastValueEvent(JsonParser.Event previousEvent) {
        this.previousEvent = previousEvent;
    }

    /**
     * Return customization used by currently processed user defined deserializer.
     *
     * @return currently used customization
     */
    public Customization getCustomization() {
        return customConfig;
    }

    /**
     * Set customization used by currently processed user defined deserializer.
     *
     * @param customConfig currently used customization
     */
    public void setCustomization(Customization customConfig) {
        this.customConfig = customConfig;
    }

    @Override
    public <T> T deserialize(Class<T> targetClass, JsonParser jsonReader) {
        return deserializeValue(targetClass, jsonReader);
    }

    @Override
    public <T> T deserialize(Type targetDescriptor, JsonParser jsonReader) {
        return deserializeValue(targetDescriptor, jsonReader);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Type targetDescriptor, JsonParser jsonReader) {
        try {
            if (previousEvent == null) {
                previousEvent = jsonReader.next();
                validateState();
            }
            ModelUnmarshaller<JsonParser> modelDeserializer = getJsonbContext().getChainModelCreator().deserializerChain(targetDescriptor);
            return (T) modelDeserializer.unmarshal(jsonReader, this);
        } catch (JsonbException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INTERNAL_ERROR, e.getMessage()), e);
        }
    }

    private void validateState() {
        if (previousEvent == JsonParser.Event.KEY_NAME) {
            throw new JsonbException("JsonParser has incorrect position as the first event: KEY_NAME");
        }
    }

}

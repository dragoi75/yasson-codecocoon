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
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Deserialization context implementation.
 */
public class DeserializationContextManager extends ProcessingContext implements DeserializationContext {
    private final List<Runnable> deferredTasks = new ArrayList<>();
    private JsonParser.Event latestEvent;
    private Customization customSettings = ClassCustomization.empty();
    private Object currentObject;

    /**
     * Parent instance for marshaller and unmarshaller.
     *
     * @param jsonbState context of Jsonb
     */
    public DeserializationContextManager(JsonbContext jsonbState) {
        super(jsonbState);
    }

    /**
     * Create new instance based on previous context.
     *
     * @param managerRef previous deserialization context
     */
    public DeserializationContextManager(DeserializationContextManager managerRef) {
        super(managerRef.getJsonbContext());
        this.latestEvent = managerRef.latestEvent;
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
        return deferredTasks;
    }

    /**
     * Return last obtained {@link JsonParser.Event} event.
     *
     * @return last obtained event
     */
    public JsonParser.Event getLastValueEvent() {
        return latestEvent;
    }

    /**
     * Set last obtained {@link JsonParser.Event} event.
     *
     * @param latestEvent last obtained event
     */
    public void setLastValueEvent(JsonParser.Event latestEvent) {
        this.latestEvent = latestEvent;
    }

    /**
     * Return customization used by currently processed user defined deserializer.
     *
     * @return currently used customization
     */
    public Customization getCustomization() {
        return customSettings;
    }

    /**
     * Set customization used by currently processed user defined deserializer.
     *
     * @param customSettings currently used customization
     */
    public void setCustomization(Customization customSettings) {
        this.customSettings = customSettings;
    }

    @Override
    public <T> T deserialize(Class<T> targetClass, JsonParser jsonReader) {
        return deserializeValue(targetClass, jsonReader);
    }

    @Override
    public <T> T deserialize(Type valueType, JsonParser jsonReader) {
        return deserializeValue(valueType, jsonReader);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Type valueType, JsonParser jsonReader) {
        try {
            if (latestEvent == null) {
                latestEvent = jsonReader.next();
                validateState();
            }
            ModelUnmarshaller<JsonParser> unmarshaller = getJsonbContext().getChainModelCreator().deserializerChain(valueType);
            return (T) unmarshaller.unmarshal(jsonReader, this);
        } catch (JsonbException jsonbError) {
            throw jsonbError;
        } catch (RuntimeException jsonbError) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.INTERNAL_ERROR, jsonbError.getMessage()), jsonbError);
        }
    }

    private void validateState() {
        if (latestEvent == JsonParser.Event.KEY_NAME) {
            throw new JsonbException("JsonParser has incorrect position as the first event: KEY_NAME");
        }
    }

}

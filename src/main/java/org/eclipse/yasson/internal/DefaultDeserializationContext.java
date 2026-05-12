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
import java.util.logging.Logger;

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.deserializer.ModelParser;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.model.customization.SerializationCustomizer;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * Deserialization context implementation.
 */
public class DefaultDeserializationContext extends ProcessingScope implements DeserializationContext {

    private static final Logger LOG = Logger.getLogger(DefaultDeserializationContext.class.getName());

    private final List<Runnable> deferredSetters = new ArrayList<>();
    private JsonParser.Event finalValueEvent;
    private SerializationCustomizer serializationCustomizer = ClassSerializationConfig.emptyConfig();
    private Object targetObject;

    /**
     * Parent instance for marshaller and unmarshaller.
     *
     * @param binding context of Jsonb
     */
    public DefaultDeserializationContext(JsonBindingContext binding) {
        super(binding);
    }

    /**
     * Create new instance based on previous context.
     *
     * @param ctx previous deserialization context
     */
    public DefaultDeserializationContext(DefaultDeserializationContext ctx) {
        super(ctx.getJsonbContext());
        this.finalValueEvent = ctx.finalValueEvent;
    }

    /**
     * Return instance of currently deserialized type.
     *
     * @return null if instance has not been created yet
     */
    public Object getInstance() {
        return targetObject;
    }

    /**
     * Set currently deserialized type instance.
     *
     * @param targetObject deserialized type instance
     */
    public void setInstance(Object targetObject) {
        this.targetObject = targetObject;
    }

    /**
     * Return the list of deferred deserializers.
     *
     * @return list of deferred deserializers
     */
    public List<Runnable> getDeferredDeserializers() {
        return deferredSetters;
    }

    /**
     * Return last obtained {@link JsonParser.Event} event.
     *
     * @return last obtained event
     */
    public JsonParser.Event getLastValueEvent() {
        return finalValueEvent;
    }

    /**
     * Set last obtained {@link JsonParser.Event} event.
     *
     * @param finalValueEvent last obtained event
     */
    public void setLastValueEvent(JsonParser.Event finalValueEvent) {
        this.finalValueEvent = finalValueEvent;
    }

    /**
     * Return customization used by currently processed user defined deserializer.
     *
     * @return currently used customization
     */
    public SerializationCustomizer getCustomization() {
        return serializationCustomizer;
    }

    /**
     * Set customization used by currently processed user defined deserializer.
     *
     * @param serializationCustomizer currently used customization
     */
    public void setCustomization(SerializationCustomizer serializationCustomizer) {
        this.serializationCustomizer = serializationCustomizer;
    }

    @Override
    public <T> T deserialize(Class<T> targetClass, JsonParser jsonReader) {
        return deserializeValue(targetClass, jsonReader);
    }

    @Override
    public <T> T deserialize(Type target, JsonParser jsonReader) {
        return deserializeValue(target, jsonReader);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Type target, JsonParser jsonReader) {
        try {
            if (finalValueEvent == null) {
                finalValueEvent = jsonReader.next();
                validateState();
            }
            ModelParser<JsonParser> modelParser = getJsonbContext().getChainModelCreator().createDeserializerChain(target);
            return (T) modelParser.deserializeModel(jsonReader, this);
        } catch (JsonbException ex) {
            LOG.severe(ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            LOG.severe(ex.getMessage());
            throw new JsonbException(MessageProvider.getMessage(MessageConstants.INTERNAL_ERROR, ex.getMessage()), ex);
        }
    }

    private void validateState() {
        if (finalValueEvent == JsonParser.Event.KEY_NAME) {
            throw new JsonbException("JsonParser has incorrect position as the first event: KEY_NAME");
        }
    }

}

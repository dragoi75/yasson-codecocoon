/*
 * Copyright (c) 2015, 2022 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2020 Payara Foundation and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerationException;
import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;
import org.eclipse.yasson.internal.serializer.ModelMarshaller;

/**
 * JSONB marshaller. Created each time marshalling operation called.
 */
public class DefaultSerializationContext extends ProcessingContext implements SerializationContext {

    private static final Logger SERIALIZATION_LOG = Logger.getLogger(DefaultSerializationContext.class.getName());

    /**
     * Used to avoid StackOverflowError, when adapted / serialized object
     * contains instance of its type inside it or when object has recursive reference.
     */
    private final Set<Object> processingObjects = new HashSet<>();

    private final Type effectiveType;
    private String fieldName = null;
    private boolean containsNulls = true;
    private boolean isTopLevel = true;

    /**
     * Creates Marshaller for generation to String.
     *
     * @param bindingContext    Current context.
     * @param baseRuntimeType Type of root object.
     */
    public DefaultSerializationContext(JsonbContext bindingContext, Type baseRuntimeType) {
        super(bindingContext);
        this.effectiveType = baseRuntimeType;
    }

    /**
     * Creates Marshaller for generation to String.
     *
     * @param bindingContext Current context.
     */
    public DefaultSerializationContext(JsonbContext bindingContext) {
        this(bindingContext, null);
    }

    /**
     * Set new current property key name.
     *
     * @param fieldName key name
     */
    public void setKey(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * Current property key name.
     *
     * @return current property key name
     */
    public String getKey() {
        return fieldName;
    }

    /**
     * Serialized value is a root value.
     *
     * @return is root value
     */
    public boolean isRoot() {
        return isTopLevel;
    }

    /**
     * Set whether serialized value is root value.
     *
     * @param isTopLevel is root value
     */
    public void setRoot(boolean isTopLevel) {
        this.isTopLevel = isTopLevel;
    }

    /**
     * Value from this property is only used in {@link org.eclipse.yasson.internal.serializer.NullSerializer}.
     * It should not be used anywhere else.
     *
     * @return if container supports nulls
     */
    public boolean isContainerWithNulls() {
        return containsNulls;
    }

    /**
     * Set if container supports null values.
     *
     * @param allowNulls should write nulls in container
     */
    public void setContainerWithNulls(boolean allowNulls) {
        this.containsNulls = allowNulls;
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     *
     * @param value        object to marshall
     * @param generator generator to use
     * @param shouldClose         if generator should be closed
     */
    public void serializeToJson(Object value, JsonGenerator generator, boolean shouldClose) {
        try {
            serializeRootObject(value, generator);
        } catch (JsonbException jsonbException) {
            throw jsonbException;
        } catch (RuntimeException jsonbException) {
            throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR, jsonbException.getMessage()), jsonbException);
        } finally {
            try {
                if (shouldClose) {
                    generator.close();
                } else {
                    generator.flush();
                }
            } catch (JsonGenerationException jsonGenerationEx) {
                SERIALIZATION_LOG.severe(jsonGenerationEx.getMessage());
            }
        }
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     * Closes the generator on completion.
     *
     * @param value        object to marshall
     * @param generator generator to use
     */
    public void serializeToJson(Object value, JsonGenerator generator) {
        serializeToJson(value, generator, true);
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     * Leaves generator open for further interaction after completion.
     *
     * @param value        object to marshall
     * @param generator generator to use
     */
    public void marshallNoClose(Object value, JsonGenerator generator) {
        serializeToJson(value, generator, false);
    }

    @Override
    public <T> void serialize(String fieldName, T value, JsonGenerator gen) {
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(value);
        setKey(fieldName);
        serializeRootObject(value, gen);
    }

    @Override
    public <T> void serialize(T value, JsonGenerator gen) {
        Objects.requireNonNull(value);
        serializeRootObject(value, gen);
    }

    /**
     * Serializes root element.
     *
     * @param <T>       Root type
     * @param isTopLevel      Root.
     * @param gen JSON generator.
     */
    public <T> void serializeRootObject(T isTopLevel, JsonGenerator gen) {
        Type resolvedType = resolveSerializationType(isTopLevel);
        final ModelMarshaller primaryMarshaller = getRootSerializer(resolvedType);
        primaryMarshaller.marshal(isTopLevel, gen, this);
    }

    private <T> Type resolveSerializationType(T isTopLevel) {
        if (isRoot() && effectiveType != null) {
            return effectiveType;
        }
        return isTopLevel == null ? Object.class : isTopLevel.getClass();
    }

    public ModelMarshaller getRootSerializer(Type resolvedType) {
        return getJsonbContext().getSerializationModelCreator().serializerChain(resolvedType, true, true);
    }

    /**
     * Adds currently processed object to the {@link Set}.
     *
     * @param value processed object
     * @return if object was added
     */
    public boolean addProcessedObject(Object value) {
        return this.processingObjects.add(value);
    }

    /**
     * Removes processed object from the {@link Set}.
     *
     * @param value processed object
     * @return if object was removed
     */
    public boolean removeProcessedObject(Object value) {
        return processingObjects.remove(value);
    }


}

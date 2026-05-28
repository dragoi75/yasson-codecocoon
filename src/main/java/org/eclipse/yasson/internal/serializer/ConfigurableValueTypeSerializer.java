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
import org.eclipse.yasson.internal.model.customization.Customization;

import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;

/**
 * Common type for all supported type serializers.
 *
 * @author Roman Grigoriadi
 */
public abstract class ConfigurableValueTypeSerializer<T> implements JsonbSerializer<T> {

    protected final Customization customization;

    /**
     * Serializes an object to JSON.
     *
     * @param value Object to serialize.
     * @param jsonWriter JSON generator to use.
     * @param serializationContext JSON-B mapper context.
     */
    @Override
    public void serialize(T value, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        Marshaller marshallingService = (Marshaller) serializationContext;
        serializeValue(value, jsonWriter, marshallingService);
    }

    protected abstract void serializeValue(T obj, JsonGenerator generator, Marshaller marshaller);

    /**
     * Creates a new instance.
     *
     * @param configOptions Model customization.
     */
    public ConfigurableValueTypeSerializer(Customization configOptions) {
        this.customization = configOptions;
    }

}

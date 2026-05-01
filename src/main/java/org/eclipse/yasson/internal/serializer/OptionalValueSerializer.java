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

import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.ProcessingContext;
import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.model.customization.Customization;

import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Common serializer logic for java Optionals.
 *
 * @author Roman Grigoriadi
 * @param <T> instantiated Optional type
 */
public class OptionalValueSerializer<T extends Optional<?>> implements CurrentItem<T>, JsonbSerializer<T> {
    private final Customization serializerSettings;

    private final CurrentItem<?> currentEntry;

    private final Type innerType;

    /**
     * Creates a new instance.
     *
     * @param serializerFactory Builder to initialize the instance.
     */
    public OptionalValueSerializer(SerializerBuilder serializerFactory) {
        this.currentEntry = serializerFactory.getWrapper();
        this.serializerSettings = serializerFactory.getCustomization();
        this.innerType = getOptionalTypeArgument(serializerFactory.getRuntimeType());
    }

    private Type getOptionalTypeArgument(Type actualType) {
        if (actualType instanceof ParameterizedType) {
            return ((ParameterizedType) actualType).getActualTypeArguments()[0];
        }
        return Object.class;
    }

    @Override
    public ClassModel getClassModel() {
        return null;
    }

    @Override
    public CurrentItem<?> getWrapper() {
        return currentEntry;
    }

    @Override
    public Type getRuntimeType() {
        return innerType;
    }

    public Customization getCustomization() {
        return serializerSettings;
    }

    @Override
    public void serialize(T value, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        JsonbContext jsonbEnv = ((ProcessingContext) serializationContext).getJsonbContext();
        if (value == null || !value.isPresent()) {
            if (!serializerSettings.isNillable()) {
                return;
            }
            jsonWriter.writeNull();
            return;
        }
        Object containedValue = value.get();
        final JsonbSerializer<?> jsonbHandler = new SerializerBuilder(jsonbEnv).withObjectClass(containedValue.getClass())
                .withType(innerType).withWrapper(currentEntry).withCustomization(serializerSettings).build();
        serialCaptor(jsonbHandler, containedValue, jsonWriter, serializationContext);
    }

    @SuppressWarnings("unchecked")
    private <T> void serialCaptor(JsonbSerializer<?> jsonbHandler, T value, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        ((JsonbSerializer<T>) jsonbHandler).serialize(value, jsonWriter, serializationContext);
    }
}

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

import org.eclipse.yasson.internal.JsonbRuntimeContext;
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

    private final Customization config;

    private final CurrentItem<?> currentItemRef;

    private final Type containedType;

    /**
     * Creates a new instance.
     *
     * @param serializerFactory Builder to initialize the instance.
     */
    public OptionalValueSerializer(TypeSerializerBuilder serializerFactory) {
        this.currentItemRef = serializerFactory.getWrapper();
        this.config = serializerFactory.getCustomization();
        this.containedType = extractOptionalType(serializerFactory.getRuntimeType());
    }

    private Type extractOptionalType(Type actualType) {
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
        return currentItemRef;
    }

    @Override
    public Type getRuntimeType() {
        return containedType;
    }

    public Customization getCustomization() {
        return config;
    }

    @Override
    public void serialize(T value, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        JsonbRuntimeContext jsonbRuntime = ((ProcessingContext) serializationContext).getJsonbContext();
        if (null == value || !value.isPresent()) {
            if (!config.isNillable()) {
                return;
            }
            jsonWriter.writeNull();
            return;
        }
        Object valueObject = value.get();
        final JsonbSerializer<?> jsonbMarshaller = new TypeSerializerBuilder(jsonbRuntime).setObjectClass(valueObject.getClass()).setType(containedType).setWrapper(currentItemRef).setCustomization(config).buildSerializer();
        invokeSerializer(jsonbMarshaller, valueObject, jsonWriter, serializationContext);
    }

    @SuppressWarnings("unchecked")
    private <T> void invokeSerializer(JsonbSerializer<?> jsonbMarshaller, T value, JsonGenerator jsonWriter, SerializationContext serializationEnv) {
        ((JsonbSerializer<T>) jsonbMarshaller).serialize(value, jsonWriter, serializationEnv);
    }
}

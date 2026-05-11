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
import org.eclipse.yasson.internal.ProcessingEnvironment;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;
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
public class OptionalValueSerializer<T extends Optional<?>> implements CurrentItemWrapper<T>, JsonbSerializer<T> {

    private final SerializationCustomization serializationConfig;

    private final CurrentItemWrapper<?> currentItemRef;

    private final Type elementType;

    /**
     * Creates a new instance.
     *
     * @param serializationFactory Builder to initialize the instance.
     */
    public OptionalValueSerializer(SerializationBuilder serializationFactory) {
        this.currentItemRef = serializationFactory.getWrapper();
        this.serializationConfig = serializationFactory.getCustomization();
        this.elementType = resolveOptionalElementType(serializationFactory.getRuntimeType());
    }

    private Type resolveOptionalElementType(Type actualType) {
        if (actualType instanceof ParameterizedType) {
            return ((ParameterizedType) actualType).getActualTypeArguments()[0];
        }
        return Object.class;
    }

    @Override
    public ClassDescriptor getClassModel() {
        return null;
    }

    @Override
    public CurrentItemWrapper<?> getWrapper() {
        return currentItemRef;
    }

    @Override
    public Type getRuntimeType() {
        return elementType;
    }

    public SerializationCustomization getCustomization() {
        return serializationConfig;
    }

    @Override
    public void serialize(T value, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        JsonbRuntimeContext runtimeContext = ((ProcessingEnvironment) serializationContext).getJsonbContext();
        if (null == value || !value.isPresent()) {
            if (!serializationConfig.isNillable()) {
                return;
            }
            jsonWriter.writeNull();
            return;
        }
        Object presentValue = value.get();
        final JsonbSerializer<?> valueAdapter = new SerializationBuilder(runtimeContext).setObjectClass(presentValue.getClass()).setType(elementType).setWrapper(currentItemRef).setCustomization(serializationConfig).buildSerializer();
        delegateToSerializer(valueAdapter, presentValue, jsonWriter, serializationContext);
    }

    @SuppressWarnings("unchecked")
    private <T> void delegateToSerializer(JsonbSerializer<?> valueAdapter, T value, JsonGenerator jsonWriter, SerializationContext serializationState) {
        ((JsonbSerializer<T>) valueAdapter).serialize(value, jsonWriter, serializationState);
    }
}

/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
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
package org.eclipse.yasson.internal.serializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Predicate;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.JsonbMarshaller;
import org.eclipse.yasson.internal.ObjectProcessingContext;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Common serializer logic for java Optionals.
 *
 * @param <T> instantiated Optional type
 */
public class OptionalObjectSerializer<T extends Optional<?>> implements CurrentItem<T>, JsonbSerializer<T> {

    private final Customization customization;

    private final CurrentItem<?> wrapper;

    private final Type optionalValueType;

    @Override
    public void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
        JsonbRuntimeContext jsonbContext = ((ObjectProcessingContext) ctx).getJsonbContext();
        if (handleEmpty(obj, Optional::isPresent, customization, generator, (JsonbMarshaller) ctx)) {
            return;
        }
        Object optionalValue = obj.get();
        final JsonbSerializer<?> serializer = new TypeSerializerBuilder(jsonbContext).setObjectClass(optionalValue.getClass()).setType(optionalValueType).setWrapper(wrapper).setCustomization(customization).buildSerializer();
        serialCaptor(serializer, optionalValue, generator, ctx);
    }

    static <T> boolean handleEmpty(T value, Predicate<T> presentCheck, Customization customization, JsonGenerator generator, JsonbMarshaller marshaller) {
        if (null != value && presentCheck.test(value)) {
            return false;
        } else {
            if (null == customization) {
                marshaller.getJsonbContext().getConfigProperties().getNullSerializer().serialize(value, generator, marshaller);
            } else {
                if (customization.isNillable()) {
                    generator.writeNull();
                    return true;
                }
            }
            return true;
        }
    }

    public Customization getCustomization() {
        return customization;
    }

    @SuppressWarnings("unchecked")
    private <T> void serialCaptor(JsonbSerializer<?> serializer, T object, JsonGenerator generator, SerializationContext context) {
        ((JsonbSerializer<T>) serializer).serialize(object, generator, context);
    }

    /**
     * Creates a new instance.
     *
     * @param builder Builder to initialize the instance.
     */
    public OptionalObjectSerializer(TypeSerializerBuilder builder) {
        this.wrapper = builder.getWrapper();
        this.customization = builder.getCustomization();
        this.optionalValueType = resolveOptionalType(builder.getRuntimeType());
    }

    @Override
    public ClassDescriptor getClassModel() {
        return null;
    }

    @Override
    public Type getRuntimeType() {
        return optionalValueType;
    }

    private Type resolveOptionalType(Type runtimeType) {
        if (runtimeType instanceof ParameterizedType) {
            return ((ParameterizedType) runtimeType).getActualTypeArguments()[0];
        }
        return Object.class;
    }

    @Override
    public CurrentItem<?> getWrapper() {
        return wrapper;
    }

}

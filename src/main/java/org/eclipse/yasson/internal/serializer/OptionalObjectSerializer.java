/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 *  Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.ObjectMarshaller;
import org.eclipse.yasson.internal.ObjectProcessingContext;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Common serializer logic for java Optionals.
 *
 * @author Roman Grigoriadi
 * @param <T> instantiated Optional type
 */
public class OptionalObjectSerializer<T extends Optional<?>> implements ActiveItemModel<T>, JsonbSerializer<T> {

    private final SerializationCustomization customization;

    private final ActiveItemModel<?> wrapper;

    private final Type optionalValueType;

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

    private Type resolveOptionalType(Type runtimeType) {
        if (runtimeType instanceof ParameterizedType) {
            return ((ParameterizedType) runtimeType).getActualTypeArguments()[0];
        }
        return Object.class;
    }

    @Override
    public ClassDescriptor getClassModel() {
        return null;
    }

    @Override
    public ActiveItemModel<?> getWrapper() {
        return wrapper;
    }

    @Override
    public Type getRuntimeType() {
        return optionalValueType;
    }

    public SerializationCustomization getCustomization() {
        return customization;
    }

    @Override
    public void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
        JsonbContext jsonbContext = ((ObjectProcessingContext) ctx).getJsonbContext();
        if (handleEmpty(obj, Optional::isPresent, customization, generator, (ObjectMarshaller) ctx)) {
            return;
        }
        Object optionalValue = obj.get();
        final JsonbSerializer<?> serializer = new TypeSerializerBuilder(jsonbContext).setObjectClass(optionalValue.getClass()).setType(optionalValueType).setWrapper(wrapper).setCustomization(customization).buildSerializer();
        serialCaptor(serializer, optionalValue, generator, ctx);
    }

    static <T> boolean handleEmpty(T value, Predicate<T> presentCheck, SerializationCustomization customization, JsonGenerator generator, ObjectMarshaller marshaller) {
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

    @SuppressWarnings("unchecked")
    private <T> void serialCaptor(JsonbSerializer<?> serializer, T object, JsonGenerator generator, SerializationContext context) {
        ((JsonbSerializer<T>) serializer).serialize(object, generator, context);
    }
}

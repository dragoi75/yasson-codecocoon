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

import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.ObjectProcessingContext;
import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.model.customization.Customization;
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
public class OptionalObjectSerializer<T extends Optional<?>> implements CurrentItem<T>, JsonbSerializer<T> {

    private final Customization customization;

    private final CurrentItem<?> wrapper;

    private final Type optionalValueType;

    @SuppressWarnings("unchecked")
    private <T> void serialCaptor(JsonbSerializer<?> serializer, T object, JsonGenerator generator, SerializationContext context) {
        ((JsonbSerializer<T>) serializer).serialize(object, generator, context);
    }

    static <T> boolean handleEmpty(T value, Predicate<T> presentCheck, Customization customization, JsonGenerator generator, Marshaller marshaller) {
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

    @Override
    public ClassModel getClassModel() {
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

    /**
     * Creates a new instance.
     *
     * @param builder Builder to initialize the instance.
     */
    public OptionalObjectSerializer(SerializerBuilder builder) {
        this.wrapper = builder.getWrapper();
        this.customization = builder.getCustomization();
        this.optionalValueType = resolveOptionalType(builder.getRuntimeType());
    }

    @Override
    public void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
        JsonbRuntimeContext jsonbContext = ((ObjectProcessingContext) ctx).getJsonbContext();
        if (handleEmpty(obj, Optional::isPresent, customization, generator, (Marshaller) ctx)) {
            return;
        }
        Object optionalValue = obj.get();
        final JsonbSerializer<?> serializer = new SerializerBuilder(jsonbContext).withObjectClass(optionalValue.getClass()).setType(optionalValueType).setWrapper(wrapper).setCustomization(customization).build();
        serialCaptor(serializer, optionalValue, generator, ctx);
    }

    public Customization getCustomization() {
        return customization;
    }

    @Override
    public CurrentItem<?> getWrapper() {
        return wrapper;
    }

}

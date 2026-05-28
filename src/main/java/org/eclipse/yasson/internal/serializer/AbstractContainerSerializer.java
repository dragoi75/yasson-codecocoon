/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.Optional;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.ReflectionHelper;
import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.model.customization.ClassCustomizationBuilder;
import org.eclipse.yasson.internal.model.customization.ContainerCustomization;

/**
 * Base class for container serializers (list, array, etc.).
 *
 * @param <T> container value type
 */
public abstract class AbstractContainerSerializer<T> extends AbstractWrappedItem<T> implements JsonbSerializer<T> {

    private JsonbSerializer<?> valueSerializer;

    private Class<?> valueClass;

    /**
     * Serializes container object item.
     *
     * @param serializer serializer of the object
     * @param object     object to serialize
     * @param generator  json generator
     * @param ctx        context
     * @param <X>        type of object
     */
    @SuppressWarnings("unchecked")
    protected <X> void serializerCaptor(JsonbSerializer<?> serializer, X object, JsonGenerator generator, SerializationContext ctx) {
        ((JsonbSerializer<X>) serializer).serialize(object, generator, ctx);
    }

    @Override
    public final void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
        beforeSerialize(obj);
        writeStart(generator);
        serializeInternal(obj, generator, ctx);
        writeEnd(generator);
    }

    /**
     * Writes end of an object or an array.
     *
     * @param generator JSON format generator
     */
    protected void writeEnd(JsonGenerator generator) {
        generator.writeEnd();
    }

    /**
     * Value type of the container.
     *
     * @param valueType value type
     * @return raw value type
     */
    protected Type getValueType(Type valueType) {
        if (valueType instanceof ParameterizedType) {
            Optional<Type> runtimeTypeOptional = ReflectionHelper.getOptionalType(this, ((ParameterizedType) valueType).getActualTypeArguments()[0]);
            return runtimeTypeOptional.orElse(Object.class);
        }
        return Object.class;
    }

    /**
     * Serialize content of provided container.
     *
     * @param obj       container to be serialized
     * @param generator JSON format generator
     * @param ctx       JSON serialization context
     */
    protected abstract void serializeInternal(T obj, JsonGenerator generator, SerializationContext ctx);

    /**
     * Return last used serializer if last value class matches.
     *
     * @param valueClass class of the serialized object
     * @return cached serializer or null
     */
    protected JsonbSerializer<?> getValueSerializer(Class<?> valueClass) {
        if (null != valueSerializer && this.valueClass == valueClass) {
            return valueSerializer;
        }
        return null;
    }

    /**
     * Write start of an object or an array with a key.
     *
     * @param key       JSON key name.
     * @param generator JSON format generator
     */
    protected abstract void writeStart(String key, JsonGenerator generator);

    /**
     * Process container before serialization begins.
     * Does nothing by default.
     *
     * @param obj item to be serialized
     */
    protected void beforeSerialize(T obj) {
    }

    /**
     * Creates a new instance.
     *
     * @param wrapper     Item to serialize.
     * @param runtimeType Runtime type of the item.
     * @param classModel  Class model.
     */
    public AbstractContainerSerializer(CurrentItem<?> wrapper, Type runtimeType, ClassModel classModel) {
        super(wrapper, runtimeType, classModel);
    }

    /**
     * Serializes container object.
     *
     * @param item      container
     * @param generator json generator
     * @param ctx       context
     */
    protected void serializeItem(Object item, JsonGenerator generator, SerializationContext ctx) {
        if (null == item) {
            generator.writeNull();
            return;
        }
        Class<?> itemClass = item.getClass();
        //Not null when generic type is present or previous item is of same type
        JsonbSerializer<?> serializer = getValueSerializer(itemClass);
        //Raw collections + lost generic information
        if (null == serializer) {
            Type instanceValueType = getValueType(getRuntimeType());
            instanceValueType = instanceValueType.equals(Object.class) ? itemClass : instanceValueType;
            SerializerBuilder builder = new SerializerBuilder(((Marshaller) ctx).getJsonbContext());
            builder.withObjectClass(itemClass);
            builder.setWrapper(this);
            builder.setType(instanceValueType);
            if (DefaultSerializers.getInstance().isKnownType(itemClass)) {
                //Still need to override isNillable to true with ContainerCustomization for all serializers
                //to preserve collections and array null elements
                builder.setCustomization(new ContainerCustomization(new ClassCustomizationBuilder()));
            } else {
                //Need for class level annotations + user adapters/serializers bound to type
                ClassModel classModel = ((Marshaller) ctx).getJsonbContext().getMappingContext().getOrCreateClassModel(itemClass);
                builder.setCustomization(new ContainerCustomization(classModel.getClassCustomization()));
            }
            serializer = builder.build();
            //Cache last used value serializer in case of next item is the same type.
            addValueSerializer(serializer, itemClass);
        }
        serializerCaptor(serializer, item, generator, ctx);
    }

    /**
     * Cache a serializer and serialized object class for next use.
     *
     * @param valueSerializer serializer
     * @param valueClass      class of serializer object
     */
    protected void addValueSerializer(JsonbSerializer<?> valueSerializer, Class<?> valueClass) {
        Objects.requireNonNull(valueSerializer);
        Objects.requireNonNull(valueClass);
        this.valueSerializer = valueSerializer;
        this.valueClass = valueClass;
    }

    /**
     * Create instance of current item with its builder.
     *
     * @param builder {@link SerializerBuilder} used to build this instance
     */
    protected AbstractContainerSerializer(SerializerBuilder builder) {
        super(builder);
    }

    /**
     * Write start of an object or an array without a key.
     *
     * @param generator JSON format generator
     */
    protected abstract void writeStart(JsonGenerator generator);

}

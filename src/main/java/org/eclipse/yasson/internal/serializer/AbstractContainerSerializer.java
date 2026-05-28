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

import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.customization.ClassCustomizationBuilder;
import org.eclipse.yasson.internal.model.customization.ContainerCustomization;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

/**
 * Base class for container serializers (list, array, etc.).
 *
 * @author Roman Grigoriadi
 */
public abstract class AbstractContainerSerializer<T> extends BaseItem<T> implements JsonbSerializer<T> {

    private JsonbSerializer<?> valueSerializer;

    private Class<?> valueClass;

    /**
     * Write start object or start array with key.
     *
     * @param key JSON key name.
     * @param generator JSON generator.
     */
    protected abstract void writeStart(String key, JsonGenerator generator);

    /**
     * Cache a serializer and serialized object class for next use.
     * @param valueSerializer serializer
     * @param valueClass class of serializer object
     */
    protected void addValueSerializer(JsonbSerializer<?> valueSerializer, Class<?> valueClass) {
        Objects.requireNonNull(valueSerializer);
        Objects.requireNonNull(valueClass);
        this.valueSerializer = valueSerializer;
        this.valueClass = valueClass;
    }

    /**
     * Return last used serializer if last value class matches.
     * @param valueClass class of the serialized object
     * @return cached serializer or null
     */
    protected JsonbSerializer<?> getValueSerializer(Class<?> valueClass) {
        if (null != valueSerializer && this.valueClass == valueClass) {
            return valueSerializer;
        }
        return null;
    }

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
            if (DefaultSerializerRegistry.getInstance().isKnownType(itemClass)) {
                //Still need to override isNillable to true with ContainerCustomization for all serializers
                //to preserve collections and array null elements
                builder.setCustomization(new ContainerCustomization(new ClassCustomizationBuilder()));
            } else {
                //Need for class level annotations + user adapters/serializers bound to type
                ClassDescriptor classModel = ((Marshaller) ctx).getJsonbContext().getMappingContext().getOrCreateClassModel(itemClass);
                builder.setCustomization(new ContainerCustomization(classModel.getCustomization()));
            }
            serializer = builder.build();
            //Cache last used value serializer in case of next item is the same type.
            addValueSerializer(serializer, itemClass);
        }
        serializerCaptor(serializer, item, generator, ctx);
    }

    /**
     * Creates a new instance.
     *
     * @param wrapper Item to serialize.
     * @param runtimeType Runtime type of the item.
     * @param classModel Class model.
     */
    public AbstractContainerSerializer(CurrentItem<?> wrapper, Type runtimeType, ClassDescriptor classModel) {
        super(wrapper, runtimeType, classModel);
    }

    @Override
    public final void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
        writeStart(generator);
        serializeInternal(obj, generator, ctx);
        writeEnd(generator);
    }

    @SuppressWarnings("unchecked")
    protected <X> void serializerCaptor(JsonbSerializer<?> serializer, X object, JsonGenerator generator, SerializationContext ctx) {
        ((JsonbSerializer<X>) serializer).serialize(object, generator, ctx);
    }

    protected abstract void serializeInternal(T obj, JsonGenerator generator, SerializationContext ctx);

    protected Type getValueType(Type valueType) {
        if (valueType instanceof ParameterizedType) {
            Optional<Type> runtimeTypeOptional = ReflectionTypeResolver.resolveTypeOptional(this, ((ParameterizedType) valueType).getActualTypeArguments()[0]);
            return runtimeTypeOptional.orElse(Object.class);
        }
        return Object.class;
    }

    /**
     * Write start object or start array without a key.
     *
     * @param generator JSON generator.
     */
    protected abstract void writeStart(JsonGenerator generator);

    /**
     * Writes end for object or array.
     *
     * @param generator JSON generator.
     */
    protected void writeEnd(JsonGenerator generator) {
        generator.writeEnd();
    }

    /**
     * Create instance of current item with its builder.
     *
     * @param builder {@link SerializerBuilder} used to build this instance
     */
    protected AbstractContainerSerializer(SerializerBuilder builder) {
        super(builder);
    }

}

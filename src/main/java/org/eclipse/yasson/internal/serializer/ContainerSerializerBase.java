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

import org.eclipse.yasson.internal.ObjectMarshaller;
import org.eclipse.yasson.internal.ReflectiveTypeResolver;
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
public abstract class ContainerSerializerBase<T> extends AbstractItem<T> implements JsonbSerializer<T> {

    private JsonbSerializer<?> elementSerializer;

    private Class<?> elementClass;

    /**
     * Create instance of current item with its builder.
     *
     * @param typeFactory {@link TypeSerializerBuilder} used to build this instance
     */
    protected ContainerSerializerBase(TypeSerializerBuilder typeFactory) {
        super(typeFactory);
    }

    /**
     * Creates a new instance.
     *
     * @param activeModel Item to serialize.
     * @param actualType Runtime type of the item.
     * @param typeDescriptor Class model.
     */
    public ContainerSerializerBase(ActiveItemModel<?> activeModel, Type actualType, ClassDescriptor typeDescriptor) {
        super(activeModel, actualType, typeDescriptor);
    }

    @Override
    public final void serialize(T value, JsonGenerator jsonGenerator, SerializationContext context) {
        writeBegin(jsonGenerator);
        serializeContents(value, jsonGenerator, context);
        writeClose(jsonGenerator);
    }

    protected abstract void serializeContents(T obj, JsonGenerator generator, SerializationContext ctx);

    /**
     * Write start object or start array without a key.
     *
     * @param jsonGenerator JSON generator.
     */
    protected abstract void writeBegin(JsonGenerator jsonGenerator);

    /**
     * Writes end for object or array.
     *
     * @param jsonGenerator JSON generator.
     */
    protected void writeClose(JsonGenerator jsonGenerator) {
        jsonGenerator.writeEnd();
    }

    /**
     * Write start object or start array with key.
     *
     * @param fieldName JSON key name.
     * @param jsonGenerator JSON generator.
     */
    protected abstract void writeBegin(String fieldName, JsonGenerator jsonGenerator);

    @SuppressWarnings("unchecked")
    protected <X> void invokeSerializer(JsonbSerializer<?> delegateSerializer, X target, JsonGenerator jsonGenerator, SerializationContext context) {
        ((JsonbSerializer<X>) delegateSerializer).serialize(target, jsonGenerator, context);
    }

    /**
     * Return last used serializer if last value class matches.
     * @param elementClass class of the serialized object
     * @return cached serializer or null
     */
    protected JsonbSerializer<?> getValueSerializer(Class<?> elementClass) {
        if (null != elementSerializer && this.elementClass == elementClass) {
            return elementSerializer;
        }
        return null;
    }

    /**
     * Cache a serializer and serialized object class for next use.
     * @param elementSerializer serializer
     * @param elementClass class of serializer object
     */
    protected void setValueSerializer(JsonbSerializer<?> elementSerializer, Class<?> elementClass) {
        Objects.requireNonNull(elementSerializer);
        Objects.requireNonNull(elementClass);
        this.elementSerializer = elementSerializer;
        this.elementClass = elementClass;
    }

    protected void serializeElement(Object elementValue, JsonGenerator jsonGenerator, SerializationContext context) {
        if (null == elementValue) {
            jsonGenerator.writeNull();
            return;
        }
        Class<?> elementClass = elementValue.getClass();
        //Not null when generic type is present or previous item is of same type
        JsonbSerializer<?> delegateSerializer = getValueSerializer(elementClass);
        //Raw collections + lost generic information
        if (null == delegateSerializer) {
            Type instanceType = getValueType(getRuntimeType());
            instanceType = instanceType.equals(Object.class) ? elementClass : instanceType;
            TypeSerializerBuilder typeFactory = new TypeSerializerBuilder(((ObjectMarshaller) context).getJsonbContext());
            typeFactory.setObjectClass(elementClass);
            typeFactory.setWrapper(this);
            typeFactory.setType(instanceType);
            if (DefaultSerializers.getInstance().isKnownType(elementClass)) {
                //Still need to override isNillable to true with ContainerCustomization for all serializers
                //to preserve collections and array null elements
                typeFactory.setCustomization(new ContainerCustomization(new ClassCustomizationBuilder()));
            } else {
                //Need for class level annotations + user adapters/serializers bound to type
                ClassDescriptor typeDescriptor = ((ObjectMarshaller) context).getJsonbContext().getMappingContext().getOrCreateClassModel(elementClass);
                typeFactory.setCustomization(new ContainerCustomization(typeDescriptor.getCustomization()));
            }
            delegateSerializer = typeFactory.buildSerializer();
            //Cache last used value serializer in case of next item is the same type.
            setValueSerializer(delegateSerializer, elementClass);
        }
        invokeSerializer(delegateSerializer, elementValue, jsonGenerator, context);
    }

    protected Type getValueType(Type requestedType) {
        if (requestedType instanceof ParameterizedType) {
            Optional<Type> maybeRuntimeType = ReflectiveTypeResolver.resolveAsOptional(this, ((ParameterizedType) requestedType).getActualTypeArguments()[0]);
            return maybeRuntimeType.orElse(Object.class);
        }
        return Object.class;
    }
}

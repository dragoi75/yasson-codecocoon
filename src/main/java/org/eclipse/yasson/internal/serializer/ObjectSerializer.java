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

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.ReflectionUtils;
import org.eclipse.yasson.internal.model.BeanPropertyModel;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Serializes arbitrary object by reading its properties.
 *
 * @param <T> object type
 */
public class ObjectSerializer<T> extends AbstractContainerSerializer<T> {

    private void marshallProperty(T object, JsonGenerator generator, SerializationContext ctx, BeanPropertyModel propertyModel) {
        Marshaller marshaller = (Marshaller) ctx;
        if (propertyModel.isReadable()) {
            final Object propertyValue = propertyModel.getValue(object);
            if (null == propertyValue || isEmptyOptional(propertyValue)) {
                if (propertyModel.getCustomization().isNillable()) {
                    generator.writeNull(propertyModel.getWriteName());
                }
                return;
            }
            generator.writeKey(propertyModel.getWriteName());
            final JsonbSerializer<?> propertyCachedSerializer = propertyModel.getPropertySerializer();
            if (null != propertyCachedSerializer) {
                serializerCaptor(propertyCachedSerializer, propertyValue, generator, ctx);
                return;
            }
            Optional<Type> runtimeTypeOptional = ReflectionUtils.resolveOptionalType(this, propertyModel.getPropertySerializationType());
            Type genericType = runtimeTypeOptional.orElse(null);
            final JsonbSerializer<?> serializer = new SerializerBuilder(marshaller.getJsonbContext()).withWrapper(this).withObjectClass(propertyValue.getClass()).withCustomization(propertyModel.getCustomization()).withType(genericType).build();
            serializerCaptor(serializer, propertyValue, generator, ctx);
        }
    }

    @Override
    protected void writeStart(String key, JsonGenerator generator) {
        generator.writeStartObject(key);
    }

    private boolean isEmptyOptional(Object object) {
        if (!(object instanceof Optional)) {
            if (!(object instanceof OptionalInt)) {
                if (!(object instanceof OptionalLong)) {
                    if (object instanceof OptionalDouble) {
                        return !((OptionalDouble) object).isPresent();
                    }
                } else {
                    return !((OptionalLong) object).isPresent();
                }
            } else {
                return !((OptionalInt) object).isPresent();
            }
        } else {
            return !((Optional) object).isPresent();
        }
        return false;
    }

    @Override
    protected void writeStart(JsonGenerator generator) {
        generator.writeStartObject();
    }

    @Override
    protected void serializeInternal(T object, JsonGenerator generator, SerializationContext ctx) {
        Marshaller context = (Marshaller) ctx;
        try {
            if (!context.addProcessedObject(object)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.RECURSIVE_REFERENCE, object.getClass()));
            } else {
                final BeanPropertyModel[] allProperties = context.getMappingContext().getOrCreateClassModel(object.getClass()).getSortedProperties();
                for (BeanPropertyModel model : allProperties) {
                    try {
                        marshallProperty(object, generator, context, model);
                    } catch (Exception e) {
                        throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.SERIALIZE_PROPERTY_ERROR, model.getWriteName(), object.getClass().getCanonicalName()), e);
                    }
                }
            }
        } finally {
            context.removeProcessedObject(object);
        }
    }

    /**
     * Creates a new instance.
     *
     * @param builder Builder to initialize the instance.
     */
    public ObjectSerializer(SerializerBuilder builder) {
        super(builder);
    }

    /**
     * Creates a new instance.
     *
     * @param wrapper     wrapped item
     * @param runtimeType class type
     * @param classModel  model of the class
     */
    public ObjectSerializer(CurrentItem<?> wrapper, Type runtimeType, ClassDescriptor classModel) {
        super(wrapper, runtimeType, classModel);
    }

}

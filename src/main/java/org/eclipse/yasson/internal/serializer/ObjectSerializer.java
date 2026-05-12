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

import org.eclipse.yasson.internal.JsonbMarshaller;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.PropertyModel;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Serializes arbitrary object by reading its properties.
 *
 * @param <T> object type
 */
public class ObjectSerializer<T> extends AbstractContainerSerializer<T> {

    /**
     * Creates a new instance.
     *
     * @param builder Builder to initialize the instance.
     */
    public ObjectSerializer(TypeSerializerBuilder builder) {
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

    @Override
    protected void serializeInternal(T object, JsonGenerator generator, SerializationContext ctx) {
        JsonbMarshaller context = (JsonbMarshaller) ctx;
        try {
            if (context.addToProcessedObjects(object)) {
                final PropertyModel[] allProperties = context.getMappingContext().getOrCreateClassModel(object.getClass())
                        .getSortedProperties();
                for (PropertyModel model : allProperties) {
                    try {
                        marshallProperty(object, generator, context, model);
                    } catch (Exception e) {
                        throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.SERIALIZE_PROPERTY_ERROR, model.getWriteName(),
                                                                     object.getClass().getCanonicalName()), e);
                    }
                }
            } else {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.RECURSIVE_REFERENCE, object.getClass()));
            }
        } finally {
            context.removeFromProcessedObjects(object);
        }
    }

    @Override
    protected void writeStart(JsonGenerator generator) {
        generator.writeStartObject();
    }

    @Override
    protected void writeStart(String key, JsonGenerator generator) {
        generator.writeStartObject(key);
    }

    private void marshallProperty(T object, JsonGenerator generator, SerializationContext ctx, PropertyModel propertyModel) {
        JsonbMarshaller marshaller = (JsonbMarshaller) ctx;

        if (propertyModel.isReadable()) {
            final Object propertyValue = propertyModel.getValue(object);
            if (propertyValue == null || isEmptyOptional(propertyValue)) {
                if (propertyModel.getCustomization().isNillable()) {
                    generator.writeNull(propertyModel.getWriteName());
                }
                return;
            }

            generator.writeKey(propertyModel.getWriteName());

            final JsonbSerializer<?> propertyCachedSerializer = propertyModel.getPropertySerializer();
            if (propertyCachedSerializer != null) {
                serializerCaptor(propertyCachedSerializer, propertyValue, generator, ctx);
                return;
            }

            Optional<Type> runtimeTypeOptional = ReflectionTypeResolver
                    .resolveTypeOptional(this, propertyModel.getPropertySerializationType());
            Type genericType = runtimeTypeOptional.orElse(null);
            final JsonbSerializer<?> serializer = new TypeSerializerBuilder(marshaller.getJsonbContext())
                    .setWrapper(this)
                    .setObjectClass(propertyValue.getClass())
                    .setCustomization(propertyModel.getCustomization())
                    .setType(genericType).buildSerializer();
            serializerCaptor(serializer, propertyValue, generator, ctx);
        }
    }

    private boolean isEmptyOptional(Object object) {
        if (object instanceof Optional) {
            return !((Optional) object).isPresent();
        } else if (object instanceof OptionalInt) {
            return !((OptionalInt) object).isPresent();
        } else if (object instanceof OptionalLong) {
            return !((OptionalLong) object).isPresent();
        } else if (object instanceof OptionalDouble) {
            return !((OptionalDouble) object).isPresent();
        }
        return false;
    }

}

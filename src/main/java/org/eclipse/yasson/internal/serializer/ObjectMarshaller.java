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

import org.eclipse.yasson.internal.ReflectiveTypeResolver;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.PropertyDescriptor;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Serializes arbitrary object by reading its properties.
 *
 * @author Roman Grigoriadi
 */
public class ObjectMarshaller<T> extends ContainerSerializerBase<T> {

    @SuppressWarnings("unchecked")
    private void marshalProperty(T entity, JsonGenerator jsonWriter, SerializationContext serializationContext, PropertyDescriptor propertyDescriptor) {
        org.eclipse.yasson.internal.ObjectMarshaller objectSerializer = (org.eclipse.yasson.internal.ObjectMarshaller) serializationContext;
        if (propertyDescriptor.isReadable()) {
            final Object value = propertyDescriptor.getValue(entity);
            if (null == value || isEmptyOptional(value)) {
                if (propertyDescriptor.getCustomization().isNillable()) {
                    jsonWriter.writeNull(propertyDescriptor.getWriteName());
                }
                return;
            }
            jsonWriter.writeKey(propertyDescriptor.getWriteName());
            final JsonbSerializer<?> cachedSerializer = propertyDescriptor.getPropertySerializer();
            if (null != cachedSerializer) {
                invokeSerializer(cachedSerializer, value, jsonWriter, serializationContext);
                return;
            }
            Optional<Type> resolvedTypeOptional = ReflectiveTypeResolver.resolveAsOptional(this, propertyDescriptor.getPropertyType());
            Type typeParameter = resolvedTypeOptional.orElse(null);
            final JsonbSerializer<?> jsonbHandler = new TypeSerializerBuilder(objectSerializer.getJsonbContext()).setWrapper(this).setObjectClass(value.getClass()).setCustomization(propertyDescriptor.getCustomization()).setType(typeParameter).buildSerializer();
            invokeSerializer(jsonbHandler, value, jsonWriter, serializationContext);
        }
    }

    @Override
    protected void writeBegin(String propertyName, JsonGenerator jsonWriter) {
        jsonWriter.writeStartObject(propertyName);
    }

    private boolean isEmptyOptional(Object entity) {
        if (!(entity instanceof Optional)) {
            if (!(entity instanceof OptionalInt)) {
                if (!(entity instanceof OptionalLong)) {
                    if (entity instanceof OptionalDouble) {
                        return !((OptionalDouble) entity).isPresent();
                    }
                } else {
                    return !((OptionalLong) entity).isPresent();
                }
            } else {
                return !((OptionalInt) entity).isPresent();
            }
        } else {
            return !((Optional) entity).isPresent();
        }
        return false;
    }

    /**
     * Creates a new instance.
     *
     * @param activeItem wrapped item
     * @param actualType class type
     * @param classDescriptor model of the class
     */
    public ObjectMarshaller(ActiveItemModel<?> activeItem, Type actualType, ClassDescriptor classDescriptor) {
        super(activeItem, actualType, classDescriptor);
    }

    @Override
    protected void writeBegin(JsonGenerator jsonWriter) {
        jsonWriter.writeStartObject();
    }

    /**
     * Creates a new instance.
     *
     * @param typeSerializerCreator Builder to initialize the instance.
     */
    public ObjectMarshaller(TypeSerializerBuilder typeSerializerCreator) {
        super(typeSerializerCreator);
    }

    @Override
    protected void serializeContents(T entity, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        final PropertyDescriptor[] propertyDescriptors = ((org.eclipse.yasson.internal.ObjectMarshaller) serializationContext).getMappingContext().getOrCreateClassModel(entity.getClass()).getSortedProperties();
        for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
            marshalProperty(entity, jsonWriter, serializationContext, propertyDescriptor);
        }
    }

}

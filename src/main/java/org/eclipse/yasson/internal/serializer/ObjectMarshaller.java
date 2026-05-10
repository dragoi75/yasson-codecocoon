/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.model.PropertyModel;

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
public class ObjectMarshaller<T> extends AbstractContainerSerializer<T> {

    /**
     * Creates a new instance.
     *
     * @param typeSerializer Builder to initialize the instance.
     */
    public ObjectMarshaller(TypeSerializerBuilder typeSerializer) {
        super(typeSerializer);
    }

    /**
     * Creates a new instance.
     *
     * @param currentItem wrapped item
     * @param actualType class type
     * @param typeModel model of the class
     */
    public ObjectMarshaller(CurrentItem<?> currentItem, Type actualType, ClassModel typeModel) {
        super(currentItem, actualType, typeModel);
    }

    @Override
    protected void serializeInternal(T value, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        final PropertyModel[] properties = ((Marshaller) serializationContext).getMappingContext().getOrCreateClassModel(value.getClass()).getSortedProperties();
        for (PropertyModel property : properties) {
            serializeProperty(value, jsonWriter, serializationContext, property);
        }
    }

    @Override
    protected void writeStart(JsonGenerator jsonWriter) {
        jsonWriter.writeStartObject();
    }

    @Override
    protected void writeStart(String propertyName, JsonGenerator jsonWriter) {
        jsonWriter.writeStartObject(propertyName);
    }

    @SuppressWarnings("unchecked")
    private void serializeProperty(T instance, JsonGenerator jsonWriter, SerializationContext serializationContext, PropertyModel property) {
        Marshaller converter = (Marshaller) serializationContext;

        if (property.isReadable()) {
            final Object value = property.getValue(instance);
            if (value == null || isEmptyOptional(value)) {
                if (property.getCustomization().isNillable()) {
                    jsonWriter.writeNull(property.getWriteName());
                }
                return;
            }

            jsonWriter.writeKey(property.getWriteName());

            final JsonbSerializer<?> cachedSerializer = property.getPropertySerializer();
            if (cachedSerializer != null) {
                serializerCaptor(cachedSerializer, value, jsonWriter, serializationContext);
                return;
            }

            Optional<Type> resolvedTypeOpt = ReflectionTypeResolver.resolveTypeOptional(this, property.getPropertyType());
            Type paramType = resolvedTypeOpt.orElse(null);
            final JsonbSerializer<?> jsonbHandler = new TypeSerializerBuilder(converter.getJsonbContext())
                    .setWrapper(this)
                    .setObjectClass(value.getClass())
                    .setCustomization(property.getCustomization())
                    .setType(paramType).buildSerializer();
            serializerCaptor(jsonbHandler, value, jsonWriter, serializationContext);
        }
    }

    private boolean isEmptyOptional(Object value) {
        if (value instanceof Optional) {
            return !((Optional) value).isPresent();
        } else if (value instanceof OptionalInt) {
            return !((OptionalInt) value).isPresent();
        } else if (value instanceof OptionalLong) {
            return !((OptionalLong) value).isPresent();
        } else if (value instanceof OptionalDouble) {
            return !((OptionalDouble) value).isPresent();
        }
        return false;
    }

}

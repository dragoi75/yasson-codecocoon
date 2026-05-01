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
import org.eclipse.yasson.internal.ReflectionUtils;
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
     * @param serializerFactory Builder to initialize the instance.
     */
    public ObjectMarshaller(SerializerBuilder serializerFactory) {
        super(serializerFactory);
    }

    /**
     * Creates a new instance.
     *
     * @param currentItem wrapped item
     * @param actualType class type
     * @param classDescriptor model of the class
     */
    public ObjectMarshaller(CurrentItem<?> currentItem, Type actualType, ClassModel classDescriptor) {
        super(currentItem, actualType, classDescriptor);
    }

    @Override
    protected void serializeInternal(T value, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        final PropertyModel[] properties = ((Marshaller) serializationContext).getMappingContext().getOrCreateClassModel(value.getClass()).getSortedProperties();
        for (PropertyModel property : properties) {
            marshallProperty(value, jsonWriter, serializationContext, property);
        }
    }

    @Override
    protected void writeStart(JsonGenerator jsonWriter) {
        jsonWriter.writeStartObject();
    }

    @Override
    protected void writeStart(String fieldName, JsonGenerator jsonWriter) {
        jsonWriter.writeStartObject(fieldName);
    }

    @SuppressWarnings("unchecked")
    private void marshallProperty(T value, JsonGenerator jsonWriter, SerializationContext serializationContext, PropertyModel propertyDescriptor) {
        Marshaller marshallingHandler = (Marshaller) serializationContext;

        if (propertyDescriptor.isReadable()) {
            final Object propValue = propertyDescriptor.getValue(value);
            if (propValue == null || isEmptyOptional(propValue)) {
                if (propertyDescriptor.getCustomization().isNillable()) {
                    jsonWriter.writeNull(propertyDescriptor.getWriteName());
                }
                return;
            }

            jsonWriter.writeKey(propertyDescriptor.getWriteName());

            final JsonbSerializer<?> cachedSerializer = propertyDescriptor.getPropertySerializer();
            if (cachedSerializer != null) {
                serializerCaptor(cachedSerializer, propValue, jsonWriter, serializationContext);
                return;
            }

            Optional<Type> resolvedTypeOpt = ReflectionUtils.resolveOptionalType(this, propertyDescriptor.getPropertyType());
            Type declaredType = resolvedTypeOpt.orElse(null);
            final JsonbSerializer<?> customSerializer = new SerializerBuilder(marshallingHandler.getJsonbContext())
                    .withWrapper(this)
                    .withObjectClass(propValue.getClass())
                    .withCustomization(propertyDescriptor.getCustomization())
                    .withType(declaredType).build();
            serializerCaptor(customSerializer, propValue, jsonWriter, serializationContext);
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

/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
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

import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.yasson.internal.JsonBindingContext;
import org.eclipse.yasson.internal.SerializationContextImpl;
import org.eclipse.yasson.internal.model.customization.SerializationCustomizer;

/**
 * Null value serializer. Determines proper behavior when the serialized value is null.
 */
public class NullValueSerializer implements ModelMarshaller {

    private final ModelMarshaller modelMarshaller;

    private final ModelMarshaller nullValueMarshaller;

    private final ModelMarshaller rootNullMarshaller;

    /**
     * Create new instance.
     *
     * @param modelMarshaller      non-null value delegate
     * @param serializationCustomizer component customization
     * @param bindingContext  jsonb context
     */
    public NullValueSerializer(ModelMarshaller modelMarshaller, SerializationCustomizer serializationCustomizer, JsonBindingContext bindingContext) {
        this.modelMarshaller = modelMarshaller;
        if (!serializationCustomizer.isNillable()) {
            nullValueMarshaller = new DisableNullWriting();
        } else {
            nullValueMarshaller = new NullSerializationEnabled();
        }
        JsonbSerializer<?> customNullSerializer = bindingContext.getConfigProperties().getNullSerializer();
        if (null == customNullSerializer) {
            rootNullMarshaller = nullValueMarshaller;
        } else {
            rootNullMarshaller = (value, jsonWriter, serializationState) -> customNullSerializer.serialize(null, jsonWriter, serializationState);
        }
    }

    @Override
    public void marshal(Object input, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
        if (null != input) {
            serializationState.setRoot(false);
            modelMarshaller.marshal(input, jsonWriter, serializationState);
        } else {
            if (!serializationState.isRoot()) {
                nullValueMarshaller.marshal(null, jsonWriter, serializationState);
            } else {
                serializationState.setRoot(false);
                rootNullMarshaller.marshal(null, jsonWriter, serializationState);
            }
            serializationState.setKey(null);
        }
    }

    private static final class NullSerializationEnabled implements ModelMarshaller {

        @Override
        public void marshal(Object value, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
            if (null != serializationState.getKey()) {
                jsonWriter.writeNull(serializationState.getKey());
            } else {
                jsonWriter.writeNull();
            }
        }
    }

    private static class DisableNullWriting implements ModelMarshaller {

        @Override
        public void marshal(Object value, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
            if (serializationState.isContainerWithNulls()) {
                if (null != serializationState.getKey()) {
                    jsonWriter.writeNull(serializationState.getKey());
                } else {
                    jsonWriter.writeNull();
                }
            }
            serializationState.setKey(null);
            //Do nothing
        }
    }
}

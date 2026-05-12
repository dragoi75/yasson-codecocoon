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
    public NullValueSerializer(ModelMarshaller modelMarshaller,
                               SerializationCustomizer serializationCustomizer,
                               JsonBindingContext bindingContext) {
        this.modelMarshaller = modelMarshaller;
        if (serializationCustomizer.isNillable()) {
            nullValueMarshaller = new NullSerializationEnabled();
        } else {
            nullValueMarshaller = new DisableNullWriting();
        }
        JsonbSerializer<?> customNullSerializer = bindingContext.getConfigProperties().getNullSerializer();
        if (customNullSerializer != null) {
            rootNullMarshaller = (value, jsonWriter, serializationState) -> customNullSerializer.serialize(null, jsonWriter, serializationState);
        } else {
            rootNullMarshaller = nullValueMarshaller;
        }
    }

    @Override
    public void marshal(Object input, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
        if (input == null) {
            if (serializationState.isRoot()) {
                serializationState.setRoot(false);
                rootNullMarshaller.marshal(null, jsonWriter, serializationState);
            } else {
                nullValueMarshaller.marshal(null, jsonWriter, serializationState);
            }
            serializationState.setKey(null);
        } else {
            serializationState.setRoot(false);
            modelMarshaller.marshal(input, jsonWriter, serializationState);
        }
    }

    private static final class NullSerializationEnabled implements ModelMarshaller {

        @Override
        public void marshal(Object value, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
            if (serializationState.getKey() == null) {
                jsonWriter.writeNull();
            } else {
                jsonWriter.writeNull(serializationState.getKey());
            }
        }

    }

    private static class DisableNullWriting implements ModelMarshaller {

        @Override
        public void marshal(Object value, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
            if (serializationState.isContainerWithNulls()) {
                if (serializationState.getKey() == null) {
                    jsonWriter.writeNull();
                } else {
                    jsonWriter.writeNull(serializationState.getKey());
                }
            }
            serializationState.setKey(null);
            //Do nothing
        }

    }
}

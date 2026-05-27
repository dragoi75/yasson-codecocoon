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

import java.util.LinkedHashMap;

import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.SerializationContextImpl;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * Object container serializer.
 */
class ObjectMarshaller implements ModelMarshaller {

    private final LinkedHashMap<String, ModelMarshaller> fieldMarshallers;

    @Override
    public void marshal(Object inputObj, JsonGenerator jsonWriter, SerializationContextImpl serializationSession) {
        jsonWriter.writeStartObject();
        fieldMarshallers.forEach((propName, marshaller) -> {
            try {
                serializationSession.setKey(propName);
                marshaller.marshal(inputObj, jsonWriter, serializationSession);
            } catch (Exception ex) {
                throw new JsonbException(MessageProvider.getMessage(MessageConstants.SERIALIZE_PROPERTY_ERROR, propName,
                                                             inputObj.getClass().getCanonicalName()), ex);
            }
        });
        jsonWriter.writeEnd();
    }

    ObjectMarshaller(LinkedHashMap<String, ModelMarshaller> fieldMarshallers) {
        this.fieldMarshallers = fieldMarshallers;
    }

}

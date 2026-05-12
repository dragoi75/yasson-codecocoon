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

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.SerializationContextImpl;
import org.eclipse.yasson.internal.components.AdapterBindingInfo;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * User defined adapter invoker.
 */
class AdapterMarshaller extends AbstractSerializer {

    private final JsonbAdapter<Object, Object> jsonbConverter;
    private final AdapterBindingInfo bindingInfo;

    @SuppressWarnings("unchecked")
    AdapterMarshaller(AdapterBindingInfo bindingInfo,
                      ModelMarshaller modelMarshaller) {
        super(modelMarshaller);
        this.jsonbConverter = (JsonbAdapter<Object, Object>) bindingInfo.getAdapter();
        this.bindingInfo = bindingInfo;
    }

    @Override
    public void marshal(Object inputObj, JsonGenerator jsonWriter, SerializationContextImpl serializationState) {
        try {
            delegate.marshal(jsonbConverter.adaptToJson(inputObj), jsonWriter, serializationState);
        } catch (Exception exception) {
            throw new JsonbException(MessageProvider.getMessage(MessageConstants.ADAPTER_EXCEPTION,
                                                         bindingInfo.getBindingType(),
                                                         bindingInfo.getToType(),
                                                         jsonbConverter.getClass()), exception);
        }
    }

}

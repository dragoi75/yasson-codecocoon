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

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.ProcessingContext;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Serializes an object with user defined serializer.
 *
 * @param <T> type of serializer
 */
public class UserSerializerSerializer<T> implements JsonbSerializer<T> {

    private final JsonbSerializer<T> userSerializer;

    private final ClassDescriptor classModel;

    @Override
    public void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
        ProcessingContext context = (Marshaller) ctx;
        try {
            if (!context.addProcessedObject(obj)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.RECURSIVE_REFERENCE, obj.getClass()));
            } else {
                userSerializer.serialize(obj, generator, ctx);
            }
        } finally {
            context.removeProcessedObject(obj);
        }
    }

    /**
     * Create instance of current item with its builder.
     *
     * @param classModel     model
     * @param userSerializer user serializer
     */
    public UserSerializerSerializer(ClassDescriptor classModel, JsonbSerializer<T> userSerializer) {
        this.classModel = classModel;
        this.userSerializer = userSerializer;
    }

}

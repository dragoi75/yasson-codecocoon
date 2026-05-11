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

import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.ProcessingContext;
import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import javax.json.bind.JsonbException;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;

/**
 * Serializes an object with user defined serializer.
 *
 * @author Roman Grigoriadi
 * @param <T> type of serializer
 */
public class UserSerializerWrapper<T> implements JsonbSerializer<T> {

    private final JsonbSerializer<T> userJsonbAdapter;

    private final ClassModel typeModel;

    /**
     * Create instance of current item with its builder.
     *
     * @param typeModel model
     * @param userJsonbAdapter user serializer
     */
    public UserSerializerWrapper(ClassModel typeModel, JsonbSerializer<T> userJsonbAdapter) {
        this.typeModel = typeModel;
        this.userJsonbAdapter = userJsonbAdapter;
    }

    @Override
    public void serialize(T value, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        ProcessingContext processingEnv = (Marshaller) serializationContext;
        try {
            if (!processingEnv.addProcessedObject(value)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.RECURSIVE_REFERENCE, value.getClass()));
            } else {
                userJsonbAdapter.serialize(value, jsonWriter, serializationContext);
            }
        } finally {
            processingEnv.removeProcessedObject(value);
        }
    }
}

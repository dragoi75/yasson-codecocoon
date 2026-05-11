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

import org.eclipse.yasson.internal.JsonbMarshaller;
import org.eclipse.yasson.internal.ProcessingEnvironment;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;
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
public class UserSerializerSerializer<T> implements JsonbSerializer<T> {

    private final JsonbSerializer<T> userSerializer;

    private final ClassDescriptor classModel;

    /**
     * Create instance of current item with its builder.
     *
     * @param classModel model
     * @param userSerializer user serializer
     */
    public UserSerializerSerializer(ClassDescriptor classModel, JsonbSerializer<T> userSerializer) {
        this.classModel = classModel;
        this.userSerializer = userSerializer;
    }

    @Override
    public void serialize(T obj, JsonGenerator generator, SerializationContext ctx) {
        ProcessingEnvironment context = (JsonbMarshaller) ctx;
        try {
            if (!context.registerProcessedObject(obj)) {
                throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.RECURSIVE_REFERENCE, obj.getClass()));
            } else {
                userSerializer.serialize(obj, generator, ctx);
            }
        } finally {
            context.unregisterProcessedObject(obj);
        }
    }
}

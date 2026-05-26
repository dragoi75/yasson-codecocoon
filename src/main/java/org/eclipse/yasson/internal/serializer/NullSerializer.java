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
import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.SerializationContextImpl;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Null value serializer. Determines proper behavior when the serialized value is null.
 */
public class NullSerializer implements ModelSerializer {

    private final ModelSerializer delegate;

    private final ModelSerializer nullSerializer;

    private final ModelSerializer rootNullSerializer;

    private static final class NullWritingEnabled implements ModelSerializer {

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializationContextImpl context) {
            if (null != context.getKey()) {
                generator.writeNull(context.getKey());
            } else {
                generator.writeNull();
            }
        }
    }

    private static class NullWritingDisabled implements ModelSerializer {

        @Override
        public void serialize(Object value, JsonGenerator generator, SerializationContextImpl context) {
            if (context.isContainerWithNulls()) {
                if (null != context.getKey()) {
                    generator.writeNull(context.getKey());
                } else {
                    generator.writeNull();
                }
            }
            context.setKey(null);
            //Do nothing
        }
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializationContextImpl context) {
        if (null != value) {
            context.setRoot(false);
            delegate.serialize(value, generator, context);
        } else {
            if (!context.isRoot()) {
                nullSerializer.serialize(null, generator, context);
            } else {
                context.setRoot(false);
                rootNullSerializer.serialize(null, generator, context);
            }
            context.setKey(null);
        }
    }

    /**
     * Create new instance.
     *
     * @param delegate      non-null value delegate
     * @param customization component customization
     * @param jsonbContext  jsonb context
     */
    public NullSerializer(ModelSerializer delegate, Customization customization, JsonbContext jsonbContext) {
        this.delegate = delegate;
        if (!customization.isNillable()) {
            nullSerializer = new NullWritingDisabled();
        } else {
            nullSerializer = new NullWritingEnabled();
        }
        JsonbSerializer<?> userDefinedNullSerializer = jsonbContext.getConfigProperties().getNullSerializer();
        if (null == userDefinedNullSerializer) {
            rootNullSerializer = nullSerializer;
        } else {
            rootNullSerializer = (value, generator, context) -> userDefinedNullSerializer.serialize(null, generator, context);
        }
    }

}

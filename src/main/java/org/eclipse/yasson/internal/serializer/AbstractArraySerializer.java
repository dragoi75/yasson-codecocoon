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

import org.eclipse.yasson.internal.ReflectionHelper;
import javax.json.stream.JsonGenerator;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Common serializer for arrays.
 *
 * @author Roman Grigoriadi
 * @param <T> Type to serialize.
 */
public abstract class AbstractArraySerializer<T> extends AbstractContainerSerializer<T> implements EmbeddedItem {

    protected final Type arrayValType;

    protected AbstractArraySerializer(TypeSerializerBuilder builder) {
        super(builder);
        arrayValType = resolveArrayType();
    }

    private Type resolveArrayType() {
        if (null != getRuntimeType() && Object.class != getRuntimeType()) {
            if (!(getRuntimeType() instanceof ParameterizedType)) {
                if (!(getRuntimeType() instanceof GenericArrayType)) {
                    return ReflectionHelper.getRawType(getRuntimeType()).getComponentType();
                } else {
                    return ReflectionHelper.getRawType(this, ((GenericArrayType) getRuntimeType()).getGenericComponentType());
                }
            } else {
                return ReflectionHelper.resolveGenericType(this, ((ParameterizedType) getRuntimeType()).getActualTypeArguments()[0]);
            }
        } else {
            return Object.class;
        }
    }

    @Override
    protected void writeStart(JsonGenerator generator) {
        generator.writeStartArray();
    }

    @Override
    protected void writeStart(String key, JsonGenerator generator) {
        generator.writeStartArray(key);
    }
}

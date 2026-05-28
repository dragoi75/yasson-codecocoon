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

import org.eclipse.yasson.internal.ReflectionTypeResolver;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

/**
 * Serializer for maps.
 *
 * @author Roman Grigoriadi
 */
public class MapTypeSerializer<T extends Map<?, ?>> extends AbstractContainerSerializer<T> implements EmbeddedItem {

    private final boolean allowsNulls;

    @Override
    protected void writeStart(JsonGenerator jsonWriter) {
        jsonWriter.writeStartObject();
    }

    @Override
    protected Type getValueType(Type elementType) {
        if (elementType instanceof ParameterizedType) {
            Optional<Type> runtimeTypeMaybe = ReflectionTypeResolver.resolveTypeOptional(this, ((ParameterizedType) elementType).getActualTypeArguments()[1]);
            return runtimeTypeMaybe.orElse(Object.class);
        }
        return Object.class;
    }

    @Override
    protected void writeStart(String identifier, JsonGenerator jsonWriter) {
        jsonWriter.writeStartObject(identifier);
    }

    protected MapTypeSerializer(TypeSerializerBuilder serializerFactory) {
        super(serializerFactory);
        allowsNulls = serializerFactory.getJsonbContext().getConfigProperties().getConfigNullable();
    }

    @Override
    protected void serializeInternal(T valueToSerialize, JsonGenerator jsonWriter, SerializationContext serializationContext) {
        for (Map.Entry<?, ?> mapElement : valueToSerialize.entrySet()) {
            final String keysJoined = String.valueOf(mapElement.getKey());
            final Object content = mapElement.getValue();
            if (null == content) {
                if (allowsNulls) {
                    jsonWriter.writeNull(keysJoined);
                }
                continue;
            }
            jsonWriter.writeKey(keysJoined);
            serializeItem(content, jsonWriter, serializationContext);
        }
    }

}

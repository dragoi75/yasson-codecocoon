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

import java.lang.reflect.GenericArrayType;
import java.util.List;

import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbStreamingParser;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.ClassDescriptor;

/**
 * Common array unmarshalling item implementation.
 *
 * @param <T> array type
 */
public abstract class AbstractArrayDeserializer<T> extends AbstractContainerDeserializer<T> implements EmbeddedElement {

    /**
     * Runtime type class of an array.
     */
    private final Class<?> componentClass;
    private final ClassDescriptor componentClassModel;

    /**
     * Creates new class instance.
     *
     * @param builder deserializer builder
     */
    AbstractArrayDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
        if (getRuntimeType() instanceof GenericArrayType) {
            componentClass = ReflectionTypeResolver
                    .getRawClass(this, ((GenericArrayType) getRuntimeType()).getGenericComponentType());
        } else {
            componentClass = ReflectionTypeResolver.getRawType(getRuntimeType()).getComponentType();
        }
        if (!DefaultSerializerRegistry.getInstance().isKnownType(componentClass)) {
            componentClassModel = builder.getJsonbContext().getMappingContext().getOrCreateClassModel(componentClass);
        } else {
            componentClassModel = null;
        }
    }

    /**
     * Returns component class.
     *
     * @return component class
     */
    Class<?> getComponentClass() {
        return componentClass;
    }

    @Override
    public void appendResult(Object result) {
        appendCaptor(convertNullToOptionalEmpty(componentClass, result));
    }

    @SuppressWarnings("unchecked")
    private <X> void appendCaptor(X value) {
        ((List<X>) getItems()).add(value);
    }

    @Override
    protected void deserializeNext(JsonParser parser, JsonbUnmarshaller context) {
        final JsonbDeserializer<?> deserializer = newUnmarshallerItemBuilder(context.getJsonbContext()).setType(componentClass)
                .setCustomization(componentClassModel == null ? null : componentClassModel.getClassCustomization()).buildDeserializer();
        appendResult(deserializer.deserialize(parser, context, componentClass));
    }

    /**
     * Returns list of deserialized items.
     *
     * @return list of items
     */
    protected abstract List<?> getItems();

    @Override
    protected JsonbStreamingParser.LevelParseContext moveToFirst(JsonbNavigator parser) {
        parser.moveTo(JsonParser.Event.START_ARRAY);
        return parser.getCurrentLevel();
    }
}

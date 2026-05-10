/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonUnmarshaller;
import org.eclipse.yasson.internal.JsonbStreamParser;
import org.eclipse.yasson.internal.JsonbRiStreamParser;
import org.eclipse.yasson.internal.ReflectionHelper;
import org.eclipse.yasson.internal.model.ClassDescriptor;

import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import java.lang.reflect.GenericArrayType;
import java.util.List;

/**
 * Common array unmarshalling item implementation.
 *
 * @author Roman Grigoriadi
 */
public abstract class AbstractArrayDeserializer<T> extends AbstractContainerDeserializer<T> implements EmbeddedItem {

    /**
     * Runtime type class of an array.
     */
    protected final Class<?> componentClass;

    protected final ClassDescriptor componentClassModel;

    protected AbstractArrayDeserializer(DeserializationBuilder builder) {
        super(builder);
        if (getRuntimeType() instanceof GenericArrayType) {
            componentClass = ReflectionHelper.getRawType(this, ((GenericArrayType) getRuntimeType()).getGenericComponentType());
        } else {
            componentClass = ReflectionHelper.getRawType(getRuntimeType()).getComponentType();
        }
        if (!DefaultSerializerProvider.getInstance().isKnownType(componentClass)) {
            componentClassModel = builder.getJsonbContext().getMappingContext().getOrCreateClassModel(componentClass);
        } else {
            componentClassModel = null;
        }
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
    protected void deserializeNext(JsonParser parser, JsonUnmarshaller context) {
        final JsonbDeserializer<?> deserializer = newUnmarshallerItemBuilder(context.getJsonbContext()).setType(componentClass)
                .setCustomization(componentClassModel == null ? null : componentClassModel.getCustomization()).buildDeserializer();
        appendResult(deserializer.deserialize(parser, context, componentClass));
    }

    protected abstract List<?> getItems();

    @Override
    protected JsonbRiStreamParser.LevelParseState moveToFirst(JsonbStreamParser parser) {
        parser.moveTo(JsonParser.Event.START_ARRAY);
        return parser.getCurrentLevel();
    }
}
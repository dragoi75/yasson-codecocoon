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

import org.eclipse.yasson.internal.JsonbRiStreamParser;
import org.eclipse.yasson.internal.JsonbStructureNavigator;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.JsonbDeserializer;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import javax.json.stream.JsonParser;
import java.lang.reflect.GenericArrayType;
import java.util.List;

/**
 * Common array unmarshalling item implementation.
 *
 * @author Roman Grigoriadi
 */
public abstract class AbstractArrayDeserializer<T> extends AbstractCollectionDeserializer<T> implements EmbeddedItem {

    /**
     * Runtime type class of an array.
     */
    protected final Class<?> componentClass;

    protected final ClassDescriptor componentClassModel;

    protected AbstractArrayDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
        if (!(getRuntimeType() instanceof GenericArrayType)) {
            componentClass = ReflectionTypeResolver.getRawType(getRuntimeType()).getComponentType();
        } else {
            componentClass = ReflectionTypeResolver.resolveRawClass(this, ((GenericArrayType) getRuntimeType()).getGenericComponentType());
        }
        if (DefaultSerializerRegistry.getInstance().isKnownType(componentClass)) {
            componentClassModel = null;
        } else {
            componentClassModel = builder.getJsonbContext().getMappingContext().getOrCreateClassModel(componentClass);
        }
    }

    @Override
    public void appendValueToResult(Object result) {
        appendCaptor(convertNullToEmptyOptional(componentClass, result));
    }

    @SuppressWarnings("unchecked")
    private <X> void appendCaptor(X value) {
        ((List<X>) getItems()).add(value);
    }

    @Override
    protected void deserializeItem(JsonParser parser, JsonbDeserializer context) {
        final javax.json.bind.serializer.JsonbDeserializer<?> deserializer = createUnmarshallerItemBuilder(context.getJsonbContext()).setType(componentClass).setCustomization(null == componentClassModel ? null : componentClassModel.getCustomization()).buildDeserializer();
        appendValueToResult(deserializer.deserialize(parser, context, componentClass));
    }

    protected abstract List<?> getItems();

    @Override
    protected JsonbRiStreamParser.ParsingLevelContext moveToFirstElement(JsonbStructureNavigator parser) {
        parser.moveTo(JsonParser.Event.START_ARRAY);
        return parser.getCurrentLevel();
    }
}

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

import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbRiEventParser;
import org.eclipse.yasson.internal.ReflectionTypeUtils;
import org.eclipse.yasson.internal.JsonUnmarshaller;
import org.eclipse.yasson.internal.model.ClassModel;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import java.lang.reflect.GenericArrayType;
import java.util.List;

/**
 * Common array unmarshalling item implementation.
 *
 * @author Roman Grigoriadi
 */
public abstract class AbstractArrayDeserializer<T> extends BaseContainerDeserializer<T> implements EmbeddedElement {

    /**
     * Runtime type class of an array.
     */
    protected final Class<?> componentClass;

    protected final ClassModel componentClassModel;

    @Override
    protected void deserializeElement(JsonParser parser, JsonUnmarshaller context) {
        final JsonbDeserializer<?> deserializer = createUnmarshallerItemBuilder(context.getJsonbContext()).setType(componentClass).setCustomization(null == componentClassModel ? null : componentClassModel.getCustomization()).buildDeserializer();
        addResult(deserializer.deserialize(parser, context, componentClass));
    }

    @Override
    protected JsonbRiEventParser.LevelParseContext moveToStart(JsonbNavigator parser) {
        parser.moveTo(JsonParser.Event.START_ARRAY);
        return parser.getCurrentLevel();
    }

    protected abstract List<?> getItems();

    @SuppressWarnings("unchecked")
    private <X> void appendCaptor(X value) {
        ((List<X>) getItems()).add(value);
    }

    protected AbstractArrayDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
        if (!(getRuntimeType() instanceof GenericArrayType)) {
            componentClass = ReflectionTypeUtils.getRawType(getRuntimeType()).getComponentType();
        } else {
            componentClass = ReflectionTypeUtils.getRawType(this, ((GenericArrayType) getRuntimeType()).getGenericComponentType());
        }
        if (DefaultSerializers.getInstance().isKnownType(componentClass)) {
            componentClassModel = null;
        } else {
            componentClassModel = builder.getJsonbContext().getMappingContext().getOrCreateClassModel(componentClass);
        }
    }

    @Override
    public void addResult(Object result) {
        appendCaptor(convertNullToEmptyOptional(componentClass, result));
    }

}

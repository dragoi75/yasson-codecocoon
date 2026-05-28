/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.JsonbCursor;
import org.eclipse.yasson.internal.JsonbStreamingParser;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.ReflectiveTypeUtils;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Item implementation for {@link java.util.List} fields
 *
 * @author Roman Grigoriadi
 */
class CollectionValueDeserializer<T extends Collection<?>> extends BaseContainerDeserializer<T> implements EmbeddedItem {

    /**
     * Generic bound parameter of List.
     */
    private final Type elementType;

    private T element;

    @SuppressWarnings("unchecked")
    private <T> void addToCollection(T element) {
        ((Collection<T>) this.element).add(element);
    }

    @Override
    public void addResult(Object addedValue) {
        addToCollection(nullToOptionalEmpty(elementType, addedValue));
    }

    @Override
    protected void deserializeNextValue(JsonParser jsonStream, JsonbUnmarshaller jsonbState) {
        final JsonbDeserializer<?> valueReader = createCollectionOrMapItemDeserializer(elementType, jsonbState.getJsonbContext());
        addResult(valueReader.deserialize(jsonStream, jsonbState, elementType));
    }

    /**
     * @param deserializerCreator {@link JsonValueDeserializerBuilder ) used to build this instance
     */
    protected CollectionValueDeserializer(JsonValueDeserializerBuilder deserializerCreator) {
        super(deserializerCreator);
        elementType = getRuntimeType() instanceof ParameterizedType ? ReflectiveTypeUtils.resolveGenericType(this, ((ParameterizedType) getRuntimeType()).getActualTypeArguments()[0]) : Object.class;
        element = instantiate(deserializerCreator);
    }

    @Override
    protected JsonbStreamingParser.LevelParseContext moveToFirstToken(JsonbCursor jsonStream) {
        jsonStream.moveTo(JsonParser.Event.START_ARRAY);
        return jsonStream.getCurrentLevel();
    }

    @SuppressWarnings("unchecked")
    private T instantiate(JsonValueDeserializerBuilder deserializerCreator) {
        Class<T> targetClass = (Class<T>) ReflectiveTypeUtils.getRawType(getRuntimeType());
        if (targetClass.isInterface()) {
            final T elementValue = instantiateInterface(targetClass);
            if (null != elementValue)
                return elementValue;
        }
        return deserializerCreator.getJsonbContext().getInstanceCreator().createInstance(targetClass);
    }

    @SuppressWarnings("unchecked")
    private T instantiateInterface(Class<?> interfaceClass) {
        if (List.class.isAssignableFrom(interfaceClass)) {
            if (interfaceClass == LinkedList.class) {
                return (T) new LinkedList();
            }
            return (T) new ArrayList<>();
        }
        if (Set.class.isAssignableFrom(interfaceClass)) {
            if (SortedSet.class.isAssignableFrom(interfaceClass)) {
                return (T) new TreeSet<>();
            }
            return (T) new HashSet<>();
        }
        if (Queue.class.isAssignableFrom(interfaceClass)) {
            return (T) new ArrayDeque<>();
        }
        if (interfaceClass == Collection.class) {
            return (T) new ArrayList();
        }
        return null;
    }

    @Override
    public T getInstance(JsonbUnmarshaller unmarshaller) {
        return element;
    }

}

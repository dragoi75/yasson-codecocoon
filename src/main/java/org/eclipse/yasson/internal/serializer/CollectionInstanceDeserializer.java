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

import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbRiEventParser;
import org.eclipse.yasson.internal.ReflectionTypeUtils;
import org.eclipse.yasson.internal.JsonUnmarshaller;
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
class CollectionInstanceDeserializer<T extends Collection<?>> extends BaseContainerDeserializer<T> implements EmbeddedElement {

    /**
     * Generic bound parameter of List.
     */
    private final Type elementType;

    private T elementValue;

    @Override
    protected JsonbRiEventParser.LevelParseContext moveToStart(JsonbNavigator jsonReader) {
        jsonReader.moveTo(JsonParser.Event.START_ARRAY);
        return jsonReader.getCurrentLevel();
    }

    @SuppressWarnings("unchecked")
    private <T> void addElement(T newElement) {
        ((Collection<T>) elementValue).add(newElement);
    }

    @Override
    protected void deserializeElement(JsonParser jsonReader, JsonUnmarshaller unmarshalEnv) {
        final JsonbDeserializer<?> elementHandler = createCollectionOrMapItem(elementType, unmarshalEnv.getJsonbContext());
        addResult(elementHandler.deserialize(jsonReader, unmarshalEnv, elementType));
    }

    @Override
    public void addResult(Object outcome) {
        addElement(convertNullToEmptyOptional(elementType, outcome));
    }

    @Override
    public T getInstance(JsonUnmarshaller unmarshaller) {
        return elementValue;
    }

    /**
     * @param deserializerFactory {@link JsonDeserializerBuilder ) used to build this instance
     */
    protected CollectionInstanceDeserializer(JsonDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
        elementType = getRuntimeType() instanceof ParameterizedType ? ReflectionTypeUtils.resolveGenericType(this, ((ParameterizedType) getRuntimeType()).getActualTypeArguments()[0]) : Object.class;
        elementValue = instantiate(deserializerFactory);
    }

    @SuppressWarnings("unchecked")
    private T instantiate(JsonDeserializerBuilder deserializerFactory) {
        Class<T> actualClass = (Class<T>) ReflectionTypeUtils.getRawType(getRuntimeType());
        if (actualClass.isInterface()) {
            final T element = instantiateInterface(actualClass);
            if (null != element)
                return element;
        }
        return deserializerFactory.getJsonbContext().getInstanceCreator().getOrCreateInstance(actualClass);
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

}

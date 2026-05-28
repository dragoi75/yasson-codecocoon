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

import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.JsonbRiParser;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.Unmarshaller;
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
class CollectionParser<T extends Collection<?>> extends BaseContainerDeserializer<T> implements EmbeddedItem {

    /**
     * Generic bound parameter of List.
     */
    private final Type elementType;

    private T element;

    @Override
    protected JsonbRiParser.LevelContext advanceToFirst(JsonbParser jsonReader) {
        jsonReader.moveTo(JsonParser.Event.START_ARRAY);
        return jsonReader.getCurrentLevel();
    }

    @Override
    public void addResult(Object value) {
        appendElement(convertNullToEmptyOptional(elementType, value));
    }

    @Override
    public T getInstance(Unmarshaller unmarshaller) {
        return element;
    }

    @Override
    protected void deserializeNextValue(JsonParser jsonReader, Unmarshaller unmarshallerRef) {
        final JsonbDeserializer<?> jsonbHandler = createCollectionOrMapItemDeserializer(elementType, unmarshallerRef.getJsonbContext());
        addResult(jsonbHandler.deserialize(jsonReader, unmarshallerRef, elementType));
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

    @SuppressWarnings("unchecked")
    private T instantiate() {
        Class<T> targetClass = (Class<T>) ReflectionTypeResolver.getRawType(getRuntimeType());
        assert Collection.class.isAssignableFrom(targetClass);
        if (targetClass.isInterface()) {
            final T value = instantiateInterface(targetClass);
            if (null != value)
                return value;
        }
        return ReflectionTypeResolver.createInstanceWithNoArgs(targetClass);
    }

    @SuppressWarnings("unchecked")
    private <T> void appendElement(T element) {
        ((Collection<T>) this.element).add(element);
    }

    /**
     * @param deserializerFactory {@link JsonbDeserializerBuilder ) used to build this instance
     */
    protected CollectionParser(JsonbDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
        elementType = getRuntimeType() instanceof ParameterizedType ? ReflectionTypeResolver.resolveActualType(this, ((ParameterizedType) getRuntimeType()).getActualTypeArguments()[0]) : Object.class;
        element = instantiate();
    }

}

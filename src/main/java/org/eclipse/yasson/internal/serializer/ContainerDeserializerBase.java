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

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.JsonbDeserializer;
import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbRiEventParser;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.ReflectionHelper;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

/**
 * Base class for all deserializers producing non single value result.
 * Deserialize bean objects, collections, maps, arrays, etc.
 *
 * @param <T> container type
 */
public abstract class ContainerDeserializerBase<T> extends AbstractWrappedItem<T> implements jakarta.json.bind.serializer.JsonbDeserializer<T> {

    private JsonbRiEventParser.ParsingLevelContext parsingLevel;

    /**
     * Sets new parser context.
     *
     * @param parsingLevel parser context
     */
    void setParserContext(JsonbRiEventParser.ParsingLevelContext parsingLevel) {
        this.parsingLevel = parsingLevel;
    }

    /**
     * If value is null and property model type is one of {@link Optional}, {@link OptionalDouble},
     * {@link OptionalInt}, or {@link OptionalLong}, value of corresponding {@code Optional#empty()}
     * is returned.
     *
     * @param fieldType property type
     * @param item        value to set
     * @return empty optional if applies
     */
    protected Object convertNullToOptional(Type fieldType, Object item) {
        if (null != item) {
            return item;
        }
        if (!(fieldType instanceof Class)) {
            fieldType = ReflectionHelper.getRawType(ReflectionHelper.resolveActualType(this, fieldType));
        }
        if (Optional.class != fieldType) {
            if (OptionalInt.class != fieldType) {
                if (OptionalLong.class != fieldType) {
                    if (OptionalDouble.class != fieldType) {
                        return null;
                    } else {
                        return OptionalDouble.empty();
                    }
                } else {
                    return OptionalLong.empty();
                }
            } else {
                return OptionalInt.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    /**
     * Deserialize specific item type.
     *
     * @param tokenStream  jsonb parser
     * @param deserializationEnv context
     */
    protected void deserialize(JsonbNavigator tokenStream, JsonbDeserializer deserializationEnv) {
        parsingLevel = moveToStart(tokenStream);
        while (tokenStream.hasNext()) {
            final JsonParser.Event currentToken = tokenStream.next();
            switch(currentToken) {
                case START_OBJECT:
                case START_ARRAY:
                case VALUE_STRING:
                case VALUE_NUMBER:
                case VALUE_FALSE:
                case VALUE_TRUE:
                    try {
                        deserializeNextValue(tokenStream, deserializationEnv);
                    } catch (JsonbException ex) {
                        if (null != parsingLevel && null != parsingLevel.getLastKeyName()) {
                            throw new JsonbException("Unable to deserialize property '" + parsingLevel.getLastKeyName() + "' because of: " + ex.getMessage(), ex);
                        } else {
                            throw ex;
                        }
                    }
                    break;
                case KEY_NAME:
                    break;
                case VALUE_NULL:
                    addResult(null);
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    return;
                default:
                    throw new JsonbException(Messages.getMessage(MessageKeys.NOT_VALUE_TYPE, currentToken));
            }
        }
    }

    /**
     * Returns new deserialization builder for specific item.
     *
     * @param elementDeserializer jsonb context
     * @return deserialization builder
     */
    protected JsonDeserializerBuilder createUnmarshallerItemBuilder(JsonbRuntimeContext elementDeserializer) {
        return ContainerDeserializerUtils.newUnmarshallerItemBuilder(this, elementDeserializer, parsingLevel.getLastEvent());
    }

    /**
     * After object is transitively deserialized from JSON, "append" it to its wrapper.
     * In case of a field set value to field, in case of collections
     * or other embedded objects use methods provided.
     *
     * @param output An instance result of an item.
     */
    public abstract void addResult(Object output);

    /**
     * Creates and initializes an instance of deserializing item.
     *
     * @param deserializerInstance Current deserialization context.
     * @return An instance of deserializing item.
     */
    protected abstract T getInstance(JsonbDeserializer deserializerInstance);

    /**
     * Create instance of current item with its builder.
     *
     * @param deserializerFactory {@link JsonDeserializerBuilder} used to build this instance
     */
    ContainerDeserializerBase(JsonDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }

    /**
     * Returns parser context.
     *
     * @return parser context
     */
    JsonbRiEventParser.ParsingLevelContext getParserContext() {
        return parsingLevel;
    }

    /**
     * Determine class mappings and create an instance of a new deserializer.
     * Currently processed deserializer is pushed to stack, for waiting till new object is finished.
     *
     * @param tokenStream  Json parser.
     * @param deserializationEnv Current unmarshalling context.
     */
    protected abstract void deserializeNextValue(JsonParser tokenStream, JsonbDeserializer deserializationEnv);

    /**
     * Returns new deserialization builder for specific collection or map.
     *
     * @param elementType value type
     * @param elementDeserializer       jsonb context
     * @return deserialization builder
     */
    protected jakarta.json.bind.serializer.JsonbDeserializer<?> createCollectionOrMapItem(Type elementType, JsonbRuntimeContext elementDeserializer) {
        return ContainerDeserializerUtils.newCollectionOrMapItem(this, elementType, elementDeserializer, parsingLevel.getLastEvent());
    }

    /**
     * Move to first event for current deserializer structure.
     *
     * @param tokenStream Json parser.
     * @return First event.
     */
    protected abstract JsonbRiEventParser.ParsingLevelContext moveToStart(JsonbNavigator tokenStream);

    /**
     * Drives JSONP {@link JsonParser} to deserialize json document.
     *
     * @param tokenStream  JSON parser.
     * @param deserializationEnv Deseriaization context.
     * @param runtimeType  Runtime type.
     * @return Instance of a type for this item.
     */
    @Override
    public final T deserialize(JsonParser tokenStream, DeserializationContext deserializationEnv, Type runtimeType) {
        JsonbDeserializer elementDeserializer = (JsonbDeserializer) deserializationEnv;
        deserialize((JsonbNavigator) tokenStream, elementDeserializer);
        return getInstance((JsonbDeserializer) deserializationEnv);
    }

}

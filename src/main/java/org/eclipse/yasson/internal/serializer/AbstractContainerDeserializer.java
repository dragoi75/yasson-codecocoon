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
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.JsonbStreamingParser;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Base class for all deserializers producing non single value result.
 * Deserialize bean objects, collections, maps, arrays, etc.
 *
 * @param <T> container type
 */
public abstract class AbstractContainerDeserializer<T> extends BaseItem<T> implements JsonbDeserializer<T> {

    private JsonbStreamingParser.LevelParseContext parserContext;

    /**
     * Returns parser context.
     *
     * @return parser context
     */
    JsonbStreamingParser.LevelParseContext getParserContext() {
        return parserContext;
    }

    /**
     * Returns new deserialization builder for specific collection or map.
     *
     * @param valueType value type
     * @param ctx       jsonb context
     * @return deserialization builder
     */
    protected JsonbDeserializer<?> newCollectionOrMapItem(Type valueType, JsonbRuntimeContext ctx) {
        return ContainerDeserializerUtils.newCollectionOrMapItem(this, valueType, ctx, parserContext.getLastEvent());
    }

    /**
     * Returns new deserialization builder for specific item.
     *
     * @param ctx jsonb context
     * @return deserialization builder
     */
    protected JsonDeserializerBuilder newUnmarshallerItemBuilder(JsonbRuntimeContext ctx) {
        return ContainerDeserializerUtils.newUnmarshallerItemBuilder(this, ctx, parserContext.getLastEvent());
    }

    /**
     * If value is null and property model type is one of {@link Optional}, {@link OptionalDouble},
     * {@link OptionalInt}, or {@link OptionalLong}, value of corresponding {@code Optional#empty()}
     * is returned.
     *
     * @param propertyType property type
     * @param value        value to set
     * @return empty optional if applies
     */
    protected Object convertNullToOptionalEmpty(Type propertyType, Object value) {
        if (null != value) {
            return value;
        }
        if (!(propertyType instanceof Class)) {
            propertyType = ReflectionTypeResolver.getRawType(ReflectionTypeResolver.resolveTypeDefault(this, propertyType));
        }
        if (Optional.class != propertyType) {
            if (OptionalInt.class != propertyType) {
                if (OptionalLong.class != propertyType) {
                    if (OptionalDouble.class != propertyType) {
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
     * Sets new parser context.
     *
     * @param parserContext parser context
     */
    void setParserContext(JsonbStreamingParser.LevelParseContext parserContext) {
        this.parserContext = parserContext;
    }

    /**
     * Deserialize specific item type.
     *
     * @param parser  jsonb parser
     * @param context context
     */
    protected void deserializeInternal(JsonbNavigator parser, JsonbUnmarshaller context) {
        parserContext = moveToFirst(parser);
        while (parser.hasNext()) {
            final JsonParser.Event event = parser.next();
            switch(event) {
                case START_OBJECT:
                case START_ARRAY:
                case VALUE_STRING:
                case VALUE_NUMBER:
                case VALUE_FALSE:
                case VALUE_TRUE:
                    try {
                        deserializeNext(parser, context);
                    } catch (JsonbException e) {
                        if (null != parserContext && null != parserContext.getLastKeyName()) {
                            throw new JsonbException("Unable to deserialize property '" + parserContext.getLastKeyName() + "' because of: " + e.getMessage(), e);
                        } else {
                            throw e;
                        }
                    }
                    break;
                case KEY_NAME:
                    break;
                case VALUE_NULL:
                    appendResult(null);
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    return;
                default:
                    throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.NOT_VALUE_TYPE, event));
            }
        }
    }

    /**
     * Move to first event for current deserializer structure.
     *
     * @param parser Json parser.
     * @return First event.
     */
    protected abstract JsonbStreamingParser.LevelParseContext moveToFirst(JsonbNavigator parser);

    /**
     * Creates and initializes an instance of deserializing item.
     *
     * @param unmarshaller Current deserialization context.
     * @return An instance of deserializing item.
     */
    protected abstract T getInstance(JsonbUnmarshaller unmarshaller);

    /**
     * Drives JSONP {@link JsonParser} to deserialize json document.
     *
     * @param parser  JSON parser.
     * @param context Deseriaization context.
     * @param rtType  Runtime type.
     * @return Instance of a type for this item.
     */
    @Override
    public final T deserialize(JsonParser parser, DeserializationContext context, Type rtType) {
        JsonbUnmarshaller ctx = (JsonbUnmarshaller) context;
        deserializeInternal((JsonbNavigator) parser, ctx);
        return getInstance((JsonbUnmarshaller) context);
    }

    /**
     * Create instance of current item with its builder.
     *
     * @param builder {@link JsonDeserializerBuilder} used to build this instance
     */
    AbstractContainerDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
    }

    /**
     * After object is transitively deserialized from JSON, "append" it to its wrapper.
     * In case of a field set value to field, in case of collections
     * or other embedded objects use methods provided.
     *
     * @param result An instance result of an item.
     */
    public abstract void appendResult(Object result);

    /**
     * Determine class mappings and create an instance of a new deserializer.
     * Currently processed deserializer is pushed to stack, for waiting till new object is finished.
     *
     * @param parser  Json parser.
     * @param context Current unmarshalling context.
     */
    protected abstract void deserializeNext(JsonParser parser, JsonbUnmarshaller context);

}

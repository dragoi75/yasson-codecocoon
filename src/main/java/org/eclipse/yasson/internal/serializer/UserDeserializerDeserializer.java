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

import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.JsonbDeserializer;
import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbRiEventParser;
import org.eclipse.yasson.internal.UserDeserializerParser;
import org.eclipse.yasson.internal.components.DeserializerBinding;

/**
 * Item for processing types, to which deserializer is bound.
 *
 * @param <T> object type
 */
public class UserDeserializerDeserializer<T> extends ContainerDeserializerBase<T> {

    private DeserializerBinding<?> deserializerBinding;

    private T deserializerResult;

    /**
     * Create instance of current item with its builder.
     * Contains user provided component for custom deserialization.
     * Decorates calls to JsonParser, with validation logic so user can't left parser cursor
     * in wrong position after returning from deserializerBinding.
     *
     * @param builder             {@link JsonDeserializerBuilder} used to build this instance
     * @param deserializerBinding Deserializer.
     */
    protected UserDeserializerDeserializer(JsonDeserializerBuilder builder, DeserializerBinding<?> deserializerBinding) {
        super(builder);
        this.deserializerBinding = deserializerBinding;
    }

    @Override
    public void addResult(Object result) {
        //ignore internal deserialize() call in custom deserializer
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getInstance(JsonbDeserializer unmarshaller) {
        return deserializerResult;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void deserialize(JsonbNavigator parser, JsonbDeserializer context) {
        setParserContext(moveToStart(parser));
        JsonParser.Event lastEvent = getParserContext().getLastEvent();
        final UserDeserializerParser userDeserializerParser = new UserDeserializerParser(parser);
        deserializerResult = (T) deserializerBinding.getJsonbDeserializer().deserialize(userDeserializerParser, context, getRuntimeType());
        //In case deserialized structure is json object or array and the parser is not advanced
        //after enclosing bracket of deserialized object.
        if (getParserContext() == parser.getCurrentLevel() && !JsonDeserializerBuilder.isJsonValueEvent(lastEvent)) {
            userDeserializerParser.advanceParserToEnd();
        }
    }

    @Override
    protected void deserializeNextValue(JsonParser parser, JsonbDeserializer context) {
        throw new UnsupportedOperationException("Not supported for user deserializer");
    }

    /**
     * Don't move anywhere in case of user deserializer.
     */
    @Override
    protected JsonbRiEventParser.ParsingLevelContext moveToStart(JsonbNavigator parser) {
        return parser.getCurrentLevel();
    }
}

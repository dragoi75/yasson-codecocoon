/*******************************************************************************
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved.
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
import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbRiEventParser;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.UserDeserializerParser;

import javax.json.stream.JsonParser;

/**
 * Item for processing types, to which deserializer is bound.
 *
 * @author Roman Grigoriadi
 */
public class UserDeserializerDeserializer<T> extends BaseContainerDeserializer<T> {

    private DeserializerBinding<?> deserializerBinding;

    private T deserializerResult;

    /**
     * Don't move anywhere in case of user deserializer.
     */
    @Override
    protected JsonbRiEventParser.LevelParseContext moveToStart(JsonbNavigator parser) {
        return parser.getCurrentLevel();
    }

    @Override
    protected void deserializeElement(JsonParser parser, JsonUnmarshaller context) {
        throw new UnsupportedOperationException("Not supported for user deserializer");
    }

    @SuppressWarnings("unchecked")
    @Override
    public void deserializeContents(JsonbNavigator parser, JsonUnmarshaller context) {
        parserContext = moveToStart(parser);
        JsonParser.Event lastEvent = parserContext.getLastEvent();
        final UserDeserializerParser userDeserializerParser = new UserDeserializerParser(parser);
        deserializerResult = (T) deserializerBinding.getJsonbDeserializer().deserialize(userDeserializerParser, context, getRuntimeType());
        //In case deserialized structure is json object or array and the parser is not advanced
        //after enclosing bracket of deserialized object.
        if (parser.getCurrentLevel() == parserContext && !JsonDeserializerBuilder.isJsonValueEvent(lastEvent)) {
            userDeserializerParser.advanceParserToEnd();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getInstance(JsonUnmarshaller unmarshaller) {
        return deserializerResult;
    }

    /**
     * Create instance of current item with its builder.
     * Contains user provided component for custom deserialization.
     * Decorates calls to JsonParser, with validation logic so user can't left parser cursor
     * in wrong position after returning from deserializerBinding.
     *
     * @param builder {@link JsonDeserializerBuilder} used to build this instance
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

}
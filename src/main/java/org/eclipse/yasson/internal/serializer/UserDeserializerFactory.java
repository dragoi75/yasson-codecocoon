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

import org.eclipse.yasson.internal.JsonbCursor;
import org.eclipse.yasson.internal.JsonbStreamingParser;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.UserDeserializerParser;

import javax.json.stream.JsonParser;

/**
 * Item for processing types, to which deserializer is bound.
 *
 * @author Roman Grigoriadi
 */
public class UserDeserializerFactory<T> extends BaseContainerDeserializer<T> {

    private JsonbDeserializerBinding<?> bindingEntry;

    private T parsedValue;

    /**
     * Create instance of current item with its builder.
     * Contains user provided component for custom deserialization.
     * Decorates calls to JsonParser, with validation logic so user can't left parser cursor
     * in wrong position after returning from deserializerBinding.
     *
     * @param valueFactory {@link JsonValueDeserializerBuilder} used to build this instance
     * @param bindingEntry Deserializer.
     */
    protected UserDeserializerFactory(JsonValueDeserializerBuilder valueFactory, JsonbDeserializerBinding<?> bindingEntry) {
        super(valueFactory);
        this.bindingEntry = bindingEntry;
    }

    @Override
    public void addResult(Object result) {
        //ignore internal deserialize() call in custom deserializer
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getInstance(JsonbUnmarshaller unmarshaller) {
        return parsedValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void deserializeContainer(JsonbCursor cursor, JsonbUnmarshaller unmarshalEnv) {
        parserContext = moveToFirstToken(cursor);
        JsonParser.Event previousEvent = parserContext.getLastEvent();
        final UserDeserializerParser userParser = new UserDeserializerParser(cursor);
        parsedValue = (T) bindingEntry.getJsonbDeserializer().deserialize(userParser, unmarshalEnv, getRuntimeType());
        //Avoid moving parser to the end of the object, if deserializer was for one value only.
        if (previousEvent == JsonParser.Event.START_ARRAY || previousEvent == JsonParser.Event.START_OBJECT) {
            userParser.advanceParserToEnd();
        }
    }

    @Override
    protected void deserializeNextValue(JsonParser parser, JsonbUnmarshaller context) {
        throw new UnsupportedOperationException("Not supported for user deserializer");
    }

    /**
     * Don't move anywhere in case of user deserializer.
     */
    @Override
    protected JsonbStreamingParser.LevelParseContext moveToFirstToken(JsonbCursor cursor) {
        return cursor.getCurrentLevel();
    }

}
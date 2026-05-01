/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.deserializer;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DeserializationContextImpl;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

/**
 * Object container deserializer.
 */
class PojoDeserializer implements ModelDeserializer<JsonParser> {

    private final Map<String, ModelDeserializer<JsonParser>> propertyParserHandlers;
    private final Function<String, String> nameTransformer;
    private final Class<?> rawType;
    private final boolean rejectUnknownProperties;
    private final Set<String> excludedProperties;

    PojoDeserializer(Map<String, ModelDeserializer<JsonParser>> propertyParserHandlers,
                     Function<String, String> nameTransformer,
                     Class<?> rawType,
                     boolean rejectUnknownProperties,
                     Set<String> excludedProperties) {
        this.propertyParserHandlers = Map.copyOf(propertyParserHandlers);
        this.nameTransformer = nameTransformer;
        this.rawType = rawType;
        this.rejectUnknownProperties = rejectUnknownProperties;
        this.excludedProperties = Set.copyOf(excludedProperties);
    }

    @Override
    public Object deserialize(JsonParser jsonReader, DeserializationContextImpl deserializationEnv) {
        String propertyName = null;
        while (jsonReader.hasNext()) {
            final JsonParser.Event upcomingEvent = jsonReader.next();
            deserializationEnv.setLastValueEvent(upcomingEvent);
            switch (upcomingEvent) {
            case KEY_NAME:
                propertyName = nameTransformer.apply(jsonReader.getString());
                break;
            case VALUE_NULL:
            case START_OBJECT:
            case START_ARRAY:
            case VALUE_STRING:
            case VALUE_NUMBER:
            case VALUE_FALSE:
            case VALUE_TRUE:
                if (propertyParserHandlers.containsKey(propertyName)) {
                    try {
                        propertyParserHandlers.get(propertyName).deserialize(jsonReader, deserializationEnv);
                    } catch (JsonbException exception) {
                        throw new JsonbException("Unable to deserialize property '" + propertyName + "' because of: " + exception.getMessage(), exception);
                    }
                } else if (rejectUnknownProperties && !excludedProperties.contains(propertyName)) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.UNKNOWN_JSON_PROPERTY, propertyName, rawType));
                }
                break;
            case END_ARRAY:
                break;
            case END_OBJECT:
                return deserializationEnv.getInstance();
            default:
                throw new JsonbException("Unexpected state: " + upcomingEvent);
            }
        }
        return deserializationEnv.getInstance();
    }
}

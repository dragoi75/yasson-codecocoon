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

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DeserializationContextImplementation;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Object container deserializer.
 */
class BeanDeserializer implements ModelUnmarshaller<JsonParser> {

    static final Consumer<JsonParser> EMPTY_CONSUMER = jsonParser -> {};
    static final EnumMap<JsonParser.Event, Consumer<JsonParser>> EVENT_CONSUMERS = new EnumMap<>(JsonParser.Event.class);

    static {
        EVENT_CONSUMERS.put(JsonParser.Event.START_OBJECT, JsonParser::skipObject);
        EVENT_CONSUMERS.put(JsonParser.Event.START_ARRAY, JsonParser::skipArray);
    }

    private final Map<String, ModelUnmarshaller<JsonParser>> fieldDeserializers;
    private final Function<String, String> nameTransformer;
    private final Class<?> targetClass;
    private final boolean rejectUnknownProperties;
    private final Set<String> excludedFields;

    @Override
    public Object unmarshal(JsonParser jsonInput, DeserializationContextImplementation deserState) {
        String propertyName = null;
        while (jsonInput.hasNext()) {
            final JsonParser.Event upcomingEvent = jsonInput.next();
            deserState.setLastValueEvent(upcomingEvent);
            switch (upcomingEvent) {
            case KEY_NAME:
                propertyName = nameTransformer.apply(jsonInput.getString());
                break;
            case VALUE_NULL:
            case START_OBJECT:
            case START_ARRAY:
            case VALUE_STRING:
            case VALUE_NUMBER:
            case VALUE_FALSE:
            case VALUE_TRUE:
                if (fieldDeserializers.containsKey(propertyName)) {
                    try {
                        fieldDeserializers.get(propertyName).unmarshal(jsonInput, deserState);
                    } catch (JsonbException ex) {
                        throw new JsonbException("Unable to deserialize property '" + propertyName + "' because of: " + ex.getMessage(), ex);
                    }
                } else if (rejectUnknownProperties && !excludedFields.contains(propertyName)) {
                    throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.UNKNOWN_JSON_PROPERTY, propertyName, targetClass));
                } else {
                    //We need to skip the corresponding structure if property key was not found
                    EVENT_CONSUMERS.getOrDefault(upcomingEvent, EMPTY_CONSUMER).accept(jsonInput);
                }
                break;
            case END_ARRAY:
                break;
            case END_OBJECT:
                return deserState.getInstance();
            default:
                throw new JsonbException("Unexpected state: " + upcomingEvent);
            }
        }
        return deserState.getInstance();
    }

    BeanDeserializer(Map<String, ModelUnmarshaller<JsonParser>> fieldDeserializers,
                     Function<String, String> nameTransformer,
                     Class<?> targetClass,
                     boolean rejectUnknownProperties,
                     Set<String> excludedFields) {
        this.fieldDeserializers = Map.copyOf(fieldDeserializers);
        this.nameTransformer = nameTransformer;
        this.targetClass = targetClass;
        this.rejectUnknownProperties = rejectUnknownProperties;
        this.excludedFields = Set.copyOf(excludedFields);
    }

}

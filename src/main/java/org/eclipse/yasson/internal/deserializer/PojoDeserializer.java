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
import org.eclipse.yasson.internal.DeserializationContextManager;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Object container deserializer.
 */
class PojoDeserializer implements ModelUnmarshaller<JsonParser> {

    private final Map<String, ModelUnmarshaller<JsonParser>> propertyUnmarshallers;

    private final Function<String, String> nameMapper;

    private final Class<?> targetType;

    private final boolean throwOnUnknownFields;

    private final Set<String> excludedProperties;

    PojoDeserializer(Map<String, ModelUnmarshaller<JsonParser>> propertyUnmarshallers, Function<String, String> nameMapper, Class<?> targetType, boolean throwOnUnknownFields, Set<String> excludedProperties) {
        this.propertyUnmarshallers = Map.copyOf(propertyUnmarshallers);
        this.nameMapper = nameMapper;
        this.targetType = targetType;
        this.throwOnUnknownFields = throwOnUnknownFields;
        this.excludedProperties = Set.copyOf(excludedProperties);
    }

    @Override
    public Object unmarshal(JsonParser jsonInput, DeserializationContextManager ctxManager) {
        String propertyName = null;
        while (jsonInput.hasNext()) {
            final JsonParser.Event upcomingEvent = jsonInput.next();
            ctxManager.setLastValueEvent(upcomingEvent);
            switch(upcomingEvent) {
                case KEY_NAME:
                    propertyName = nameMapper.apply(jsonInput.getString());
                    break;
                case VALUE_NULL:
                case START_OBJECT:
                case START_ARRAY:
                case VALUE_STRING:
                case VALUE_NUMBER:
                case VALUE_FALSE:
                case VALUE_TRUE:
                    if (!propertyUnmarshallers.containsKey(propertyName)) {
                        if (throwOnUnknownFields && !excludedProperties.contains(propertyName)) {
                            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.UNKNOWN_JSON_PROPERTY, propertyName, targetType));
                        }
                    } else {
                        try {
                            propertyUnmarshallers.get(propertyName).deserialize(jsonInput, ctxManager);
                        } catch (JsonbException ex) {
                            throw new JsonbException("Unable to deserialize property '" + propertyName + "' because of: " + ex.getMessage(), ex);
                        }
                    }
                    break;
                case END_ARRAY:
                    break;
                case END_OBJECT:
                    return ctxManager.getInstance();
                default:
                    throw new JsonbException("Unexpected state: " + upcomingEvent);
            }
        }
        return ctxManager.getInstance();
    }
}

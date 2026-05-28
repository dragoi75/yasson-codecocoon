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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.DeserializationContextManager;
import org.eclipse.yasson.internal.model.CreatorProfile;
import org.eclipse.yasson.internal.model.JsonbCreatorInvoker;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;

/**
 * Creator of the Object instance with the usage of the {@link JsonbCreatorInvoker}.
 */
class JsonbCreatorInstantiator implements ModelUnmarshaller<JsonParser> {

    private final Map<String, ModelUnmarshaller<JsonParser>> propertyUnmarshallers;

    private final Map<String, ModelUnmarshaller<Object>> creatorDefaultValues;

    private final List<String> creatorParameters;

    private final Set<String> excludedProperties;

    private final JsonbCreatorInvoker creatorInvoker;

    private final Class<?> type;

    private final Function<String, String> nameMapper;

    private final boolean failOnUnknown;

    @Override
    public String toString() {
        return "ObjectInstanceCreator{" + "parameters=" + creatorParameters + ", clazz=" + type + '}';
    }

    @Override
    public Object unmarshal(JsonParser jsonParser, DeserializationContextManager deserializationContext) {
        String propertyName = null;
        Map<String, Object> paramValueMap = new HashMap<>();
        while (jsonParser.hasNext()) {
            final JsonParser.Event nextEvent = jsonParser.next();
            deserializationContext.setLastValueEvent(nextEvent);
            switch(nextEvent) {
                case KEY_NAME:
                    propertyName = nameMapper.apply(jsonParser.getString());
                    break;
                case VALUE_NULL:
                case START_OBJECT:
                case START_ARRAY:
                case VALUE_STRING:
                case VALUE_NUMBER:
                case VALUE_FALSE:
                case VALUE_TRUE:
                    if (!propertyUnmarshallers.containsKey(propertyName)) {
                        if (failOnUnknown && !excludedProperties.contains(propertyName)) {
                            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.UNKNOWN_JSON_PROPERTY, propertyName, type));
                        }
                    } else {
                        try {
                            Object value = propertyUnmarshallers.get(propertyName).deserialize(jsonParser, deserializationContext);
                            if (creatorParameters.contains(propertyName)) {
                                paramValueMap.put(propertyName, value);
                            }
                        } catch (JsonbException ex) {
                            throw new JsonbException("Unable to deserialize property '" + propertyName + "' because of: " + ex.getMessage(), ex);
                        }
                    }
                    break;
                case END_OBJECT:
                    Object[] arguments = new Object[creatorParameters.size()];
                    for (int index = 0; creatorParameters.size() > index; index += 1) {
                        String paramName = creatorParameters.get(index);
                        if (!paramValueMap.containsKey(paramName)) {
                            arguments[index] = creatorDefaultValues.get(paramName).deserialize(null, deserializationContext);
                        } else {
                            arguments[index] = paramValueMap.get(paramName);
                        }
                    }
                    deserializationContext.setInstance(creatorInvoker.invoke(arguments, type));
                    deserializationContext.getDeferredDeserializers().forEach(Runnable::run);
                    deserializationContext.getDeferredDeserializers().clear();
                    return deserializationContext.getInstance();
                default:
                    throw new JsonbException("Unexpected state: " + nextEvent);
            }
        }
        return deserializationContext.getInstance();
    }

    JsonbCreatorInstantiator(Map<String, ModelUnmarshaller<JsonParser>> propertyUnmarshallers, Map<String, ModelUnmarshaller<Object>> creatorDefaultValues, JsonbCreatorInvoker creatorInvoker, Class<?> type, Function<String, String> nameMapper, boolean failOnUnknown, Set<String> excludedProperties) {
        this.propertyUnmarshallers = propertyUnmarshallers;
        this.creatorDefaultValues = creatorDefaultValues;
        this.creatorParameters = Arrays.stream(creatorInvoker.getParams()).map(CreatorProfile::getName).collect(Collectors.toList());
        this.excludedProperties = Set.copyOf(excludedProperties);
        this.creatorInvoker = creatorInvoker;
        this.type = type;
        this.nameMapper = nameMapper;
        this.failOnUnknown = failOnUnknown;
    }

}

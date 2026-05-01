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

import org.eclipse.yasson.internal.DeserializationContextImpl;
import org.eclipse.yasson.internal.model.CreatorModel;
import org.eclipse.yasson.internal.model.JsonbCreator;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

/**
 * Creator of the Object instance with the usage of the {@link JsonbCreator}.
 */
class JsonbCreatorBasedDeserializer implements ModelDeserializer<JsonParser> {

    private final Map<String, ModelDeserializer<JsonParser>> fieldParserPipelines;
    private final Map<String, ModelDeserializer<Object>> fallbackParamDeserializers;
    private final List<String> constructorParams;
    private final Set<String> excludedFields;
    private final JsonbCreator creationAnnotation;
    private final Class<?> targetType;
    private final Function<String, String> nameMapper;
    private final boolean rejectUnknownProps;

    JsonbCreatorBasedDeserializer(Map<String, ModelDeserializer<JsonParser>> fieldParserPipelines,
                                  Map<String, ModelDeserializer<Object>> fallbackParamDeserializers,
                                  JsonbCreator creationAnnotation,
                                  Class<?> targetType,
                                  Function<String, String> nameMapper,
                                  boolean rejectUnknownProps,
                                  Set<String> excludedFields) {
        this.fieldParserPipelines = fieldParserPipelines;
        this.fallbackParamDeserializers = fallbackParamDeserializers;
        this.constructorParams = Arrays.stream(creationAnnotation.getParams()).map(CreatorModel::getName).collect(Collectors.toList());
        this.excludedFields = Set.copyOf(excludedFields);
        this.creationAnnotation = creationAnnotation;
        this.targetType = targetType;
        this.nameMapper = nameMapper;
        this.rejectUnknownProps = rejectUnknownProps;
    }

    @Override
    public Object deserialize(JsonParser jsonParser, DeserializationContextImpl deserializationContext) {
        String mapKey = null;
        Map<String, Object> paramValueMap = new HashMap<>();
        while (jsonParser.hasNext()) {
            final JsonParser.Event nextEvent = jsonParser.next();
            deserializationContext.setLastValueEvent(nextEvent);
            switch (nextEvent) {
            case KEY_NAME:
                mapKey = nameMapper.apply(jsonParser.getString());
                break;
            case VALUE_NULL:
            case START_OBJECT:
            case START_ARRAY:
            case VALUE_STRING:
            case VALUE_NUMBER:
            case VALUE_FALSE:
            case VALUE_TRUE:
                if (fieldParserPipelines.containsKey(mapKey)) {
                    try {
                        Object value = fieldParserPipelines.get(mapKey).deserialize(jsonParser, deserializationContext);
                        if (constructorParams.contains(mapKey)) {
                            paramValueMap.put(mapKey, value);
                        }
                    } catch (JsonbException ex) {
                        throw new JsonbException("Unable to deserialize property '" + mapKey + "' because of: " + ex.getMessage(), ex);
                    }
                } else if (rejectUnknownProps && !excludedFields.contains(mapKey)) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.UNKNOWN_JSON_PROPERTY, mapKey, targetType));
                }
                break;
            case END_OBJECT:
                Object[] argsArray = new Object[constructorParams.size()];
                for (int idx = 0; idx < constructorParams.size(); idx++) {
                    String paramName = constructorParams.get(idx);
                    if (paramValueMap.containsKey(paramName)) {
                        argsArray[idx] = paramValueMap.get(paramName);
                    } else {
                        argsArray[idx] = fallbackParamDeserializers.get(paramName).deserialize(null, deserializationContext);
                    }
                }
                deserializationContext.setInstance(creationAnnotation.call(argsArray, targetType));
                deserializationContext.getDeferredDeserializers().forEach(Runnable::run);
                deserializationContext.getDeferredDeserializers().clear();
                return deserializationContext.getInstance();
            default:
                throw new JsonbException("Unexpected state: " + nextEvent);
            }
        }
        return deserializationContext.getInstance();
    }

    @Override
    public String toString() {
        return "ObjectInstanceCreator{"
                + "parameters=" + constructorParams
                + ", clazz=" + targetType
                + '}';
    }
}

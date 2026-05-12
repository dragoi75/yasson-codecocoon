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
import java.util.concurrent.ConcurrentHashMap;

import jakarta.json.JsonObject;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.DefaultDeserializationContext;
import org.eclipse.yasson.internal.jsonstructure.JsonStructureParserAdapter;
import org.eclipse.yasson.internal.model.customization.TypeInheritanceSettings;

import static jakarta.json.stream.JsonParser.Event;

/**
 * Instance creator following the inheritance structure defined by {@link jakarta.json.bind.annotation.JsonbTypeInfo}.
 */
class PolymorphicInstanceFactory implements ModelParser<JsonParser> {

    private final Class<?> targetClass;
    private final Map<String, Class<?>> classRegistry = new ConcurrentHashMap<>();
    private final DeserializationModelFactory modelFactory;
    private final TypeInheritanceSettings inheritanceSettings;
    private final ModelParser<JsonParser> fallbackParser;

    PolymorphicInstanceFactory(Class<?> targetClass,
                               DeserializationModelFactory modelFactory,
                               TypeInheritanceSettings inheritanceSettings,
                               ModelParser<JsonParser> fallbackParser) {
        this.targetClass = targetClass;
        this.modelFactory = modelFactory;
        this.inheritanceSettings = inheritanceSettings;
        this.fallbackParser = fallbackParser;
    }

    @Override
    public Object deserializeModel(JsonParser jsonInput, DefaultDeserializationContext deserializationState) {
        String typeKey;
        JsonParser internalParser;
        String inheritanceKey = inheritanceSettings.getFieldName();
        JsonObject jsonNode = jsonInput.getObject();
        typeKey = jsonNode.getString(inheritanceKey, null);
        JsonObject transformedNode = deserializationState.getJsonbContext().getJsonProvider().createObjectBuilder(jsonNode)
                .remove(inheritanceKey)
                .build();
        internalParser = new JsonStructureParserAdapter(transformedNode);
        //To get to the first event
        Event evt = internalParser.next();
        deserializationState.setLastValueEvent(evt);
        Class<?> subtypeClass;
        if (typeKey == null) {
            return fallbackParser.deserializeModel(internalParser, deserializationState);
        }
        subtypeClass = getPolymorphicTypeClass(typeKey);
        if (subtypeClass.equals(targetClass)) {
            return fallbackParser.deserializeModel(internalParser, deserializationState);
        }
        ModelParser<JsonParser> modelParser = modelFactory.createDeserializerChain(subtypeClass);
        return modelParser.deserializeModel(internalParser, deserializationState);
    }

    @Override
    public String toString() {
        return "Property " + inheritanceSettings.getFieldName() + " polymorphic information handler";
    }

    private Class<?> getPolymorphicTypeClass(String typeKey) {
        if (classRegistry.containsKey(typeKey)) {
            return classRegistry.get(typeKey);
        }
        for (Map.Entry<Class<?>, String> mappingPair : inheritanceSettings.getAliases().entrySet()) {
            if (mappingPair.getValue().equals(typeKey)) {
                classRegistry.put(typeKey, mappingPair.getKey());
                return mappingPair.getKey();
            }
        }
        throw new JsonbException("Unknown alias \"" + typeKey + "\" known aliases: "
                                         + inheritanceSettings.getAliases().values());
    }

}

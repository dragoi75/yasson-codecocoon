/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.internal.JsonbContextManager;
import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.JsonbRiParser;
import org.eclipse.yasson.internal.ReflectionUtils;
import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.BeanPropertyModel;
import org.eclipse.yasson.internal.model.CreatorProfile;
import org.eclipse.yasson.internal.model.JsonbInstantiator;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Item for handling all types of unknown objects by reflection, parsing their fields, according to json key name.
 *
 * @param <T> object type
 */
class ObjectDeserializer<T> extends AbstractContainerDeserializer<T> {

    /**
     * Last property model cache to avoid lookup by jsonKey on every access.
     */
    private static class LastPropertyModel {

        private final String jsonKeyName;
        private final BeanPropertyModel propertyModel;

        LastPropertyModel(String jsonKeyName, BeanPropertyModel propertyModel) {
            this.jsonKeyName = jsonKeyName;
            this.propertyModel = propertyModel;
        }

        public String getJsonKeyName() {
            return jsonKeyName;
        }

        public BeanPropertyModel getPropertyModel() {
            return propertyModel;
        }
    }

    private Map<String, ValueWrapper> values = new LinkedHashMap<>();

    private T instance;

    private LastPropertyModel lastPropertyModel;

    /**
     * Creates instance of an item.
     *
     * @param builder builder to build from
     */
    protected ObjectDeserializer(DeserializerBuilder builder) {
        super(builder);
    }

    /**
     * Due to support of custom (parametrized) constructors and factory methods, values are held in map,
     * which is transferred into instance values by calling getInstance.
     *
     * @param unmarshaller Current deserialization context.
     * @return An instance of deserializing item.
     */
    @Override
    @SuppressWarnings("unchecked")
    public T getInstance(Unmarshaller unmarshaller) {
        if (instance != null) {
            return instance;
        }
        final Class<?> rawType = ReflectionUtils.getRawType(getRuntimeType());
        final JsonbInstantiator creator = getClassModel().getClassCustomization().getCreator();
        if (creator != null) {
            instance = createInstance((Class<T>) rawType, creator);
        } else {
            Constructor<T> defaultConstructor = (Constructor<T>) getClassModel().getDefaultConstructor();
            if (defaultConstructor == null) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.NO_DEFAULT_CONSTRUCTOR, rawType));
            }
            instance = ReflectionUtils.createNoArgConstructorInstance(defaultConstructor);
        }
        //values must be set in order, in which they appears in JSON by spec
        values.forEach((key, wrapper) -> {
            //skip creator values
            if (wrapper.getCreatorModel() != null) {
                return;
            }
            final BeanPropertyModel propertyModel = wrapper.getPropertyModel();
            propertyModel.setValue(instance, wrapper.getValue());
        });

        return instance;
    }

    /**
     * Creates instance with custom jsonb creator (parameterized constructor or factory method).
     */
    private T createInstance(Class<T> rawType, JsonbInstantiator creator) {
        final T instance;
        final List<Object> paramValues = new ArrayList<>();
        for (CreatorProfile param : creator.getParams()) {
            final ValueWrapper valueWrapper = values.get(param.getName());
            //required by spec
            if (valueWrapper == null) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CREATOR_MISSING_PROPERTY, param.getName()));
            }
            paramValues.add(valueWrapper.getValue());
        }
        instance = creator.invoke(paramValues.toArray(), rawType);
        return instance;
    }

    /**
     * Set populated instance of current object to its unfinished wrapper values map.
     *
     * @param result An instance result of an item.
     */
    @Override
    public void appendResult(Object result) {
        final BeanPropertyModel model = getModel();
        //missing property for null values
        if (model == null) {
            return;
        }
        values.put(model.getReadName(),
                   new ValueWrapper(model, convertNullToOptionalEmpty(model.getPropertyDeserializationType(), result)));
    }

    @Override
    protected void deserializeNext(JsonParser parser, Unmarshaller context) {

        final JsonbInstantiator creator = getClassModel().getClassCustomization().getCreator();
        //first check jsonb creator param, since it can be different from property name
        if (creator != null) {
            final CreatorProfile param = creator.findParameterByName(getParserContext().getLastKeyName());
            if (param != null) {
                final JsonbDeserializer<?> deserializer = newUnmarshallerItemBuilder(context.getJsonbContext())
                        .withType(param.getType())
                        .withCustomization(param.getCustomization())
                        .build();
                Object result = deserializer.deserialize(parser, context, param.getType());
                values.put(param.getName(), new ValueWrapper(param, result));
                return;
            }
        }

        //identify field model of currently processed class model
        BeanPropertyModel newPropertyModel = getModel();
        if (newPropertyModel != null && newPropertyModel.isWritable()) {
            //create current item instance of identified object field
            final JsonbDeserializer<?> deserializer = newUnmarshallerItemBuilder(context.getJsonbContext())
                    .withCustomization(newPropertyModel.getCustomization())
                    .withType(newPropertyModel.getPropertyDeserializationType())
                    .build();

            Type resolvedType = ReflectionUtils.resolveType(this, newPropertyModel.getPropertyDeserializationType());
            Object result = deserializer.deserialize(parser, context, resolvedType);
            values.put(newPropertyModel.getPropertyName(), new ValueWrapper(newPropertyModel, result));
            return;
        }
        skipJsonProperty((JsonbParser) parser, context.getJsonbContext());
    }

    /**
     * Rise an exception, or ignore JSON property, which is missing in class model.
     */
    private void skipJsonProperty(JsonbParser parser, JsonbContextManager jsonbContext) {
        if (jsonbContext.getConfigProperties().getConfigFailOnUnknownProperties()) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.UNKNOWN_JSON_PROPERTY,
                                                         getParserContext().getLastKeyName(),
                                                         getRuntimeType()));
        }
        parser.skipJsonStructure();
    }

    @Override
    protected JsonbRiParser.LevelContext moveToFirst(JsonbParser parser) {
        parser.moveTo(JsonParser.Event.START_OBJECT);
        return parser.getCurrentLevel();
    }

    protected BeanPropertyModel getModel() {
        final String lastKeyName = getParserContext().getLastKeyName();
        if (lastPropertyModel != null && lastPropertyModel.getJsonKeyName().equals(lastKeyName)) {
            return lastPropertyModel.getPropertyModel();
        }
        lastPropertyModel = new LastPropertyModel(lastKeyName, getClassModel().getPropertyModelByJsonReadName(lastKeyName));
        return lastPropertyModel.getPropertyModel();
    }

    private static class ValueWrapper {

        private final CreatorProfile creatorModel;
        private final BeanPropertyModel propertyModel;
        private final Object value;

        ValueWrapper(CreatorProfile creator, Object value) {
            this.creatorModel = creator;
            this.value = value;
            propertyModel = null;
        }

        ValueWrapper(BeanPropertyModel propertyModel, Object value) {
            this.propertyModel = propertyModel;
            this.value = value;
            creatorModel = null;
        }

        public CreatorProfile getCreatorModel() {
            return creatorModel;
        }

        public BeanPropertyModel getPropertyModel() {
            return propertyModel;
        }

        public Object getValue() {
            return value;
        }
    }
}

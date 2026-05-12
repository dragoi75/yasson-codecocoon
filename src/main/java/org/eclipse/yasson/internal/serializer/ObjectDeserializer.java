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
import org.eclipse.yasson.internal.JsonbNavigator;
import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.JsonbStreamingParser;
import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.ReflectionTypeResolver;
import org.eclipse.yasson.internal.model.CreatorProfile;
import org.eclipse.yasson.internal.model.JsonbCreatorInvoker;
import org.eclipse.yasson.internal.model.PropertyModel;
import org.eclipse.yasson.internal.properties.MessageBundle;
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

        private final PropertyModel propertyModel;

        LastPropertyModel(String jsonKeyName, PropertyModel propertyModel) {
            this.jsonKeyName = jsonKeyName;
            this.propertyModel = propertyModel;
        }

        public String getJsonKeyName() {
            return jsonKeyName;
        }

        public PropertyModel getPropertyModel() {
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
    protected ObjectDeserializer(JsonDeserializerBuilder builder) {
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
    public T getInstance(JsonbUnmarshaller unmarshaller) {
        if (null != instance) {
            return instance;
        }
        final Class<?> rawType = ReflectionTypeResolver.getRawType(getRuntimeType());
        final JsonbCreatorInvoker creator = getClassModel().getClassCustomization().getCreator();
        if (null == creator) {
            Constructor<T> defaultConstructor = (Constructor<T>) getClassModel().getDefaultConstructor();
            if (null == defaultConstructor) {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.NO_DEFAULT_CONSTRUCTOR, rawType));
            }
            instance = ReflectionTypeResolver.instantiateNoArgs(defaultConstructor);
        } else {
            instance = createInstance((Class<T>) rawType, creator);
        }
        //values must be set in order, in which they appears in JSON by spec
        values.forEach((key, wrapper) -> {
            //skip creator values
            if (null != wrapper.getCreatorModel()) {
                return;
            }
            final PropertyModel propertyModel = wrapper.getPropertyModel();
            propertyModel.setValue(instance, wrapper.getValue());
        });
        return instance;
    }

    /**
     * Creates instance with custom jsonb creator (parameterized constructor or factory method).
     */
    private T createInstance(Class<T> rawType, JsonbCreatorInvoker creator) {
        final T instance;
        final List<Object> paramValues = new ArrayList<>();
        for (CreatorProfile param : creator.getParams()) {
            final ValueWrapper valueWrapper = values.get(param.getName());
            //required by spec
            if (null == valueWrapper) {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.JSONB_CREATOR_MISSING_PROPERTY, param.getName()));
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
        final PropertyModel model = getModel();
        //missing property for null values
        if (null == model) {
            return;
        }
        values.put(model.getReadName(), new ValueWrapper(model, convertNullToOptionalEmpty(model.getPropertyDeserializationType(), result)));
    }

    @Override
    protected void deserializeNext(JsonParser parser, JsonbUnmarshaller context) {
        final JsonbCreatorInvoker creator = getClassModel().getClassCustomization().getCreator();
        //first check jsonb creator param, since it can be different from property name
        if (null != creator) {
            final CreatorProfile param = creator.findByParamName(getParserContext().getLastKeyName());
            if (null != param) {
                final JsonbDeserializer<?> deserializer = newUnmarshallerItemBuilder(context.getJsonbContext()).setType(param.getType()).setCustomization(param.getCustomization()).buildDeserializer();
                Object result = deserializer.deserialize(parser, context, param.getType());
                values.put(param.getName(), new ValueWrapper(param, result));
                return;
            }
        }
        //identify field model of currently processed class model
        PropertyModel newPropertyModel = getModel();
        if (null != newPropertyModel && newPropertyModel.isWritable()) {
            //create current item instance of identified object field
            final JsonbDeserializer<?> deserializer = newUnmarshallerItemBuilder(context.getJsonbContext()).setCustomization(newPropertyModel.getCustomization()).setType(newPropertyModel.getPropertyDeserializationType()).buildDeserializer();
            Type resolvedType = ReflectionTypeResolver.resolveTypeDefault(this, newPropertyModel.getPropertyDeserializationType());
            Object result = deserializer.deserialize(parser, context, resolvedType);
            values.put(newPropertyModel.getPropertyName(), new ValueWrapper(newPropertyModel, result));
            return;
        }
        skipJsonProperty((JsonbNavigator) parser, context.getJsonbContext());
    }

    /**
     * Rise an exception, or ignore JSON property, which is missing in class model.
     */
    private void skipJsonProperty(JsonbNavigator parser, JsonbRuntimeContext jsonbContext) {
        if (jsonbContext.getConfigProperties().getConfigFailOnUnknownProperties()) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.UNKNOWN_JSON_PROPERTY, getParserContext().getLastKeyName(), getRuntimeType()));
        }
        parser.skipJsonStructure();
    }

    @Override
    protected JsonbStreamingParser.LevelParseContext moveToFirst(JsonbNavigator parser) {
        parser.moveTo(JsonParser.Event.START_OBJECT);
        return parser.getCurrentLevel();
    }

    protected PropertyModel getModel() {
        final String lastKeyName = getParserContext().getLastKeyName();
        if (null != lastPropertyModel && lastPropertyModel.getJsonKeyName().equals(lastKeyName)) {
            return lastPropertyModel.getPropertyModel();
        }
        lastPropertyModel = new LastPropertyModel(lastKeyName, getClassModel().findPropertyByJsonReadName(lastKeyName));
        return lastPropertyModel.getPropertyModel();
    }

    private static class ValueWrapper {

        private final CreatorProfile creatorModel;

        private final PropertyModel propertyModel;

        private final Object value;

        ValueWrapper(CreatorProfile creator, Object value) {
            this.creatorModel = creator;
            this.value = value;
            propertyModel = null;
        }

        ValueWrapper(PropertyModel propertyModel, Object value) {
            this.propertyModel = propertyModel;
            this.value = value;
            creatorModel = null;
        }

        public CreatorProfile getCreatorModel() {
            return creatorModel;
        }

        public PropertyModel getPropertyModel() {
            return propertyModel;
        }

        public Object getValue() {
            return value;
        }
    }
}

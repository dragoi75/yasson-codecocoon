/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.*;
import org.eclipse.yasson.internal.properties.MessageKey;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.model.CreatorModel;
import org.eclipse.yasson.internal.model.JsonbCreator;
import org.eclipse.yasson.internal.model.PropertyModel;
import javax.json.bind.JsonbException;
import javax.json.stream.JsonParser;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Item for handling all types of unknown objects by reflection, parsing their fields, according to json key name.
 *
 * @author Roman Grigoriadi
 */
class ObjectDeserializer<T> extends AbstractCollectionDeserializer<T> {

    /**
     * Last property model cache to avoid lookup by jsonKey on every access.
     */
    private static class LastPropertyModel {

        private final String jsonKeyName;

        private final PropertyModel propertyModel;

        public PropertyModel getPropertyModel() {
            return propertyModel;
        }

        public LastPropertyModel(String jsonKeyName, PropertyModel propertyModel) {
            this.jsonKeyName = jsonKeyName;
            this.propertyModel = propertyModel;
        }

        public String getJsonKeyName() {
            return jsonKeyName;
        }

    }

    private static final Logger log = Logger.getLogger(ObjectDeserializer.class.getName());

    private Map<String, ValueWrapper> values = new LinkedHashMap<>();

    private T instance;

    private LastPropertyModel lastPropertyModel;

    private static class ValueWrapper {

        private final CreatorModel creatorModel;

        private final PropertyModel propertyModel;

        private final Object value;

        public Object getValue() {
            return value;
        }

        public PropertyModel getPropertyModel() {
            return propertyModel;
        }

        public ValueWrapper(CreatorModel creator, Object value) {
            this.creatorModel = creator;
            this.value = value;
            propertyModel = null;
        }

        public ValueWrapper(PropertyModel propertyModel, Object value) {
            this.propertyModel = propertyModel;
            this.value = value;
            creatorModel = null;
        }

        public CreatorModel getCreatorModel() {
            return creatorModel;
        }

    }

    /**
     * Creates instance with custom jsonb creator (parameterized constructor or factory method)
     */
    private T createInstance(Class<T> rawType, JsonbCreator creator) {
        final T instance;
        final List<Object> paramValues = new ArrayList<>();
        for (CreatorModel param : creator.getParams()) {
            final ValueWrapper valueWrapper = values.get(param.getName());
            //required by spec
            if (null == valueWrapper) {
                throw new JsonbException(MessageBundle.getMessage(MessageKey.JSONB_CREATOR_MISSING_PROPERTY, param.getName()));
            }
            paramValues.add(valueWrapper.getValue());
        }
        instance = creator.call(paramValues.toArray(), rawType);
        return instance;
    }

    /**
     * Rise an exception, or ignore JSON property, which is missing in class model.
     */
    private void skipJsonProperty(JsonbStructureNavigator parser, JsonbRuntimeContext jsonbContext) {
        if (jsonbContext.getConfigProperties().getConfigFailOnUnknownProperties()) {
            throw new JsonbException(MessageBundle.getMessage(MessageKey.UNKNOWN_JSON_PROPERTY, parserContext.getLastKeyName(), getRuntimeType()));
        }
        parser.skipJsonStructure();
    }

    protected PropertyModel getModel() {
        final String lastKeyName = parserContext.getLastKeyName();
        if (null != lastPropertyModel && lastPropertyModel.getJsonKeyName().equals(lastKeyName)) {
            return lastPropertyModel.getPropertyModel();
        }
        lastPropertyModel = new LastPropertyModel(lastKeyName, getClassModel().locatePropertyModelByJsonReadName(lastKeyName));
        return lastPropertyModel.getPropertyModel();
    }

    @Override
    protected JsonbRiStreamParser.ParsingLevelContext moveToFirstElement(JsonbStructureNavigator parser) {
        parser.moveTo(JsonParser.Event.START_OBJECT);
        return parser.getCurrentLevel();
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
    public T getInstance(JsonbDeserializer unmarshaller) {
        if (null != instance) {
            return instance;
        }
        final Class<?> rawType = ReflectionTypeResolver.getRawType(getRuntimeType());
        final JsonbCreator creator = getClassModel().getClassCustomization().getCreator();
        instance = null != creator ? createInstance((Class<T>) rawType, creator) : ReflectionTypeResolver.createNoArgInstance((Constructor<T>) getClassModel().getDefaultConstructor());
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

    @Override
    protected void deserializeItem(JsonParser parser, JsonbDeserializer context) {
        final JsonbCreator creator = getClassModel().getClassCustomization().getCreator();
        //first check jsonb creator param, since it can be different from property name
        if (null != creator) {
            final CreatorModel param = creator.findByName(parserContext.getLastKeyName());
            if (null != param) {
                final javax.json.bind.serializer.JsonbDeserializer<?> deserializer = createUnmarshallerItemBuilder(context.getJsonbContext()).setType(param.getType()).setCustomization(param.getCustomization()).buildDeserializer();
                Object result = deserializer.deserialize(parser, context, param.getType());
                values.put(param.getName(), new ValueWrapper(param, result));
                return;
            }
        }
        //identify field model of currently processed class model
        PropertyModel newPropertyModel = getModel();
        if (null != newPropertyModel && newPropertyModel.isWritable()) {
            //create current item instance of identified object field
            final javax.json.bind.serializer.JsonbDeserializer<?> deserializer = createUnmarshallerItemBuilder(context.getJsonbContext()).setCustomization(newPropertyModel.getCustomization()).setType(newPropertyModel.getPropertyDeserializationType()).buildDeserializer();
            Type resolvedType = ReflectionTypeResolver.resolveActualType(this, newPropertyModel.getPropertyDeserializationType());
            Object result = deserializer.deserialize(parser, context, resolvedType);
            values.put(newPropertyModel.getPropertyName(), new ValueWrapper(newPropertyModel, result));
            return;
        }
        skipJsonProperty((JsonbStructureNavigator) parser, context.getJsonbContext());
    }

    /**
     * Creates instance of an item.
     * @param builder builder to build from
     */
    protected ObjectDeserializer(JsonDeserializerBuilder builder) {
        super(builder);
    }

    /**
     * Set populated instance of current object to its unfinished wrapper values map.
     *
     * @param result An instance result of an item.
     */
    @Override
    public void appendValueToResult(Object result) {
        final PropertyModel model = getModel();
        //missing property for null values
        if (null == model) {
            return;
        }
        values.put(model.getReadName(), new ValueWrapper(model, convertNullToEmptyOptional(model.getPropertyType(), result)));
    }

}

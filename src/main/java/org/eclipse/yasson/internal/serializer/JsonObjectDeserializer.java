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
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;
import org.eclipse.yasson.internal.model.CreatorModel;
import org.eclipse.yasson.internal.model.JsonbCreator;
import org.eclipse.yasson.internal.model.PropertyModel;
import javax.json.bind.JsonbException;
import javax.json.bind.serializer.JsonbDeserializer;
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
class JsonObjectDeserializer<T> extends BaseContainerDeserializer<T> {

    /**
     * Last property model cache to avoid lookup by jsonKey on every access.
     */
    private static class LeafPropertyModel {

        private final String jsonField;

        private final PropertyModel propertyDescriptor;

        public String getJsonKeyName() {
            return jsonField;
        }

        public PropertyModel getPropertyModel() {
            return propertyDescriptor;
        }

        public LeafPropertyModel(String jsonField, PropertyModel propertyDescriptor) {
            this.jsonField = jsonField;
            this.propertyDescriptor = propertyDescriptor;
        }

    }

    private static final Logger log = Logger.getLogger(JsonObjectDeserializer.class.getName());

    private Map<String, ValueHolder> valueMap = new LinkedHashMap<>();

    private T objectInstance;

    private LeafPropertyModel lastLeafProperty;

    private static class ValueHolder {

        private final CreatorModel creatorInfo;

        private final PropertyModel propertyDescriptor;

        private final Object storedValue;

        public CreatorModel getCreatorModel() {
            return creatorInfo;
        }

        public Object getValue() {
            return storedValue;
        }

        public PropertyModel getPropertyModel() {
            return propertyDescriptor;
        }

        public ValueHolder(CreatorModel creationMeta, Object storedValue) {
            this.creatorInfo = creationMeta;
            this.storedValue = storedValue;
            propertyDescriptor = null;
        }

        public ValueHolder(PropertyModel propertyDescriptor, Object storedValue) {
            this.propertyDescriptor = propertyDescriptor;
            this.storedValue = storedValue;
            creatorInfo = null;
        }

    }

    /**
     * Rise an exception, or ignore JSON property, which is missing in class model.
     */
    private void skipUnknownProperty(JsonbCursor jsonStreamParser, JsonbRuntimeContext runtimeContext) {
        if (runtimeContext.getConfigProperties().getConfigFailOnUnknownProperties()) {
            throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.UNKNOWN_JSON_PROPERTY, parserContext.getLastKeyName(), getRuntimeType()));
        }
        jsonStreamParser.skipJsonStructure();
    }

    @Override
    protected JsonbStreamingParser.LevelParseContext moveToFirstToken(JsonbCursor jsonStreamParser) {
        jsonStreamParser.moveTo(JsonParser.Event.START_OBJECT);
        return jsonStreamParser.getCurrentLevel();
    }

    protected PropertyModel getModel() {
        final String previousKeyName = parserContext.getLastKeyName();
        if (null != lastLeafProperty && lastLeafProperty.getJsonKeyName().equals(previousKeyName)) {
            return lastLeafProperty.getPropertyModel();
        }
        lastLeafProperty = new LeafPropertyModel(previousKeyName, getClassModel().getPropertyModelByJsonReadName(previousKeyName));
        return lastLeafProperty.getPropertyModel();
    }

    @Override
    protected void deserializeNextValue(JsonParser jsonStreamParser, JsonbUnmarshaller unmarshalContext) {
        final JsonbCreator creationMeta = getClassModel().getClassCustomization().getCreator();
        //first check jsonb creator param, since it can be different from property name
        if (null != creationMeta) {
            final CreatorModel creatorArg = creationMeta.findByName(parserContext.getLastKeyName());
            if (null != creatorArg) {
                final JsonbDeserializer<?> valueDeserializer = createUnmarshallerItemBuilder(unmarshalContext.getJsonbContext()).setType(creatorArg.getType()).setCustomization(creatorArg.getCustomization()).buildDeserializer();
                Object outcome = valueDeserializer.deserialize(jsonStreamParser, unmarshalContext, creatorArg.getType());
                valueMap.put(creatorArg.getName(), new ValueHolder(creatorArg, outcome));
                return;
            }
        }
        //identify field model of currently processed class model
        PropertyModel createdProperty = getModel();
        if (null != createdProperty && createdProperty.isWritable()) {
            //create current item instance of identified object field
            final JsonbDeserializer<?> valueDeserializer = createUnmarshallerItemBuilder(unmarshalContext.getJsonbContext()).setCustomization(createdProperty.getCustomization()).setType(createdProperty.getPropertyDeserializationType()).buildDeserializer();
            Type resolvedGenericType = ReflectiveTypeUtils.resolveGenericType(this, createdProperty.getPropertyDeserializationType());
            Object outcome = valueDeserializer.deserialize(jsonStreamParser, unmarshalContext, resolvedGenericType);
            valueMap.put(createdProperty.getPropertyName(), new ValueHolder(createdProperty, outcome));
            return;
        }
        skipUnknownProperty((JsonbCursor) jsonStreamParser, unmarshalContext.getJsonbContext());
    }

    /**
     * Creates instance of an item.
     * @param deserializerFactory builder to build from
     */
    protected JsonObjectDeserializer(JsonValueDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }

    /**
     * Set populated instance of current object to its unfinished wrapper values map.
     *
     * @param outcome An instance result of an item.
     */
    @Override
    public void addResult(Object outcome) {
        final PropertyModel propertyDesc = getModel();
        //missing property for null values
        if (null == propertyDesc) {
            return;
        }
        valueMap.put(propertyDesc.getReadName(), new ValueHolder(propertyDesc, nullToOptionalEmpty(propertyDesc.getPropertyType(), outcome)));
    }

    /**
     * Due to support of custom (parametrized) constructors and factory methods, values are held in map,
     * which is transferred into instance values by calling getInstance.
     *
     * @param unmarshalContext Current deserialization context.
     * @return An instance of deserializing item.
     */
    @Override
    @SuppressWarnings("unchecked")
    public T getInstance(JsonbUnmarshaller unmarshalContext) {
        if (null != objectInstance) {
            return objectInstance;
        }
        final Class<?> resolvedRawType = ReflectiveTypeUtils.getRawType(getRuntimeType());
        final JsonbCreator creationMeta = getClassModel().getClassCustomization().getCreator();
        objectInstance = null != creationMeta ? instantiate((Class<T>) resolvedRawType, creationMeta) : ReflectiveTypeUtils.instantiateNoArgConstructor((Constructor<T>) getClassModel().getDefaultConstructor());
        //values must be set in order, in which they appears in JSON by spec
        valueMap.forEach((key, holder) -> {
            //skip creator values
            if (null != holder.getCreatorModel()) {
                return;
            }
            final PropertyModel propertyDescriptor = holder.getPropertyModel();
            propertyDescriptor.setValue(objectInstance, holder.getValue());
        });
        return objectInstance;
    }

    /**
     * Creates instance with custom jsonb creator (parameterized constructor or factory method)
     */
    private T instantiate(Class<T> resolvedRawType, JsonbCreator creationMeta) {
        final T objectInstance;
        final List<Object> constructorArgs = new ArrayList<>();
        for (CreatorModel creatorArg : creationMeta.getParams()) {
            final ValueHolder holder = valueMap.get(creatorArg.getName());
            //required by spec
            if (null == holder) {
                throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.JSONB_CREATOR_MISSING_PROPERTY, creatorArg.getName()));
            }
            constructorArgs.add(holder.getValue());
        }
        objectInstance = creationMeta.call(constructorArgs.toArray(), resolvedRawType);
        return objectInstance;
    }

}

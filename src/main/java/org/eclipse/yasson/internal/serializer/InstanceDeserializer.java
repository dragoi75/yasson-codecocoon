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
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.model.CreatorModel;
import org.eclipse.yasson.internal.model.JsonbCreator;
import org.eclipse.yasson.internal.model.PropertyModel;
import javax.json.bind.JsonbException;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
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
class InstanceDeserializer<T> extends BaseContainerDeserializer<T> {

    /**
     * Last property model cache to avoid lookup by jsonKey on every access.
     */
    private static class LastJsonPropertyModel {

        private final String lastJsonKey;

        private final PropertyModel lastPropertyDescriptor;

        public PropertyModel getPropertyModel() {
            return lastPropertyDescriptor;
        }

        public LastJsonPropertyModel(String lastJsonKey, PropertyModel lastPropertyDescriptor) {
            this.lastJsonKey = lastJsonKey;
            this.lastPropertyDescriptor = lastPropertyDescriptor;
        }

        public String getJsonKeyName() {
            return lastJsonKey;
        }

    }

    private static final Logger log = Logger.getLogger(InstanceDeserializer.class.getName());

    private Map<String, ValueHolder> valueMap = new LinkedHashMap<>();

    private T targetInstance;

    private LastJsonPropertyModel lastPropertyInfo;

    private static class ValueHolder {

        private final CreatorModel creationModel;

        private final PropertyModel lastPropertyDescriptor;

        private final Object heldValue;

        public PropertyModel getPropertyModel() {
            return lastPropertyDescriptor;
        }

        public Object getValue() {
            return heldValue;
        }

        public ValueHolder(CreatorModel creationInfo, Object heldValue) {
            this.creationModel = creationInfo;
            this.heldValue = heldValue;
            lastPropertyDescriptor = null;
        }

        public ValueHolder(PropertyModel lastPropertyDescriptor, Object heldValue) {
            this.lastPropertyDescriptor = lastPropertyDescriptor;
            this.heldValue = heldValue;
            creationModel = null;
        }

        public CreatorModel getCreatorModel() {
            return creationModel;
        }

    }

    @Override
    protected void deserializeNextValue(JsonParser jsonStream, Unmarshaller unmarshalEnv) {
        final JsonbCreator creationInfo = getClassModel().getClassCustomization().getCreator();
        //first check jsonb creator param, since it can be different from property name
        if (null != creationInfo) {
            final CreatorModel creatorInfo = creationInfo.findByName(parserContext.getLastKeyName());
            if (null != creatorInfo) {
                final JsonbDeserializer<?> valueReader = createUnmarshallerItemBuilder(unmarshalEnv.getJsonbContext()).setType(creatorInfo.getType()).setCustomization(creatorInfo.getCustomization()).buildDeserializer();
                Object outcome = valueReader.deserialize(jsonStream, unmarshalEnv, creatorInfo.getType());
                valueMap.put(creatorInfo.getName(), new ValueHolder(creatorInfo, outcome));
                return;
            }
        }
        //identify field model of currently processed class model
        PropertyModel createdPropertyDescriptor = getModel();
        if (null != createdPropertyDescriptor && createdPropertyDescriptor.isWritable()) {
            //create current item instance of identified object field
            final JsonbDeserializer<?> valueReader = createUnmarshallerItemBuilder(unmarshalEnv.getJsonbContext()).setCustomization(createdPropertyDescriptor.getCustomization()).setType(createdPropertyDescriptor.getPropertyDeserializationType()).buildDeserializer();
            Type concreteType = ReflectionTypeResolver.resolveActualType(this, createdPropertyDescriptor.getPropertyDeserializationType());
            Object outcome = valueReader.deserialize(jsonStream, unmarshalEnv, concreteType);
            valueMap.put(createdPropertyDescriptor.getPropertyName(), new ValueHolder(createdPropertyDescriptor, outcome));
            return;
        }
        skipUnknownProperty((JsonbParser) jsonStream, unmarshalEnv.getJsonbContext());
    }

    @Override
    protected JsonbRiParser.LevelContext advanceToFirst(JsonbParser jsonStream) {
        jsonStream.moveTo(JsonParser.Event.START_OBJECT);
        return jsonStream.getCurrentLevel();
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
    public T getInstance(Unmarshaller unmarshalContext) {
        if (null != targetInstance) {
            return targetInstance;
        }
        final Class<?> resolvedRawClass = ReflectionTypeResolver.getRawType(getRuntimeType());
        final JsonbCreator creationInfo = getClassModel().getClassCustomization().getCreator();
        targetInstance = null != creationInfo ? instantiate((Class<T>) resolvedRawClass, creationInfo) : ReflectionTypeResolver.createInstanceWithNoArgs((Class<T>) resolvedRawClass);
        //values must be set in order, in which they appears in JSON by spec
        valueMap.forEach((key, valueContainer) -> {
            //skip creator values
            if (null != valueContainer.getCreatorModel()) {
                return;
            }
            final PropertyModel lastPropertyDescriptor = valueContainer.getPropertyModel();
            lastPropertyDescriptor.setValue(targetInstance, valueContainer.getValue());
        });
        return targetInstance;
    }

    protected PropertyModel getModel() {
        final String finalKey = parserContext.getLastKeyName();
        if (null != lastPropertyInfo && lastPropertyInfo.getJsonKeyName().equals(finalKey)) {
            return lastPropertyInfo.getPropertyModel();
        }
        lastPropertyInfo = new LastJsonPropertyModel(finalKey, getClassModel().findPropertyModelByJsonReadName(finalKey));
        return lastPropertyInfo.getPropertyModel();
    }

    /**
     * Creates instance of an item.
     * @param deserializerBuilder builder to build from
     */
    protected InstanceDeserializer(JsonbDeserializerBuilder deserializerBuilder) {
        super(deserializerBuilder);
    }

    /**
     * Creates instance with custom jsonb creator (parameterized constructor or factory method)
     */
    private T instantiate(Class<T> resolvedRawClass, JsonbCreator creationInfo) {
        final T targetInstance;
        final List<Object> constructorArgs = new ArrayList<>();
        for (CreatorModel creatorInfo : creationInfo.getParams()) {
            final ValueHolder heldValue = valueMap.get(creatorInfo.getName());
            //required by spec
            if (null == heldValue) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.JSONB_CREATOR_MISSING_PROPERTY, creatorInfo.getName()));
            }
            constructorArgs.add(heldValue.getValue());
        }
        targetInstance = creationInfo.call(constructorArgs.toArray(), resolvedRawClass);
        return targetInstance;
    }

    /**
     * Rise an exception, or ignore JSON property, which is missing in class model.
     */
    private void skipUnknownProperty(JsonbParser jsonStream, JsonbRuntimeContext runtimeContext) {
        if (runtimeContext.getConfigProperties().getConfigFailOnUnknownProperties()) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.UNKNOWN_JSON_PROPERTY, parserContext.getLastKeyName(), getRuntimeType()));
        }
        jsonStream.skipJsonStructure();
    }

    /**
     * Set populated instance of current object to its unfinished wrapper values map.
     *
     * @param outcome An instance result of an item.
     */
    @Override
    public void addResult(Object outcome) {
        final PropertyModel propDescriptor = getModel();
        //missing property for null values
        if (null == propDescriptor) {
            return;
        }
        valueMap.put(propDescriptor.getReadName(), new ValueHolder(propDescriptor, convertNullToEmptyOptional(propDescriptor.getPropertyType(), outcome)));
    }

}

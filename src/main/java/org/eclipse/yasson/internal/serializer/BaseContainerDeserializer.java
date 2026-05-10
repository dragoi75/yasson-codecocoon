/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 * <p>
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.*;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;

import javax.json.bind.JsonbException;
import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Base class for all deserializers producing non single value result.
 * Deserialize bean objects, collections, maps, arrays, etc.
 *
 * @author Roman Grigoriadi
 */
public abstract class BaseContainerDeserializer<T> extends AbstractItem<T> implements JsonbDeserializer<T> {
    protected JsonbStreamingParser.LevelParseContext parserContext;

    /**
     * Create instance of current item with its builder.
     *
     * @param deserializerFactory {@link JsonValueDeserializerBuilder} used to build this instance
     */
    protected BaseContainerDeserializer(JsonValueDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }

    /**
     * Drives JSONP {@link JsonParser} to deserialize json document.
     *
     * @param jsonReader JSON parser.
     * @param deserializationEnv Deseriaization context.
     * @param runtimeType Runtime type.
     * @return Instance of a type for this item.
     */
    @Override
    public final T deserialize(JsonParser jsonReader, DeserializationContext deserializationEnv, Type runtimeType) {
        JsonbUnmarshaller unmarshalContext = (JsonbUnmarshaller) deserializationEnv;
        deserializeContainer((JsonbCursor) jsonReader, unmarshalContext);
        return getInstance((JsonbUnmarshaller) deserializationEnv);
    }

    /**
     * Creates and initializes an instance of deserializing item.
     *
     * @param jsonbHandler Current deserialization context.
     * @return An instance of deserializing item.
     */
    protected abstract T getInstance(JsonbUnmarshaller jsonbHandler);

    protected void deserializeContainer(JsonbCursor jsonReader, JsonbUnmarshaller deserializationEnv) {
        parserContext = moveToFirstToken(jsonReader);
        while (jsonReader.hasNext()) {
            final JsonParser.Event currentToken = jsonReader.next();
            switch (currentToken) {
                case START_OBJECT:
                case START_ARRAY:
                case VALUE_STRING:
                case VALUE_NUMBER:
                case VALUE_FALSE:
                case VALUE_TRUE:
                    deserializeNextValue(jsonReader, deserializationEnv);
                    break;
                case KEY_NAME:
                    break;
                case VALUE_NULL:
                    addResult(null);
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    return;
                default:
                    throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.NOT_VALUE_TYPE, currentToken));
            }
        }
    }

    /**
     * Determine class mappings and create an instance of a new deserializer.
     * Currently processed deserializer is pushed to stack, for waiting till new object is finished.
     *
     * @param jsonReader Json parser.
     * @param deserializationEnv Current unmarshalling context.
     */
    protected abstract void deserializeNextValue(JsonParser jsonReader, JsonbUnmarshaller deserializationEnv);

    /**
     * Move to first event for current deserializer structure.
     *
     * @param jsonReader Json parser.
     * @return First event.
     */
    protected abstract JsonbStreamingParser.LevelParseContext moveToFirstToken(JsonbCursor jsonReader);

    protected JsonValueDeserializerBuilder createUnmarshallerItemBuilder(JsonbRuntimeContext unmarshalContext) {
        return new JsonValueDeserializerBuilder(unmarshalContext).setWrapper(this).withJsonEvent(parserContext.getLastEvent());
    }

    protected JsonbDeserializer<?> createCollectionOrMapItemDeserializer(Type elementType, JsonbRuntimeContext unmarshalContext) {
        //TODO needs performance optimization on not to create deserializer each time
        //TODO In contrast to serialization value type cannot change here
        Type resolvedElementType = ReflectiveTypeUtils.resolveGenericType(this, elementType);
        JsonValueDeserializerBuilder deserializerFactory = createUnmarshallerItemBuilder(unmarshalContext).setType(resolvedElementType);
        if (!DefaultSerializerRegistry.getInstance().isKnownType(ReflectiveTypeUtils.getRawType(resolvedElementType))) {
            ClassDescriptor classDescriptor = unmarshalContext.getMappingContext().getOrCreateClassModel(ReflectiveTypeUtils.getRawType(resolvedElementType));
            deserializerFactory.setCustomization(classDescriptor == null ? null : classDescriptor.getCustomization());
        }
        return deserializerFactory.buildDeserializer();
    }

    /**
     * If value is null and property model type is one of {@link Optional}, {@link OptionalDouble},
     * {@link OptionalInt}, or {@link OptionalLong}, value of corresponding {@code Optional#empty()}
     * is returned.
     *
     * @param fieldType property type
     * @param input value to set
     * @return empty optional if applies
     */
    protected Object nullToOptionalEmpty(Type fieldType, Object input) {
        if (input != null) {
            return input;
        }

        if (!(fieldType instanceof Class)) {
            fieldType = ReflectiveTypeUtils.getRawType(ReflectiveTypeUtils.resolveGenericType(this, fieldType));
        }

        if (fieldType == Optional.class) {
            return Optional.empty();
        } else if (fieldType == OptionalInt.class) {
            return OptionalInt.empty();
        } else if (fieldType == OptionalLong.class) {
            return OptionalLong.empty();
        } else if (fieldType == OptionalDouble.class) {
            return OptionalDouble.empty();
        } else {
            return null;
        }
    }

    /**
     * After object is transitively deserialized from JSON, "append" it to its wrapper.
     * In case of a field set value to field, in case of collections
     * or other embedded objects use methods provided.
     *
     * @param entry An instance result of an item.
     */
    public abstract void addResult(Object entry);
}

/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  <p>
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.*;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.properties.MessageKey;
import org.eclipse.yasson.internal.properties.MessageBundle;
import javax.json.bind.JsonbException;
import javax.json.bind.serializer.DeserializationContext;
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
public abstract class AbstractCollectionDeserializer<T> extends BaseItem<T> implements javax.json.bind.serializer.JsonbDeserializer<T> {

    protected JsonbRiStreamParser.ParsingLevelContext parserContext;

    /**
     * Create instance of current item with its builder.
     *
     * @param deserializerFactory {@link JsonDeserializerBuilder} used to build this instance
     */
    protected AbstractCollectionDeserializer(JsonDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }

    /**
     * Drives JSONP {@link JsonParser} to deserialize json document.
     *
     * @param tokenStream JSON parser.
     * @param deserializationState Deseriaization context.
     * @param runtimeType Runtime type.
     * @return Instance of a type for this item.
     */
    @Override
    public final T deserialize(JsonParser tokenStream, DeserializationContext deserializationState, Type runtimeType) {
        JsonbDeserializer itemDeserializer = (JsonbDeserializer) deserializationState;
        deserializeCollection((JsonbStructureNavigator) tokenStream, itemDeserializer);
        return getInstance((JsonbDeserializer) deserializationState);
    }

    /**
     * Creates and initializes an instance of deserializing item.
     *
     * @param jsonDeserializer Current deserialization context.
     * @return An instance of deserializing item.
     */
    protected abstract T getInstance(JsonbDeserializer jsonDeserializer);

    protected void deserializeCollection(JsonbStructureNavigator tokenStream, JsonbDeserializer deserializationState) {
        parserContext = moveToFirstElement(tokenStream);
        while (tokenStream.hasNext()) {
            final JsonParser.Event jsonToken = tokenStream.next();
            switch(jsonToken) {
                case START_OBJECT:
                case START_ARRAY:
                case VALUE_STRING:
                case VALUE_NUMBER:
                case VALUE_FALSE:
                case VALUE_TRUE:
                    deserializeItem(tokenStream, deserializationState);
                    break;
                case KEY_NAME:
                    break;
                case VALUE_NULL:
                    appendValueToResult(null);
                    break;
                case END_OBJECT:
                case END_ARRAY:
                    return;
                default:
                    throw new JsonbException(MessageBundle.getMessage(MessageKey.NOT_VALUE_TYPE, jsonToken));
            }
        }
    }

    /**
     * Determine class mappings and create an instance of a new deserializer.
     * Currently processed deserializer is pushed to stack, for waiting till new object is finished.
     *
     * @param tokenStream Json parser.
     * @param deserializationState Current unmarshalling context.
     */
    protected abstract void deserializeItem(JsonParser tokenStream, JsonbDeserializer deserializationState);

    /**
     * Move to first event for current deserializer structure.
     *
     * @param tokenStream Json parser.
     * @return First event.
     */
    protected abstract JsonbRiStreamParser.ParsingLevelContext moveToFirstElement(JsonbStructureNavigator tokenStream);

    protected JsonDeserializerBuilder createUnmarshallerItemBuilder(JsonbRuntimeContext itemDeserializer) {
        return new JsonDeserializerBuilder(itemDeserializer).setWrapper(this).setJsonValueType(parserContext.getLastEvent());
    }

    protected javax.json.bind.serializer.JsonbDeserializer<?> createCollectionOrMapItemDeserializer(Type elementType, JsonbRuntimeContext itemDeserializer) {
        //TODO needs performance optimization on not to create deserializer each time
        //TODO In contrast to serialization value type cannot change here
        Type resolvedValueType = ReflectionTypeResolver.resolveActualType(this, elementType);
        JsonDeserializerBuilder deserializerFactory = createUnmarshallerItemBuilder(itemDeserializer).setType(resolvedValueType);
        if (!DefaultSerializerRegistry.getInstance().isKnownType(ReflectionTypeResolver.getRawType(resolvedValueType))) {
            ClassDescriptor classDescriptor = itemDeserializer.getMappingContext().getOrCreateClassModel(ReflectionTypeResolver.getRawType(resolvedValueType));
            deserializerFactory.setCustomization(null == classDescriptor ? null : classDescriptor.getCustomization());
        }
        return deserializerFactory.buildDeserializer();
    }

    /**
     * If value is null and property model type is one of {@link Optional}, {@link OptionalDouble},
     * {@link OptionalInt}, or {@link OptionalLong}, value of corresponding {@code Optional#empty()}
     * is returned.
     *
     * @param attributeType property type
     * @param input value to set
     * @return empty optional if applies
     */
    protected Object convertNullToEmptyOptional(Type attributeType, Object input) {
        if (null != input) {
            return input;
        }
        if (!(attributeType instanceof Class)) {
            attributeType = ReflectionTypeResolver.getRawType(ReflectionTypeResolver.resolveActualType(this, attributeType));
        }
        if (Optional.class != attributeType) {
            if (OptionalInt.class != attributeType) {
                if (OptionalLong.class != attributeType) {
                    if (OptionalDouble.class != attributeType) {
                        return null;
                    } else {
                        return OptionalDouble.empty();
                    }
                } else {
                    return OptionalLong.empty();
                }
            } else {
                return OptionalInt.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    /**
     * After object is transitively deserialized from JSON, "append" it to its wrapper.
     * In case of a field set value to field, in case of collections
     * or other embedded objects use methods provided.
     *
     * @param target An instance result of an item.
     */
    public abstract void appendValueToResult(Object target);
}

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
import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageConstants;
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

    protected JsonbRiParser.LevelContext parserContext;

    /**
     * Create instance of current item with its builder.
     *
     * @param deserializerFactory {@link JsonbDeserializerBuilder} used to build this instance
     */
    protected BaseContainerDeserializer(JsonbDeserializerBuilder deserializerFactory) {
        super(deserializerFactory);
    }

    /**
     * Drives JSONP {@link JsonParser} to deserialize json document.
     *
     * @param tokenStream JSON parser.
     * @param deserializerState Deseriaization context.
     * @param runtimeType Runtime type.
     * @return Instance of a type for this item.
     */
    @Override
    public final T deserialize(JsonParser tokenStream, DeserializationContext deserializerState, Type runtimeType) {
        Unmarshaller unmarshallerInstance = (Unmarshaller) deserializerState;
        deserializeContents((JsonbParser) tokenStream, unmarshallerInstance);
        return getInstance((Unmarshaller) deserializerState);
    }

    /**
     * Creates and initializes an instance of deserializing item.
     *
     * @param unmarshalAgent Current deserialization context.
     * @return An instance of deserializing item.
     */
    protected abstract T getInstance(Unmarshaller unmarshalAgent);

    protected void deserializeContents(JsonbParser tokenStream, Unmarshaller deserializerState) {
        parserContext = advanceToFirst(tokenStream);
        while (tokenStream.hasNext()) {
            final JsonParser.Event currentToken = tokenStream.next();
            switch(currentToken) {
                case START_OBJECT:
                case START_ARRAY:
                case VALUE_STRING:
                case VALUE_NUMBER:
                case VALUE_FALSE:
                case VALUE_TRUE:
                    deserializeNextValue(tokenStream, deserializerState);
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
                    throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.NOT_VALUE_TYPE, currentToken));
            }
        }
    }

    /**
     * Determine class mappings and create an instance of a new deserializer.
     * Currently processed deserializer is pushed to stack, for waiting till new object is finished.
     *
     * @param tokenStream Json parser.
     * @param deserializerState Current unmarshalling context.
     */
    protected abstract void deserializeNextValue(JsonParser tokenStream, Unmarshaller deserializerState);

    /**
     * Move to first event for current deserializer structure.
     *
     * @param tokenStream Json parser.
     * @return First event.
     */
    protected abstract JsonbRiParser.LevelContext advanceToFirst(JsonbParser tokenStream);

    protected JsonbDeserializerBuilder createUnmarshallerItemBuilder(JsonbRuntimeContext unmarshallerInstance) {
        return new JsonbDeserializerBuilder(unmarshallerInstance).setWrapper(this).setJsonValueType(parserContext.getLastEvent());
    }

    protected JsonbDeserializer<?> createCollectionOrMapItemDeserializer(Type elementType, JsonbRuntimeContext unmarshallerInstance) {
        //TODO needs performance optimization on not to create deserializer each time
        //TODO In contrast to serialization value type cannot change here
        Type resolvedType = ReflectionTypeResolver.resolveActualType(this, elementType);
        JsonbDeserializerBuilder deserializerFactory = createUnmarshallerItemBuilder(unmarshallerInstance).setType(resolvedType);
        if (!DefaultSerializerRegistry.getInstance().isKnownType(ReflectionTypeResolver.getRawType(resolvedType))) {
            ClassModel typeModel = unmarshallerInstance.getMappingContext().getOrCreateClassModel(ReflectionTypeResolver.getRawType(resolvedType));
            deserializerFactory.setCustomization(null == typeModel ? null : typeModel.getCustomization());
        }
        return deserializerFactory.buildDeserializer();
    }

    /**
     * If value is null and property model type is one of {@link Optional}, {@link OptionalDouble},
     * {@link OptionalInt}, or {@link OptionalLong}, value of corresponding {@code Optional#empty()}
     * is returned.
     *
     * @param fieldType property type
     * @param payload value to set
     * @return empty optional if applies
     */
    protected Object convertNullToEmptyOptional(Type fieldType, Object payload) {
        if (null != payload) {
            return payload;
        }
        if (!(fieldType instanceof Class)) {
            fieldType = ReflectionTypeResolver.getRawType(ReflectionTypeResolver.resolveActualType(this, fieldType));
        }
        if (Optional.class != fieldType) {
            if (OptionalInt.class != fieldType) {
                if (OptionalLong.class != fieldType) {
                    if (OptionalDouble.class != fieldType) {
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
     * @param outcome An instance result of an item.
     */
    public abstract void addResult(Object outcome);
}

package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.JsonbParser;
import org.eclipse.yasson.internal.ProcessingContext;

import javax.json.bind.serializer.DeserializationContext;
import javax.json.bind.serializer.JsonbDeserializer;
import javax.json.stream.JsonParser;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Deserialize optional object.
 *
 * @author Roman Grigoriadi
 */
public class OptionalValueDeserializer implements JsonbDeserializer<Optional<?>> {

    private final CurrentItem<?> itemHolder;

    private final Type optionalType;

    public OptionalValueDeserializer(JsonbDeserializerBuilder jsonbBuilder) {
        this.itemHolder = jsonbBuilder.getWrapper();
        this.optionalType = determineOptionalType(jsonbBuilder.getRuntimeType());
    }

    @Override
    public Optional<?> deserialize(JsonParser jsonReader, DeserializationContext deserializationContext, Type rtType) {
        JsonbRuntimeContext runtimeContext = ((ProcessingContext) deserializationContext).getJsonbContext();
        final JsonParser.Event finalEvent = ((JsonbParser) jsonReader).getCurrentLevel().getLastEvent();
        JsonbDeserializer valueConverter = new JsonbDeserializerBuilder(runtimeContext).setType(optionalType)
                .setWrapper(itemHolder).setJsonValueType(finalEvent).buildDeserializer();
        return Optional.of(valueConverter.deserialize(jsonReader, deserializationContext, optionalType));
    }


    private Type determineOptionalType(Type resolvedType) {
        if (resolvedType instanceof ParameterizedType) {
            return ((ParameterizedType) resolvedType).getActualTypeArguments()[0];
        }
        return Object.class;
    }
}

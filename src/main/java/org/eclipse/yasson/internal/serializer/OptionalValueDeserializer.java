package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonbContext;
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

    private final CurrentItem<?> currentItem;

    private final Type valueType;

    public OptionalValueDeserializer(JsonbDeserializerBuilder builder) {
        this.currentItem = builder.getWrapper();
        this.valueType = resolveOptionalValueType(builder.getRuntimeType());
    }

    @Override
    public Optional<?> deserialize(JsonParser jsonReader, DeserializationContext context, Type runtimeType) {
        JsonbContext jsonbEnv = ((ProcessingContext) context).getJsonbContext();
        final JsonParser.Event finalEvent = ((JsonbParser) jsonReader).getCurrentLevel().getLastEvent();
        JsonbDeserializer valueReader = new JsonbDeserializerBuilder(jsonbEnv).withType(valueType)
                .withWrapper(currentItem).withJsonEvent(finalEvent).build();
        return Optional.of(valueReader.deserialize(jsonReader, context, valueType));
    }


    private Type resolveOptionalValueType(Type actualType) {
        if (actualType instanceof ParameterizedType) {
            return ((ParameterizedType) actualType).getActualTypeArguments()[0];
        }
        return Object.class;
    }
}

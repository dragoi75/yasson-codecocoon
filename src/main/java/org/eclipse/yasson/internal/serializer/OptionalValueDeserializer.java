package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.JsonbCursor;
import org.eclipse.yasson.internal.ProcessingEnvironment;

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

    private final CurrentItemWrapper<?> currentItem;

    private final Type elementType;

    public OptionalValueDeserializer(JsonValueDeserializerBuilder builder) {
        this.currentItem = builder.getWrapper();
        this.elementType = resolveOptionalElementType(builder.getRuntimeType());
    }

    @Override
    public Optional<?> deserialize(JsonParser jsonReader, DeserializationContext context, Type rtType) {
        JsonbRuntimeContext runtimeContext = ((ProcessingEnvironment) context).getJsonbContext();
        final JsonParser.Event finalEvent = ((JsonbCursor) jsonReader).getCurrentLevel().getLastEvent();
        JsonbDeserializer valueReader = new JsonValueDeserializerBuilder(runtimeContext).setType(elementType)
                .setWrapper(currentItem).withJsonEvent(finalEvent).buildDeserializer();
        return Optional.of(valueReader.deserialize(jsonReader, context, elementType));
    }


    private Type resolveOptionalElementType(Type elementType) {
        if (elementType instanceof ParameterizedType) {
            return ((ParameterizedType) elementType).getActualTypeArguments()[0];
        }
        return Object.class;
    }
}

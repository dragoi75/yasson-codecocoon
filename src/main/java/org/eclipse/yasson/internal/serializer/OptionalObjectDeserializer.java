package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.JsonbConfigurationContext;
import org.eclipse.yasson.internal.JsonbStreamParser;
import org.eclipse.yasson.internal.ProcessingContextManager;

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
public class OptionalObjectDeserializer implements JsonbDeserializer<Optional<?>> {

    private final CurrentItemProvider<?> wrapper;

    private final Type optionalValueType;


    @Override
    public Optional<?> deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        JsonbConfigurationContext jsonbContext = ((ProcessingContextManager) ctx).getJsonbContext();
        final JsonParser.Event lastEvent = ((JsonbStreamParser) parser).getCurrentLevel().getLastEvent();
        JsonbDeserializer deserializer = new DeserializationBuilder(jsonbContext).setType(optionalValueType)
                .setWrapper(wrapper).withJsonEvent(lastEvent).buildDeserializer();
        return Optional.of(deserializer.deserialize(parser, ctx, optionalValueType));
    }

    private Type resolveOptionalType(Type runtimeType) {
        if (runtimeType instanceof ParameterizedType) {
            return ((ParameterizedType) runtimeType).getActualTypeArguments()[0];
        }
        return Object.class;
    }

    public OptionalObjectDeserializer(DeserializationBuilder deserializerBuilder) {
        this.wrapper = deserializerBuilder.getWrapper();
        this.optionalValueType = resolveOptionalType(deserializerBuilder.getRuntimeType());
    }

}

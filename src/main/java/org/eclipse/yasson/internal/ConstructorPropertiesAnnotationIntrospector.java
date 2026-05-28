package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.components.JsonbComponentInstanceCreator;
import org.eclipse.yasson.internal.model.CreatorModel;
import org.eclipse.yasson.internal.model.JsonbCreator;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.logging.Logger;

class ConstructorPropertiesAnnotationIntrospector {

    private static final Logger LOG = Logger.getLogger(JsonbComponentInstanceCreator.class.getName());

    private final JsonbConfigurationContext jsonbContext;

    private final AnnotationFinder constructorProperties;

    private JsonbCreator createJsonbCreator(Executable executable, String[] properties) {
        final Parameter[] parameters = executable.getParameters();
        CreatorModel[] creatorModels = new CreatorModel[parameters.length];
        int i = 0;
        while (parameters.length > i) {
            final Parameter parameter = parameters[i];
            creatorModels[i] = new CreatorModel(properties[i], parameter, jsonbContext);
            i += 1;
        }
        return new JsonbCreator(executable, creatorModels);
    }

    @Override
    public String toString() {
        return "ConstructorPropertiesAnnotationIntrospector [jsonbContext=" + jsonbContext + ", constructorProperties=" + constructorProperties + "]";
    }

    public JsonbCreator getCreator(Constructor<?>[] constructors) {
        JsonbCreator jsonbCreator = null;
        for (Constructor<?> constructor : constructors) {
            Object properties = constructorProperties.valueIn(constructor.getDeclaredAnnotations());
            if (!(properties instanceof String[])) {
                continue;
            }
            if (null != jsonbCreator) {
                // don't fail in this case, because it is perfectly allowed to have more than one
                // @ConstructorProperties-Annotation in general.
                // It is just undefined, which constructor to choose for JSON in this case.
                // The behavior should be the same (null), as if there is no ConstructorProperties-Annotation at all.
                LOG.warning(LocalizedMessages.getMessage(MessageKeyConstants.MULTIPLE_CONSTRUCTOR_PROPERTIES_CREATORS, constructor.getDeclaringClass().getName()));
                return null;
            }
            jsonbCreator = createJsonbCreator(constructor, (String[]) properties);
        }
        return jsonbCreator;
    }

    public static final ConstructorPropertiesAnnotationIntrospector forContext(JsonbConfigurationContext jsonbContext) {
        return new ConstructorPropertiesAnnotationIntrospector(jsonbContext, AnnotationFinder.findConstructorProperties());
    }

    /**
     * Only for testing and internal purposes.
     * <p>
     * Please use static factory methods e.g. {@link #forContext(JsonbConfigurationContext)}.
     *
     * @param context          {@link JsonbConfigurationContext}
     * @param annotationFinder {@link AnnotationFinder}
     */
    protected ConstructorPropertiesAnnotationIntrospector(JsonbConfigurationContext context, AnnotationFinder annotationFinder) {
        this.jsonbContext = context;
        this.constructorProperties = annotationFinder;
    }

}

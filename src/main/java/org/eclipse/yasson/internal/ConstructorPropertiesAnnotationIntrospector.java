/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.logging.Logger;

import org.eclipse.yasson.internal.model.CreatorProfile;
import org.eclipse.yasson.internal.model.JsonbInstantiator;
import org.eclipse.yasson.internal.properties.ErrorMessageKeys;
import org.eclipse.yasson.internal.properties.MessageBundle;

class ConstructorPropertiesAnnotationIntrospector {

    private static final Logger LOG = Logger.getLogger(ConstructorPropertiesAnnotationIntrospector.class.getName());

    private final JsonbRuntimeContext jsonbContext;
    private final AnnotationFinder constructorProperties;

    public static final ConstructorPropertiesAnnotationIntrospector forContext(JsonbRuntimeContext jsonbContext) {
        return new ConstructorPropertiesAnnotationIntrospector(jsonbContext, AnnotationFinder.findConstructorProperties());
    }

    /**
     * Only for testing and internal purposes.
     * <p>
     * Please use static factory methods e.g. {@link #forContext(JsonbRuntimeContext)}.
     *
     * @param context          {@link JsonbRuntimeContext}
     * @param annotationFinder {@link AnnotationFinder}
     */
    protected ConstructorPropertiesAnnotationIntrospector(JsonbRuntimeContext context, AnnotationFinder annotationFinder) {
        this.jsonbContext = context;
        this.constructorProperties = annotationFinder;
    }

    public JsonbInstantiator getCreator(Constructor<?>[] constructors) {
        JsonbInstantiator jsonbCreator = null;

        for (Constructor<?> constructor : constructors) {
            Object properties = constructorProperties.valueIn(constructor.getDeclaredAnnotations());
            if (!(properties instanceof String[])) {
                continue;
            }
            if (!Modifier.isPublic(constructor.getModifiers())) {
                String declaringClass = constructor.getDeclaringClass().getName();
                String message = "The constructor of {0} annotated with @ConstructorProperties {1} is not accessible and will "
                        + "be ignored.";
                LOG.finest(String.format(message, declaringClass, Arrays.toString((String[]) properties)));
                continue;
            }
            if (jsonbCreator != null) {
                // don't fail in this case, because it is perfectly allowed to have more than one
                // @ConstructorProperties-Annotation in general.
                // It is just undefined, which constructor to choose for JSON in this case.
                // The behavior should be the same (null), as if there is no ConstructorProperties-Annotation at all.
                LOG.warning(MessageBundle.getMessage(ErrorMessageKeys.MULTIPLE_CONSTRUCTOR_PROPERTIES_CREATORS,
                                                constructor.getDeclaringClass().getName()));
                return null;
            }
            jsonbCreator = createJsonbCreator(constructor, (String[]) properties);
        }
        return jsonbCreator;
    }

    private JsonbInstantiator createJsonbCreator(Executable executable, String[] properties) {
        final Parameter[] parameters = executable.getParameters();

        CreatorProfile[] creatorModels = new CreatorProfile[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];
            creatorModels[i] = new CreatorProfile(properties[i], parameter, jsonbContext);
        }
        return new JsonbInstantiator(executable, creatorModels);
    }

    @Override
    public String toString() {
        return "ConstructorPropertiesAnnotationIntrospector [jsonbContext=" + jsonbContext + ", constructorProperties=" + constructorProperties + "]";
    }
}

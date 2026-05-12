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
import org.eclipse.yasson.internal.model.JsonbCreatorInvoker;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

class ConstructorPropertiesAnnotationInspector {

    private static final Logger LOGGER = Logger.getLogger(ConstructorPropertiesAnnotationInspector.class.getName());

    private final JsonbRuntimeContext runtimeContext;

    private final AnnotationLocator constructorAnnotations;

    public static final ConstructorPropertiesAnnotationInspector forJsonbContext(JsonbRuntimeContext runtimeContext) {
        return new ConstructorPropertiesAnnotationInspector(runtimeContext, AnnotationLocator.findConstructorPropertiesAnnotation());
    }

    /**
     * Only for testing and internal purposes.
     * <p>
     * Please use static factory methods e.g. {@link #forJsonbContext(JsonbRuntimeContext)}.
     *
     * @param runtime          {@link JsonbRuntimeContext}
     * @param annotationLocator {@link AnnotationLocator}
     */
    protected ConstructorPropertiesAnnotationInspector(JsonbRuntimeContext runtime, AnnotationLocator annotationLocator) {
        this.runtimeContext = runtime;
        this.constructorAnnotations = annotationLocator;
    }

    public JsonbCreatorInvoker getCreator(Constructor<?>[] ctorArray) {
        JsonbCreatorInvoker creatorInvoker = null;
        for (Constructor<?> ctor : ctorArray) {
            Object props = constructorAnnotations.getValueIn(ctor.getDeclaredAnnotations());
            if (!(props instanceof String[])) {
                continue;
            }
            if (!Modifier.isPublic(ctor.getModifiers())) {
                String ownerClassName = ctor.getDeclaringClass().getName();
                String errorMsg = "The constructor of {0} annotated with @ConstructorProperties {1} is not accessible and will " + "be ignored.";
                LOGGER.finest(String.format(errorMsg, ownerClassName, Arrays.toString((String[]) props)));
                continue;
            }
            if (null != creatorInvoker) {
                // don't fail in this case, because it is perfectly allowed to have more than one
                // @ConstructorProperties-Annotation in general.
                // It is just undefined, which constructor to choose for JSON in this case.
                // The behavior should be the same (null), as if there is no ConstructorProperties-Annotation at all.
                LOGGER.warning(MessageBundle.getMessage(MessageKeyConstants.MULTIPLE_CONSTRUCTOR_PROPERTIES_CREATORS, ctor.getDeclaringClass().getName()));
                return null;
            }
            creatorInvoker = createJsonbCreatorInvoker(ctor, (String[]) props);
        }
        return creatorInvoker;
    }

    private JsonbCreatorInvoker createJsonbCreatorInvoker(Executable execMember, String[] props) {
        final Parameter[] params = execMember.getParameters();
        CreatorProfile[] creatorProfiles = new CreatorProfile[params.length];
        int idx = 0;
        while (params.length > idx) {
            final Parameter param = params[idx];
            creatorProfiles[idx] = new CreatorProfile(props[idx], param, runtimeContext);
            idx += 1;
        }
        return new JsonbCreatorInvoker(execMember, creatorProfiles);
    }

    @Override
    public String toString() {
        return "ConstructorPropertiesAnnotationIntrospector [jsonbContext=" + runtimeContext + ", constructorProperties=" + constructorAnnotations + "]";
    }
}

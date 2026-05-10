/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/
package org.eclipse.yasson.internal.model;

import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.serializer.CurrentItemWrapper;

import java.lang.reflect.Type;

/**
 * Wrapper for metadata of serialized property.
 *
 * @author Roman Grigoriadi
 */
public class JsonbPropertyDescriptor {

    private JsonbRuntimeContext jsonbRuntime;

    private Type resolvedType;

    private ClassDescriptor classDescriptor;

    private CurrentItemWrapper<?> currentItemHolder;

    /**
     * Gets context.
     *
     * @return Context.
     */
    public JsonbRuntimeContext getContext() {
        return jsonbRuntime;
    }

    /**
     * Sets context.
     *
     * @param jsonbRuntime Context to set.
     * @return Updated object.
     */
    public JsonbPropertyDescriptor setContext(JsonbRuntimeContext jsonbRuntime) {
        this.jsonbRuntime = jsonbRuntime;
        return this;
    }

    /**
     * Gets runtime type.
     *
     * @return Runtime type.
     */
    public Type getRuntimeType() {
        return resolvedType;
    }

    /**
     * Sets runtime type.
     *
     * @param resolvedType Runtime type to set.
     * @return Updated object.
     */
    public JsonbPropertyDescriptor setRuntimeType(Type resolvedType) {
        this.resolvedType = resolvedType;
        return this;
    }

    /**
     * Gets class model.
     *
     * @return Class model.
     */
    public ClassDescriptor getClassModel() {
        return classDescriptor;
    }

    /**
     * Sets class model.
     *
     * @param classDescriptor Class model to set.
     * @return Updated object.
     */
    public JsonbPropertyDescriptor withClassModel(ClassDescriptor classDescriptor) {
        this.classDescriptor = classDescriptor;
        return this;
    }

    /**
     * Gets wrapper.
     *
     * @return Wrapper.
     */
    public CurrentItemWrapper<?> getWrapper() {
        return currentItemHolder;
    }

    /**
     * Sets wrapper.
     *
     * @param currentItemHolder Wrapper to set.
     * @return Updated object.
     */
    public JsonbPropertyDescriptor setWrapper(CurrentItemWrapper<?> currentItemHolder) {
        this.currentItemHolder = currentItemHolder;
        return this;
    }
}

/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.model;

import java.lang.reflect.Type;

import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.serializer.CurrentItem;

/**
 * Wrapper for metadata of serialized property.
 */
public class JsonbPropertyMetadata {

    private JsonbRuntimeContext jsonbRuntime;

    private Type actualType;

    private ClassDescriptor typeDescriptor;

    private CurrentItem<?> currentItem;

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
    public JsonbPropertyMetadata setContext(JsonbRuntimeContext jsonbRuntime) {
        this.jsonbRuntime = jsonbRuntime;
        return this;
    }

    /**
     * Gets runtime type.
     *
     * @return Runtime type.
     */
    public Type getRuntimeType() {
        return actualType;
    }

    /**
     * Sets runtime type.
     *
     * @param actualType Runtime type to set.
     * @return Updated object.
     */
    public JsonbPropertyMetadata setRuntimeType(Type actualType) {
        this.actualType = actualType;
        return this;
    }

    /**
     * Gets class model.
     *
     * @return Class model.
     */
    public ClassDescriptor getClassModel() {
        return typeDescriptor;
    }

    /**
     * Sets class model.
     *
     * @param typeDescriptor Class model to set.
     * @return Updated object.
     */
    public JsonbPropertyMetadata withClassModel(ClassDescriptor typeDescriptor) {
        this.typeDescriptor = typeDescriptor;
        return this;
    }

    /**
     * Gets wrapper.
     *
     * @return Wrapper.
     */
    public CurrentItem<?> getWrapper() {
        return currentItem;
    }

    /**
     * Sets wrapper.
     *
     * @param currentItem Wrapper to set.
     * @return Updated object.
     */
    public JsonbPropertyMetadata setWrapper(CurrentItem<?> currentItem) {
        this.currentItem = currentItem;
        return this;
    }
}

/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashSet;
import java.util.Set;

/**
 * Jsonb processing (serializing/deserializing) context.
 * Instance is thread bound (in contrast to {@link JsonbRuntimeContext}.
 */
public abstract class ProcessingSessionContext {

    private final JsonbRuntimeContext runtimeContext;

    /**
     * Used to avoid StackOverflowError, when adapted / serialized object
     * contains contains instance of its type inside it or when object has recursive reference.
     */
    private final Set<Object> processedObjects = new HashSet<>();

    /**
     * Adds currently processed object to the {@link Set}.
     *
     * @param itemToRegister processed object
     * @return if object was added
     */
    public boolean registerProcessedObject(Object itemToRegister) {
        return this.processedObjects.add(itemToRegister);
    }

    /**
     * Removes processed object from the {@link Set}.
     *
     * @param itemToRegister processed object
     * @return if object was removed
     */
    public boolean unregisterProcessedObject(Object itemToRegister) {
        return processedObjects.remove(itemToRegister);
    }

    /**
     * Jsonb context.
     *
     * @return jsonb context
     */
    public JsonbRuntimeContext getJsonbContext() {
        return runtimeContext;
    }

    /**
     * Mapping context.
     *
     * @return mapping context
     */
    public MappingContext getMappingContext() {
        return getJsonbContext().getMappingContext();
    }

    /**
     * Parent instance for marshaller and unmarshaller.
     *
     * @param runtimeContext context of Jsonb
     */
    public ProcessingSessionContext(JsonbRuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

}

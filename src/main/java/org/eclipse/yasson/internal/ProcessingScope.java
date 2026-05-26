/*
 * Copyright (c) 2015, 2022 Oracle and/or its affiliates. All rights reserved.
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
 * Instance is thread bound (in contrast to {@link JsonBindingContext}.
 */
public abstract class ProcessingScope {

    private final JsonBindingContext jsonBindingContext;

    /**
     * Used to avoid StackOverflowError, when adapted / serialized object
     * contains instance of its type inside it or when object has recursive reference.
     */
    private final Set<Object> processedObjects = new HashSet<>();

    /**
     * Mapping context.
     *
     * @return mapping context
     */
    public ClassModelContext getMappingContext() {
        return getJsonbContext().getMappingContext();
    }

    /**
     * Adds currently processed object to the {@link Set}.
     *
     * @param item processed object
     * @return if object was added
     */
    public boolean addProcessedObject(Object item) {
        return this.processedObjects.add(item);
    }

    /**
     * Removes processed object from the {@link Set}.
     *
     * @param item processed object
     * @return if object was removed
     */
    public boolean removeProcessedObject(Object item) {
        return processedObjects.remove(item);
    }

    /**
     * Jsonb context.
     *
     * @return jsonb context
     */
    public JsonBindingContext getJsonbContext() {
        return jsonBindingContext;
    }

    /**
     * Parent for marshaller and unmarshaller.
     *
     * @param jsonBindingContext context of Jsonb
     */
    public ProcessingScope(JsonBindingContext jsonBindingContext) {
        this.jsonBindingContext = jsonBindingContext;
    }

}

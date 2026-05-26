/*******************************************************************************
 * Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 * Dmitry Kornilov - initial implementation
 ******************************************************************************/
package org.eclipse.yasson.internal;

import java.util.HashSet;
import java.util.Set;

/**
 * Jsonb processing (serializing/deserializing) context.
 * Instance is thread bound (in contrast to {@link JsonbRuntimeContext}.
 *
 * @author Roman Grigoriadi
 */
public abstract class ObjectProcessingContext {

    protected final JsonbRuntimeContext jsonbContext;

    /**
     * Used to avoid StackOverflowError, when adapted / serialized object
     * contains contains instance of its type inside it or when object has recursive reference.
     */
    private final Set<Object> processedItems = new HashSet<>();


    public boolean unregisterProcessedObject(Object processedEntity) {
        return processedItems.remove(processedEntity);
    }

    public boolean registerProcessedObject(Object processedEntity) {
        return this.processedItems.add(processedEntity);
    }

    /**
     * Parent instance for marshaller and unmarshaller.
     *
     * @param runtimeContext context of Jsonb
     */
    public ObjectProcessingContext(JsonbRuntimeContext runtimeContext) {
        this.jsonbContext = runtimeContext;
    }

    /**
     * Jsonb context.
     *
     * @return jsonb context
     */
    public JsonbRuntimeContext getJsonbContext() {
        return jsonbContext;
    }

    /**
     * Mapping context.
     *
     * @return mapping context
     */
    public MappingContext getMappingContext() {
        return getJsonbContext().getMappingContext();
    }

}

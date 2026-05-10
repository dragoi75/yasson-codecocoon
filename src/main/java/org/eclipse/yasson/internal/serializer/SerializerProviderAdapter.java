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

package org.eclipse.yasson.internal.serializer;

/**
 * Wraps serializer and deserializer providers.
 */
public class SerializerProviderAdapter {

    private ISerializerProvider serializerFactory;
    private IDeserializerProvider deserializerFactory;

    /**
     * Creates a new instance.
     *
     * @param serializerFactory   Serializer provider.
     * @param deserializerFactory Deserializer provider.
     */
    public SerializerProviderAdapter(ISerializerProvider serializerFactory, IDeserializerProvider deserializerFactory) {
        this.serializerFactory = serializerFactory;
        this.deserializerFactory = deserializerFactory;
    }

    /**
     * Gets serializer provider.
     *
     * @return Serializer provider.
     */
    public ISerializerProvider getSerializerProvider() {
        return serializerFactory;
    }

    /**
     * Gets deserializer provider.
     *
     * @return Deserializer provider.
     */
    public IDeserializerProvider getDeserializerProvider() {
        return deserializerFactory;
    }
}

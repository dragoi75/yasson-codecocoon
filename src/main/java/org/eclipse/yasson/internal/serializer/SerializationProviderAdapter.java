/*******************************************************************************
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved.
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
package org.eclipse.yasson.internal.serializer;

/**
 * Wraps serializer and deserializer providers.
 *
 * @author Roman Grigoriadi
 */
public class SerializationProviderAdapter {

    private ISerializerProvider serializationService;
    private IDeserializerProvider deserializationService;

    /**
     * Creates a new instance.
     *
     * @param serializationService Serializer provider.
     * @param deserializationService Deserializer provider.
     */
    public SerializationProviderAdapter(ISerializerProvider serializationService, IDeserializerProvider deserializationService) {
        this.serializationService = serializationService;
        this.deserializationService = deserializationService;
    }

    /**
     * Gets serializer provider.
     *
     * @return Serializer provider.
     */
    public ISerializerProvider getSerializerProvider() {
        return serializationService;
    }

    /**
     * Gets deserializer provider.
     *
     * @return Deserializer provider.
     */
    public IDeserializerProvider getDeserializerProvider() {
        return deserializationService;
    }
}

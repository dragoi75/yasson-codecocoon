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

package org.eclipse.yasson.internal.serializer;

import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Deserializer for {@link URL} type.
 */
public class UrlTypeDeserializerImpl extends AbstractValueTypeDeserializer<URL> {

    @Override
    protected URL deserialize(String jsonString, Unmarshaller unmarshaller, Type rtType) {
        URL parsedUri = null;
        try {
            parsedUri = new URL(jsonString);
        } catch (MalformedURLException exception) {
            exception.printStackTrace();
        }
        return parsedUri;
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public UrlTypeDeserializerImpl(Customization customConfig) {
        super(URL.class, customConfig);
    }

}

/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
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

import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.SerializationContextImpl;

/**
 * Switching mechanism for default null value visibility in the JSON.
 *
 * Some constructs such as arrays, collections etc. require to have nulls serialized into the JSON by default.
 * This class switches from the default parent null visibility to the current construct visibility. As soon as the current
 * construct is serialized, visibility is switched back to the parent ones.
 */
class NullVisibilityToggle implements ModelMarshaller {

    private final boolean showNullValues;
    private final ModelMarshaller innerMarshaller;

    @Override
    public void marshal(Object objectToMarshal, JsonGenerator jsonWriter, SerializationContextImpl serializationCtx) {
        boolean wasEnabled = serializationCtx.isContainerWithNulls();
        serializationCtx.setContainerWithNulls(showNullValues);
        innerMarshaller.marshal(objectToMarshal, jsonWriter, serializationCtx);
        serializationCtx.setContainerWithNulls(wasEnabled);
    }

    NullVisibilityToggle(boolean showNullValues, ModelMarshaller innerMarshaller) {
        this.showNullValues = showNullValues;
        this.innerMarshaller = innerMarshaller;
    }

}

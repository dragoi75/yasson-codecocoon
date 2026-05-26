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
import java.time.ZoneId;

import org.eclipse.yasson.internal.JsonbUnmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Deserializer for {@link ZoneId} type.
 */
public class ZoneIdTypeDeserializer extends AbstractValueTypeDeserializer<ZoneId> {

    @Override
    protected ZoneId deserialize(String jsonValue, JsonbUnmarshaller unmarshaller, Type rtType) {
        return ZoneId.of(jsonValue);
    }

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    public ZoneIdTypeDeserializer(Customization customization) {
        super(ZoneId.class, customization);
    }

}

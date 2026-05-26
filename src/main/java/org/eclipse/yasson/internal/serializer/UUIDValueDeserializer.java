/*******************************************************************************
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 ******************************************************************************/

package org.eclipse.yasson.internal.serializer;


import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;

import java.lang.reflect.Type;
import java.util.UUID;

/**
 * Deserializer for {@link UUID} type.
 */
public class UUIDValueDeserializer extends AbstractValueTypeDeserializer<UUID> {

    @Override
    protected UUID deserialize(String jsonText, Unmarshaller unmarshaller, Type rtType) {
        return UUID.fromString(jsonText);
    }

    /**
     * Creates a new instance.
     *
     * @param configProfile Model customization.
     */
    public UUIDValueDeserializer(Customization configProfile) {
        super(UUID.class, configProfile);
    }

}

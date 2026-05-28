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


import org.eclipse.yasson.internal.JsonbMarshaller;
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;

import javax.json.stream.JsonGenerator;
import java.util.UUID;

/**
 * Serializer for {@link UUID} type.
 */
public class UUIDTypeSerializer extends ConfigurableValueTypeSerializer<UUID> {

    @Override
    protected void serializeValue(UUID obj, JsonGenerator generator, JsonbMarshaller marshaller) {
        generator.write(obj.toString());
    }

    /**
     * Creates a new instance.
     *
     * @param customization Model customization.
     */
    public UUIDTypeSerializer(SerializationCustomization customization) {
        super(customization);
    }

}

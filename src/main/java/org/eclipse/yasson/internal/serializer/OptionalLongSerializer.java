/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.JsonbMarshaller;
import org.eclipse.yasson.internal.model.customization.SerializationCustomization;

import javax.json.stream.JsonGenerator;
import java.util.OptionalLong;

/**
 * Serializer for {@link OptionalLong} type.
 * 
 * @author David Kral
 */
public class OptionalLongSerializer extends ConfigurableValueTypeSerializer<OptionalLong> {

    /**
     * Creates a new instance.
     *
     * @param serializationSettings Model customization.
     */
    public OptionalLongSerializer(SerializationCustomization serializationSettings) {
        super(serializationSettings);
    }

    @Override
    protected void serializeValue(OptionalLong optionalLong, JsonGenerator jsonWriter, JsonbMarshaller marshaller) {
        if (optionalLong.isPresent()) {
            jsonWriter.write(optionalLong.getAsLong());
        } else if (customization.isNillable()) {
            jsonWriter.writeNull();
        }
    }
}

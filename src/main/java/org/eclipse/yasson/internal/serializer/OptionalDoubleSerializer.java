/**
 * ****************************************************************************
 *  Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal.serializer;

import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.model.customization.Customization;
import javax.json.stream.JsonGenerator;
import java.util.OptionalDouble;

/**
 * Serializer for {@link OptionalDouble} type.
 *
 * @author David Kral
 */
public class OptionalDoubleSerializer extends AbstractValueTypeSerializer<OptionalDouble> {

    @Override
    protected void serialize(OptionalDouble optionalDoubleValue, JsonGenerator jsonWriter, Marshaller marshaller) {
        if (!optionalDoubleValue.isPresent()) {
            if (customization.isNillable()) {
                jsonWriter.writeNull();
            }
        } else {
            jsonWriter.write(optionalDoubleValue.getAsDouble());
        }
    }

    /**
     * Creates a new instance.
     *
     * @param serializationOptions Model customization.
     */
    public OptionalDoubleSerializer(Customization serializationOptions) {
        super(serializationOptions);
    }

}

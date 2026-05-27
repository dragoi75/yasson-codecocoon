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
package org.eclipse.yasson.internal.serializer.types;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.JsonbNumberFormatter;
import org.eclipse.yasson.internal.DefaultSerializationContext;
import org.eclipse.yasson.internal.model.customization.Customization;
import org.eclipse.yasson.internal.serializer.ModelMarshaller;

/**
 * Base for all number related serializers.
 */
abstract class AbstractNumberSerializer<T> extends TypeSerializer<T> {

    private final ModelMarshaller actualSerializer;

    abstract void writeValue(T value, JsonGenerator generator);

    @SuppressWarnings("unchecked")
    private ModelMarshaller actualSerializer(Customization customization, JsonbContext jsonbContext) {
        JsonbNumberFormatter formatter = customization.getSerializeNumberFormatter();
        if (null == formatter) {
            return (value, generator, context) -> writeValue((T) value, generator);
        }
        final NumberFormat format = NumberFormat.getInstance(jsonbContext.getConfigProperties().getLocale(formatter.getLocale()));
        ((DecimalFormat) format).applyPattern(formatter.getFormat());
        return (value, generator, context) -> generator.write(format.format(value));
    }

    @Override
    void serializeValue(T value, JsonGenerator generator, DefaultSerializationContext context) {
        actualSerializer.marshal(value, generator, context);
    }

    AbstractNumberSerializer(TypeSerializerBuilder builder) {
        super(builder);
        actualSerializer = actualSerializer(builder.getCustomization(), builder.getJsonbContext());
    }

}

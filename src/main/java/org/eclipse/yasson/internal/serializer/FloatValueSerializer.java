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

import java.math.BigDecimal;

import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Serializer for {@link Float} type.
 */
public class FloatValueSerializer extends AbstractNumberSerializer<Float> {

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public FloatValueSerializer(Customization customConfig) {
        super(customConfig);
    }

    @Override
    protected void serializeNonFormatted(Float floatValue, JsonGenerator jsonWriter, String fieldName) {
        //floats lose precision, after upcasting to doubles in jsonp
        jsonWriter.write(fieldName, new BigDecimal(String.valueOf(floatValue)));
    }

    @Override
    protected void serializeNonFormatted(Float floatValue, JsonGenerator jsonWriter) {
        //floats lose precision, after upcasting to doubles in jsonp
        jsonWriter.write(new BigDecimal(String.valueOf(floatValue)));
    }
}

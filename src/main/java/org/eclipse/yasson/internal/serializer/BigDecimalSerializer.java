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
 * Serializer for {@link BigDecimal} type.
 */
public class BigDecimalSerializer extends AbstractNumberSerializer<BigDecimal> {

    /**
     * Creates a new instance.
     *
     * @param formatOptions Model customization.
     */
    public BigDecimalSerializer(Customization formatOptions) {
        super(formatOptions);
    }

    @Override
    protected void serializeNonFormatted(BigDecimal value, JsonGenerator jsonWriter, String propertyName) {
        jsonWriter.write(propertyName, value);
    }

    @Override
    protected void serializeNonFormatted(BigDecimal value, JsonGenerator jsonWriter) {
        jsonWriter.write(value);
    }
}

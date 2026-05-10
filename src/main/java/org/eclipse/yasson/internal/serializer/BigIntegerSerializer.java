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

import java.math.BigInteger;

import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Serializer for {@link BigInteger} type.
 */
public class BigIntegerSerializer extends AbstractNumberSerializer<BigInteger> {

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public BigIntegerSerializer(Customization customConfig) {
        super(customConfig);
    }

    @Override
    protected void serializeNonFormatted(BigInteger value, JsonGenerator jsonWriter, String propertyName) {
        jsonWriter.write(propertyName, value);
    }

    @Override
    protected void serializeNonFormatted(BigInteger value, JsonGenerator jsonWriter) {
        jsonWriter.write(value);
    }
}

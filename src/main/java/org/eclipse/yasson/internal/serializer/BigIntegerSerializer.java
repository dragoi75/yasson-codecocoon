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

import org.eclipse.yasson.internal.model.customization.Customization;

import javax.json.stream.JsonGenerator;
import java.math.BigInteger;

/**
 * Serializer for {@link BigInteger} type.
 *
 * @author David Kral
 */
public class BigIntegerSerializer extends AbstractNumberSerializer<BigInteger> {

    /**
     * Creates a new instance.
     *
     * @param config Model customization.
     */
    public BigIntegerSerializer(Customization config) {
        super(config);
    }

    @Override
    protected void serializeNonFormatted(BigInteger value, JsonGenerator jsonWriter, String fieldName) {
        if (BigNumberUtil.isIEEE754(value)) {
            jsonWriter.write(fieldName, value);
        } else {
            jsonWriter.write(fieldName, value.toString());
        }
    }

    @Override
    protected void serializeNonFormatted(BigInteger value, JsonGenerator jsonWriter) {
        if (BigNumberUtil.isIEEE754(value)) {
            jsonWriter.write(value);
        } else {
            jsonWriter.write(value.toString());
        }
    }
}

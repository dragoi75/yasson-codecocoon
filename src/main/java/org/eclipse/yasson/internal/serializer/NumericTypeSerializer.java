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
import java.math.BigDecimal;

/**
 * Serializer for {@link Number} type.
 *
 * @author David Kral
 */
public class NumericTypeSerializer extends AbstractValueTypeSerializer<Number> {

    @Override
    protected void serialize(Number value, JsonGenerator jsonWriter, Marshaller marshaller) {
        BigDecimal decimalAmount = new BigDecimal(String.valueOf(value));
        if (!BigNumberUtil.isIEEE754(decimalAmount)) {
            jsonWriter.write(String.valueOf(value));
        } else {
            jsonWriter.write(decimalAmount);
        }
    }

    /**
     * Creates a new instance.
     *
     * @param config Model customization.
     */
    public NumericTypeSerializer(Customization config) {
        super(config);
    }

}

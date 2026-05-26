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

import org.eclipse.yasson.internal.model.customization.Customization;
import javax.json.stream.JsonGenerator;
import java.math.BigDecimal;

/**
 * Serializer for {@link BigDecimal} type.
 *
 * @author David Kral
 */
public class BigDecimalSerializer extends AbstractNumberSerializer<BigDecimal> {

    @Override
    protected void serializeNonFormatted(BigDecimal decimalValue, JsonGenerator jsonWriter, String propertyName) {
        if (!BigNumberUtil.isIEEE754(decimalValue)) {
            jsonWriter.write(propertyName, decimalValue.toString());
        } else {
            jsonWriter.write(propertyName, decimalValue);
        }
    }

    @Override
    protected void serializeNonFormatted(BigDecimal decimalValue, JsonGenerator jsonWriter) {
        if (!BigNumberUtil.isIEEE754(decimalValue)) {
            jsonWriter.write(decimalValue.toString());
        } else {
            jsonWriter.write(decimalValue);
        }
    }

    /**
     * Creates a new instance.
     *
     * @param formatOptions Model customization.
     */
    public BigDecimalSerializer(Customization formatOptions) {
        super(formatOptions);
    }

}

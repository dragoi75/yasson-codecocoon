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
import java.math.BigDecimal;

/**
 * Serializer for {@link Float} type.
 *
 * @author David Kral
 */
public class FloatSerializer extends AbstractNumberSerializer<Float> {

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

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public FloatSerializer(Customization customConfig) {
        super(customConfig);
    }

}

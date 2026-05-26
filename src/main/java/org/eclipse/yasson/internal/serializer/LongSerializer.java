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

/**
 * Serializer for {@link Long} type.
 *
 * @author David Kral
 */
public class LongSerializer extends AbstractNumberSerializer<Long> {

    @Override
    protected void serializeNonFormatted(Long value, JsonGenerator outputWriter) {
        if (!BigNumberUtil.isIEEE754(value)) {
            outputWriter.write(value.toString());
        } else {
            outputWriter.write(value);
        }
    }

    @Override
    protected void serializeNonFormatted(Long value, JsonGenerator outputWriter, String fieldName) {
        if (!BigNumberUtil.isIEEE754(value)) {
            outputWriter.write(fieldName, value.toString());
        } else {
            outputWriter.write(fieldName, value);
        }
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public LongSerializer(Customization customConfig) {
        super(customConfig);
    }

}

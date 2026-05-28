/*******************************************************************************
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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

import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerator;

/**
 * Serializer for arrays of doubles.
 * @author Roman Grigoriadi
 */
public class DoubleArrayEncoder extends AbstractArraySerializer<double[]> {

    @Override
    protected void serializeInternal(double[] values, JsonGenerator jsonWriter, SerializationContext ctx) {
        for (double value : values) {
            jsonWriter.write(value);
        }
    }

    protected DoubleArrayEncoder(TypeSerializerBuilder typeSerializer) {
        super(typeSerializer);
    }

}

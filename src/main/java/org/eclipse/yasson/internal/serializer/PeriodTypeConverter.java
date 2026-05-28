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

import java.time.Period;

import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Serializer for {@link Period} type.
 */
public class PeriodTypeConverter extends AbstractValueTypeSerializer<Period> {

    @Override
    protected void serialize(Period periodValue, JsonGenerator jsonWriter, Marshaller marshaller) {
        jsonWriter.write(periodValue.toString());
    }

    /**
     * Creates a new instance.
     *
     * @param conversionOptions Model customization.
     */
    public PeriodTypeConverter(Customization conversionOptions) {
        super(conversionOptions);
    }

}

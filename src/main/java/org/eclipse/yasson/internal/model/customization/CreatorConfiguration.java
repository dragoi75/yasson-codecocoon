/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.model.PropertyMetadata;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * Customization for creator (constructor / factory methods) parameters.
 */
public class CreatorConfiguration extends CustomizationBase {

    private JsonbNumberFormatter numericFormatter;

    private JsonbDateFormatter temporalFormatter;

    private PropertyMetadata fieldMetadata;

    @Override
    public JsonbDateFormatter getDeserializeDateFormatter() {
        if (null == temporalFormatter) {
            if (null != fieldMetadata) {
                return fieldMetadata.getCustomization().getDeserializeDateFormatter();
            }
        } else {
            return temporalFormatter;
        }
        return null;
    }

    @Override
    public boolean isNillable() {
        throw new UnsupportedOperationException("Not supported for creator parameters.");
    }

    @Override
    public JsonbNumberFormatter getSerializeNumberFormatter() {
        throw new UnsupportedOperationException("Serialization is not supported for creator parameters.");
    }

    /**
     * Set property referenced model.
     *
     * @param fieldMetadata referenced property model
     */
    public void setPropertyModel(PropertyMetadata fieldMetadata) {
        this.fieldMetadata = fieldMetadata;
    }

    @Override
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        if (null == numericFormatter) {
            if (null != fieldMetadata) {
                return fieldMetadata.getCustomization().getDeserializeNumberFormatter();
            }
        } else {
            return numericFormatter;
        }
        return null;
    }

    /**
     * Creates new creator customization instance.
     *
     * @param customBuilder   builder of the customization
     * @param numericFormatter number formatter
     * @param temporalFormatter   date formatter
     */
    public CreatorConfiguration(CustomizationBuilder customBuilder, JsonbNumberFormatter numericFormatter, JsonbDateFormatter temporalFormatter) {
        super(customBuilder);
        this.numericFormatter = numericFormatter;
        this.temporalFormatter = temporalFormatter;
    }

    @Override
    public JsonbDateFormatter getSerializeDateFormatter() {
        throw new UnsupportedOperationException("Serialization is not supported for creator parameters.");
    }

}

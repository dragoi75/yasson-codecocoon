/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.deserializer.types;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import jakarta.json.bind.JsonbException;

import org.eclipse.yasson.internal.DefaultDeserializationContext;
import org.eclipse.yasson.internal.JsonbConfigurationProperties;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * Deserializer of the {@link String} type.
 */
class StringDeserializer extends TypeDeserializer {

    StringDeserializer(TypeDeserializerBuilder builder) {
        super(builder);
    }

    @Override
    public Object deserializeStringValue(String value, DefaultDeserializationContext context, Type rType) {
        JsonbConfigurationProperties config = context.getJsonbContext().getConfigProperties();
        return checkIJson(value, config);
    }

    private String checkIJson(String value, JsonbConfigurationProperties config) {
        if (config.isStrictIJson()) {
            String newString = new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            if (!newString.equals(value)) {
                throw new JsonbException(MessageProvider.getMessage(MessageConstants.UNPAIRED_SURROGATE));
            }
        }
        return value;
    }
}

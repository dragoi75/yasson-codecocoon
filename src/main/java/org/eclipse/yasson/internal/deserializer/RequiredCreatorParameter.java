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

package org.eclipse.yasson.internal.deserializer;

import jakarta.json.bind.JsonbException;

import org.eclipse.yasson.internal.DeserializationContextManager;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

class RequiredCreatorParameter implements ModelUnmarshaller<Object> {

    private final String parameterName;

    RequiredCreatorParameter(String parameterName) {
        this.parameterName = parameterName;
    }

    @Override
    public Object unmarshal(Object value, DeserializationContextManager context) {
        throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CREATOR_MISSING_PROPERTY, parameterName));
    }

}

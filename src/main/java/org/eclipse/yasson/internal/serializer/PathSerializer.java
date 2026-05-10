/*
 * Copyright (c) 2020 IBM and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;

import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.Marshaller;
import org.eclipse.yasson.internal.model.customization.Customization;

public class PathSerializer extends AbstractValueTypeSerializer<Path> {
    public PathSerializer(Customization customConfig) {
        super(customConfig);
    }

    @Override
    protected void serialize(Path pathValue, JsonGenerator jsonWriter, Marshaller marshaller) {
        jsonWriter.write(pathValue.toString());
    }
}
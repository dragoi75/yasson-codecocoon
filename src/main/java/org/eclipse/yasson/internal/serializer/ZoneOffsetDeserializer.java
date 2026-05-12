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

import org.eclipse.yasson.internal.Unmarshaller;
import org.eclipse.yasson.internal.model.customization.Customization;

import java.lang.reflect.Type;
import java.time.ZoneOffset;

/**
 * Deserializer for {@link ZoneOffset} type.
 * 
 * @author David Kral
 */
public class ZoneOffsetDeserializer extends AbstractValueTypeDeserializer<ZoneOffset> {

    /**
     * Creates a new instance.
     *
     * @param config customization
     */
    public ZoneOffsetDeserializer(Customization config) {
        super(ZoneOffset.class, config);
    }

    @Override
    protected ZoneOffset deserialize(String jsonText, Unmarshaller unmarshaller, Type rtType) {
        return ZoneOffset.of(jsonText);
    }
}

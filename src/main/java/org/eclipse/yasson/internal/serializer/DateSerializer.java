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

import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Serializer for {@link Date} type.
 *
 * @author David Kral
 */
public class DateSerializer<T extends Date> extends AbstractDateTypeSerializer<T> {

    private DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME.withZone(UTC);


    protected DateTimeFormatter getDefaultFormatter() {
        return dateTimeFormatter;
    }

    /**
     * Creates a new instance.
     *
     * @param customConfig Model customization.
     */
    public DateSerializer(Customization customConfig) {
        super(customConfig);
    }

}

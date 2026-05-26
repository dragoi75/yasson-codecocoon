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

package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.components.TypeAdapterBinding;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.components.JsonbSerializerBinding;

/**
 * Customization which is aware of bound components, such as adapters and (de)serializers.
 */
public interface ComponentBoundCustomization {

    /**
     * Deserializer wrapper with resolved generic info.
     *
     * @return deserializer wrapper
     */
    JsonbDeserializerBinding getDeserializerBinding();

    /**
     * Serializer wrapper with resolved generic info.
     *
     * @return serializer wrapper
     */
    JsonbSerializerBinding getSerializerBinding();

    /**
     * @return Adapter wrapper class with resolved generic information.
     */
    TypeAdapterBinding getSerializeAdapterBinding();

    /**
     * @return Adapter wrapper class with resolved generic information.
     */
    TypeAdapterBinding getDeserializeAdapterBinding();

}

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

package org.eclipse.yasson.internal;

import java.lang.reflect.Type;

/**
 * Holds runtime type and wrapper runtime type info if any.
 */
public class RuntimeTypeContainer implements RuntimeTypeInfo {

    private final RuntimeTypeInfo typeInfo;

    private final Type resolvedType;

    /**
     * Runtime type of this item.
     *
     * @return runtime type
     */
    @Override
    public Type getRuntimeType() {
        return resolvedType;
    }

    /**
     * Wrapper containing property of this type.
     *
     * @return wrapper
     */
    @Override
    public RuntimeTypeInfo getWrapper() {
        return typeInfo;
    }

    /**
     * Creates a new instance.
     *
     * @param typeInfo     runtime info about class
     * @param resolvedType class type
     */
    public RuntimeTypeContainer(RuntimeTypeInfo typeInfo, Type resolvedType) {
        this.typeInfo = typeInfo;
        this.resolvedType = resolvedType;
    }

}

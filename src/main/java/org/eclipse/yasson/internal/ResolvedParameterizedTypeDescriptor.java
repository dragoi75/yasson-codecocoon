/*
 * Copyright (c) 2016, 2022 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

/**
 * {@link ParameterizedType} implementation containing array of resolved TypeVariable type args.
 */
class ResolvedParameterizedTypeDescriptor implements ParameterizedType {

    /**
     * Original parameterized type.
     */
    private final ParameterizedType sourceParameterizedType;

    /**
     * Resolved args by runtime type.
     */
    private final Type[] typeArguments;

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (null == obj || !(obj instanceof ParameterizedType)) {
            return false;
        }
        final ParameterizedType otherParameterizedType = (ParameterizedType) obj;
        return this.getRawType().equals(otherParameterizedType.getRawType()) && Objects.equals(this.getOwnerType(), otherParameterizedType.getOwnerType()) && Arrays.equals(typeArguments, otherParameterizedType.getActualTypeArguments());
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(sourceParameterizedType.toString());
        if (null != typeArguments && 0 < typeArguments.length) {
            builder.append(" resolved arguments: [");
            for (Type typeArgument : typeArguments) {
                builder.append(typeArgument);
            }
            builder.append("]");
        }
        return builder.toString();
    }

    @Override
    public Type getOwnerType() {
        return sourceParameterizedType.getOwnerType();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(typeArguments) ^ (null == getOwnerType() ? 0 : getOwnerType().hashCode()) ^ (null == getRawType() ? 0 : getRawType().hashCode());
    }

    @Override
    public Type getRawType() {
        return sourceParameterizedType.getRawType();
    }

    /**
     * Creates a new instance.
     *
     * @param sourceParameterizedType         Original type.
     * @param typeArguments Resolved type arguments.
     */
    ResolvedParameterizedTypeDescriptor(ParameterizedType sourceParameterizedType, Type[] typeArguments) {
        this.sourceParameterizedType = sourceParameterizedType;
        this.typeArguments = typeArguments;
    }

    /**
     * Type arguments with resolved TypeVariables.
     *
     * @return type args
     */
    @Override
    public Type[] getActualTypeArguments() {
        return typeArguments;
    }

}

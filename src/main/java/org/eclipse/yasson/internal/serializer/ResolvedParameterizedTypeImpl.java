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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

/**
 * {@link ParameterizedType} implementation containing array of resolved TypeVariable type args.
 */
public class ResolvedParameterizedTypeImpl implements ParameterizedType {

    /**
     * Original parameterized type.
     */
    private final ParameterizedType baseType;

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
        final ParameterizedType otherType = (ParameterizedType) obj;
        return this.getRawType().equals(otherType.getRawType()) && Objects.equals(this.getOwnerType(), otherType.getOwnerType()) && Arrays.equals(typeArguments, otherType.getActualTypeArguments());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(typeArguments) ^ (null == getOwnerType() ? 0 : getOwnerType().hashCode()) ^ (null == getRawType() ? 0 : getRawType().hashCode());
    }

    @Override
    public Type getOwnerType() {
        return baseType.getOwnerType();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(baseType.toString());
        if (null != typeArguments && 0 < typeArguments.length) {
            builder.append(" resolved arguments: [");
            for (Type argumentType : typeArguments) {
                builder.append(String.valueOf(argumentType));
            }
            builder.append("]");
        }
        return builder.toString();
    }

    @Override
    public Type getRawType() {
        return baseType.getRawType();
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

    /**
     * Creates a new instance.
     *
     * @param baseType         Original type.
     * @param typeArguments Resolved type arguments.
     */
    public ResolvedParameterizedTypeImpl(ParameterizedType baseType, Type[] typeArguments) {
        this.baseType = baseType;
        this.typeArguments = typeArguments;
    }

}

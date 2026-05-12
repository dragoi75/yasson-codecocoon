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

import java.lang.reflect.Type;

import org.eclipse.yasson.internal.model.ClassDescriptor;

/**
 * Metadata wrapper for currently processed object.
 * References mapping models of an unmarshalled item,
 * creates instances of it, sets finished unmarshalled objects into object tree.
 *
 * @param <T> Instantiated object type
 */
public abstract class BaseItem<T> implements CurrentItem<T> {

    /**
     * Item containing instance of wrapping object and its metadata.
     * Null in case of a root object.
     */
    private final CurrentItem<?> currentItem;

    private final Type actualType;

    /**
     * Cached reference to mapping model of an item.
     */
    private final ClassDescriptor classDescriptor;

    /**
     * Creates and populates an instance from given builder.
     *
     * @param serializerFactory Builder to initialize from.
     */
    protected BaseItem(SerializerBuilderBase serializerFactory) {
        this.currentItem = serializerFactory.getWrapper();
        this.classDescriptor = serializerFactory.getClassModel();
        this.actualType = serializerFactory.getRuntimeType();
    }

    /**
     * Creates an instance.
     *
     * @param currentItem     Item wrapper.
     * @param actualType Runtime type.
     * @param classDescriptor  Class model.
     */
    public BaseItem(CurrentItem<?> currentItem, Type actualType, ClassDescriptor classDescriptor) {
        this.currentItem = currentItem;
        this.actualType = actualType;
        this.classDescriptor = classDescriptor;
    }

    @Override
    public ClassDescriptor getClassModel() {
        return classDescriptor;
    }

    @Override
    public CurrentItem<?> getWrapper() {
        return currentItem;
    }

    @Override
    public Type getRuntimeType() {
        return actualType;
    }

}

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

import org.eclipse.yasson.internal.model.ClassDescriptor;

import java.lang.reflect.Type;

/**
 * Metadata wrapper for currently processed object.
 * References mapping models of an unmarshalled item,
 * creates instances of it, sets finished unmarshalled objects into object tree.
 *
 * @param <T> Instantiated object type
 * @author Roman Grigoriadi
 */
public abstract class BaseItem<T> implements CurrentItem<T> {

    /**
     * Item containing instance of wrapping object and its metadata.
     * Null in case of a root object.
     */
    private final CurrentItem<?> currentItem;

    private final Type resolvedType;

    /**
     * Cached reference to mapping model of an item.
     */
    private final ClassDescriptor classDescriptor;

    /**
     * Creates and populates an instance from given builder.
     *
     * @param serializer Builder to initialize from.
     */
    protected BaseItem(AbstractSerializationBuilder serializer) {
        this.currentItem = serializer.getWrapper();
        this.classDescriptor = serializer.getClassModel();
        this.resolvedType = serializer.getRuntimeType();
    }

    /**
     * Creates an instance.
     *
     * @param currentItem Item wrapper.
     * @param resolvedType Runtime type.
     * @param classDescriptor Class model.
     */
    public BaseItem(CurrentItem<?> currentItem, Type resolvedType, ClassDescriptor classDescriptor) {
        this.currentItem = currentItem;
        this.resolvedType = resolvedType;
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
        return resolvedType;
    }

}

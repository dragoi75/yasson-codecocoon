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

import org.eclipse.yasson.internal.model.ClassModel;

import java.lang.reflect.Type;

/**
 * Metadata wrapper for currently processed object.
 * References mapping models of an unmarshalled item,
 * creates instances of it, sets finished unmarshalled objects into object tree.
 *
 * @param <T> Instantiated object type
 * @author Roman Grigoriadi
 */
public abstract class AbstractModelItem<T> implements CurrentItem<T> {

    /**
     * Item containing instance of wrapping object and its metadata.
     * Null in case of a root object.
     */
    private final CurrentItem<?> currentItem;

    private final Type actualType;

    /**
     * Cached reference to mapping model of an item.
     */
    private final ClassModel classMeta;

    @Override
    public Type getRuntimeType() {
        return actualType;
    }

    @Override
    public CurrentItem<?> getWrapper() {
        return currentItem;
    }

    /**
     * Creates and populates an instance from given builder.
     *
     * @param serializerFactory Builder to initialize from.
     */
    protected AbstractModelItem(BaseSerializerBuilder serializerFactory) {
        this.currentItem = serializerFactory.getWrapper();
        this.classMeta = serializerFactory.getClassModel();
        this.actualType = serializerFactory.getRuntimeType();
    }

    /**
     * Creates an instance.
     *
     * @param currentItem Item wrapper.
     * @param actualType Runtime type.
     * @param classMeta Class model.
     */
    public AbstractModelItem(CurrentItem<?> currentItem, Type actualType, ClassModel classMeta) {
        this.currentItem = currentItem;
        this.actualType = actualType;
        this.classMeta = classMeta;
    }

    @Override
    public ClassModel getClassModel() {
        return classMeta;
    }

}

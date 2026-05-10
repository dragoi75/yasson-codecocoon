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
import java.util.Objects;

import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.model.ClassModel;
import org.eclipse.yasson.internal.model.customization.Customization;

/**
 * Base class for serializer builders.
 *
 * @param <T> serialization builder type
 */
public class GenericSerializerBuilder<T extends GenericSerializerBuilder> {

    /**
     * Not null with an exception of a root item.
     */
    private CurrentItem<?> currentItem;

    /**
     * In case of unknown object genericType.
     * Null for embedded objects such as collections, or known conversion types.
     */
    private ClassModel classDescriptor;

    /**
     * Runtime type resolved after expanding type variables and wildcards.
     */
    private Type actualType;

    /**
     * Type is used when field model is not present.
     * In case of root, or embedded objects such as collections.
     */
    private Type typeParameter;

    /**
     * Class customization.
     */
    private Customization customConfig;

    /**
     * Jsonb context.
     */
    private final JsonbRuntimeContext jsonbRuntime;

    /**
     * Crates a builder.
     *
     * @param jsonbRuntime Not null.
     */
    public GenericSerializerBuilder(JsonbRuntimeContext jsonbRuntime) {
        Objects.requireNonNull(jsonbRuntime);
        this.jsonbRuntime = jsonbRuntime;
    }

    /**
     * Wrapper item for this item.
     *
     * @param currentItem not null.
     * @return Builder instance for call chaining.
     */
    @SuppressWarnings("unchecked")
    public T setWrapper(CurrentItem<?> currentItem) {
        this.currentItem = currentItem;
        return (T) this;
    }

    /**
     * Customization of the class.
     *
     * @param customConfig Class customization
     * @return Builder instance for call chaining.
     */
    @SuppressWarnings("unchecked")
    public T setCustomization(Customization customConfig) {
        this.customConfig = customConfig;
        return (T) this;
    }

    /**
     * Class model for this item.
     *
     * @param classDescriptor class model
     * @return Builder instance for call chaining.
     */
    @SuppressWarnings("unchecked")
    public T setClassModel(ClassModel classDescriptor) {
        this.classDescriptor = classDescriptor;
        return (T) this;
    }

    /**
     * Runtime type for this item.
     *
     * @param actualType runtime type
     * @return Builder instance for call chaining.
     */
    @SuppressWarnings("unchecked")
    public T setRuntimeType(Type actualType) {
        this.actualType = actualType;
        return (T) this;
    }

    /***
     * Gets or load class model for a class an its superclasses.
     *
     * @param rawClass Class to get model for.
     * @return Class model.
     */
    protected ClassModel getClassModel(Class<?> rawClass) {
        ClassModel classDescriptor = jsonbRuntime.getMappingContext().getClassModel(rawClass);
        if (classDescriptor == null) {
            classDescriptor = jsonbRuntime.getMappingContext().getOrCreateClassModel(rawClass);
        }
        return classDescriptor;
    }

    /**
     * Wrapper item for this item.
     *
     * @return Wrapper item.
     */
    public CurrentItem<?> getWrapper() {
        return currentItem;
    }

    /**
     * Model of a class representing current item and instance (if any).
     * Known collection classes doesn't need such a model.
     *
     * @return model of a class
     */
    public ClassModel getClassModel() {
        return classDescriptor;
    }

    /**
     * Resolved runtime type for instance in case of {@link java.lang.reflect.TypeVariable} or
     * {@link java.lang.reflect.WildcardType}.
     * Otherwise provided type in type field, or type of field model.
     *
     * @return runtime type
     */
    public Type getRuntimeType() {
        return actualType;
    }

    /**
     * Type for underlying instance to be created from.
     * In case of type variable or wildcard, will be resolved recursively from parent items.
     *
     * @param declaredType type of instance not null
     * @return builder instance for call chaining
     */
    @SuppressWarnings("unchecked")
    public T setType(Type declaredType) {
        this.typeParameter = declaredType;
        return (T) this;
    }

    /**
     * Jsonb runtime context.
     *
     * @return jsonb context
     */
    public JsonbRuntimeContext getJsonbContext() {
        return jsonbRuntime;
    }

    /**
     * Type customization.
     *
     * @return customization
     */
    public Customization getCustomization() {
        return customConfig;
    }

    /**
     * Generic type of the item.
     *
     * @return generic type
     */
    public Type getGenericType() {
        return typeParameter;
    }
}

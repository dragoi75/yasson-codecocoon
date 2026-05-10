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

import org.eclipse.yasson.internal.JsonbRuntimeContext;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.customization.Customization;

import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Base class for serializer builders.
 *
 * @author Roman Grigoriadi
 */
public class AbstractSerializationBuilder<T extends AbstractSerializationBuilder> {

    /**
     * Not null with an exception of a root item.
     */
    protected CurrentItem<?> wrapper;

    /**
     * In case of unknown object genericType.
     * Null for embedded objects such as collections, or known conversion types.
     */
    protected ClassDescriptor classModel;

    /**
     * Runtime type resolved after expanding type variables and wildcards.
     */
    protected Type runtimeType;

    /**
     * Type is used when field model is not present.
     * In case of root, or embedded objects such as collections.
     */
    protected Type genericType;

    /**
     * Class customization
     */
    protected Customization customization;

    protected final JsonbRuntimeContext jsonbContext;

    /**
     * Crates a builder.
     *
     * @param jsonbRuntime Not null.
     */
    public AbstractSerializationBuilder(JsonbRuntimeContext jsonbRuntime) {
        Objects.requireNonNull(jsonbRuntime);
        this.jsonbContext = jsonbRuntime;
    }

    /**
     * Wrapper item for this item.
     *
     * @param currentItem not null.
     * @return Builder instance for call chaining.
     */
    @SuppressWarnings("unchecked")
    public T setWrapper(CurrentItem<?> currentItem) {
        this.wrapper = currentItem;
        return (T) this;
    }

    /**
     * Customization of the class
     *
     * @param customConfig Class customization
     * @return Builder instance for call chaining.
     */
    @SuppressWarnings("unchecked")
    public T setCustomization(Customization customConfig) {
        this.customization = customConfig;
        return (T) this;
    }

    /***
     * Gets or load class model for a class an its superclasses.
     *
     * @param baseClass Class to get model for.
     * @return Class model.
     */
    protected ClassDescriptor getClassModel(Class<?> baseClass) {
        ClassDescriptor classDescriptor = jsonbContext.getMappingContext().getClassModel(baseClass);
        if (classDescriptor == null) {
            classDescriptor = jsonbContext.getMappingContext().getOrCreateClassModel(baseClass);
        }
        return classDescriptor;
    }

    /**
     * Wrapper item for this item.
     *
     * @return Wrapper item.
     */
    public CurrentItem<?> getWrapper() {
        return wrapper;
    }

    /**
     * Model of a class representing current item and instance (if any).
     * Known collection classes doesn't need such a model.
     *
     * @return model of a class
     */
    public ClassDescriptor getClassModel() {
        return classModel;
    }

    /**
     * Resolved runtime type for instance in case of {@link java.lang.reflect.TypeVariable} or {@link java.lang.reflect.WildcardType}
     * Otherwise provided type in type field, or type of field model.
     *
     * @return runtime type
     */
    public Type getRuntimeType() {
        return runtimeType;
    }

    /**
     * Type for underlying instance to be created from.
     * In case of type variable or wildcard, will be resolved recursively from parent items.
     *
     * @param valueType type of instance not null
     * @return builder instance for call chaining
     */
    @SuppressWarnings("unchecked")
    public T setType(Type valueType) {
        this.genericType = valueType;
        return (T) this;
    }

    /**
     * Jsonb runtime context.
     *
     * @return jsonb context
     */
    public JsonbRuntimeContext getJsonbContext() {
        return jsonbContext;
    }

    public Customization getCustomization() {
        return customization;
    }
}

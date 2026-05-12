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

package org.eclipse.yasson.internal.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jakarta.json.bind.JsonbException;

import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.MessageBundle;

/**
 * Object holding reference to Constructor / Method for custom object creation.
 */
public class JsonbCreatorInvoker {

    private final Executable callTarget;

    private final CreatorProfile[] creatorProfiles;

    /**
     * Creates a new instance.
     *
     * @param callTarget    Executable.
     * @param creatorProfiles Parameters.
     */
    public JsonbCreatorInvoker(Executable callTarget, CreatorProfile[] creatorProfiles) {
        this.callTarget = callTarget;
        this.creatorProfiles = creatorProfiles;
    }

    /**
     * Create instance by either constructor or factory method, with provided parameter values and a Class to call on.
     *
     * @param creatorProfiles parameters to be passed into constructor / factory method
     * @param targetType     class to call onto
     * @param <T>    Type of class / instance
     * @return instance
     */
    @SuppressWarnings("unchecked")
    public <T> T invoke(Object[] creatorProfiles, Class<T> targetType) {
        try {
            if (callTarget instanceof Constructor) {
                return ((Constructor<T>) callTarget).newInstance(creatorProfiles);
            } else {
                return (T) ((Method) callTarget).invoke(targetType, creatorProfiles);
            }
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException ex) {
            throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.ERROR_CALLING_JSONB_CREATOR, targetType), ex);
        }
    }

    /**
     * True if param name is one of creator params.
     *
     * @param name Param name to check.
     * @return True if found.
     */
    public boolean contains(String name) {
        return findByParamName(name) != null;
    }

    /**
     * Find creator parameter by name.
     *
     * @param name parameter name as it appear in json document.
     * @return Creator parameter.
     */
    public CreatorProfile findByParamName(String name) {
        for (CreatorProfile creatorProfile : creatorProfiles) {
            if (creatorProfile.getName().equals(name)) {
                return creatorProfile;
            }
        }
        return null;
    }

    /**
     * Parameters of this creator.
     *
     * @return Parameters.
     */
    public CreatorProfile[] getParams() {
        return creatorProfiles;
    }
}

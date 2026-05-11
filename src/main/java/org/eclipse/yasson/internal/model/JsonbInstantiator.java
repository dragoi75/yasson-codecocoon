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
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Object holding reference to Constructor / Method for custom object creation.
 */
public class JsonbInstantiator {

    private final Executable callableMember;

    private final CreatorProfile[] creatorProfiles;

    /**
     * Creates a new instance.
     *
     * @param callableMember    Executable.
     * @param creatorProfiles Parameters.
     */
    public JsonbInstantiator(Executable callableMember, CreatorProfile[] creatorProfiles) {
        this.callableMember = callableMember;
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
            if (!(callableMember instanceof Constructor)) {
                return (T) ((Method) callableMember).invoke(targetType, creatorProfiles);
            } else {
                return ((Constructor<T>) callableMember).newInstance(creatorProfiles);
            }
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException errorCause) {
            throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.ERROR_CALLING_JSONB_CREATOR, targetType), errorCause);
        }
    }

    /**
     * True if param name is one of creator params.
     *
     * @param parameterName Param name to check.
     * @return True if found.
     */
    public boolean contains(String parameterName) {
        return null != findParameterByName(parameterName);
    }

    /**
     * Find creator parameter by name.
     *
     * @param parameterName parameter name as it appear in json document.
     * @return Creator parameter.
     */
    public CreatorProfile findParameterByName(String parameterName) {
        for (CreatorProfile creatorProfile : creatorProfiles) {
            if (creatorProfile.getName().equals(parameterName)) {
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

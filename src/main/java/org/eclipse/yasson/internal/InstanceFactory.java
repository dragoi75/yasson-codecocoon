/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Creates instances for known types, caches constructors of unknown.
 * (Constructors of parsed types are stored in {@link org.eclipse.yasson.internal.model.ClassModel}).
 */
public class InstanceFactory {

    private static final InstanceFactory DEFAULT_FACTORY = new InstanceFactory();

    static InstanceFactory getSingleton() {
        return DEFAULT_FACTORY;
    }

    private static final Map<Class, Supplier> CLASS_TO_SUPPLIER_MAP = new HashMap<>();

    static {
        CLASS_TO_SUPPLIER_MAP.put(ArrayList.class, ArrayList::new);
        CLASS_TO_SUPPLIER_MAP.put(LinkedList.class, LinkedList::new);
        CLASS_TO_SUPPLIER_MAP.put(HashSet.class, HashSet::new);
        CLASS_TO_SUPPLIER_MAP.put(TreeSet.class, TreeSet::new);
        CLASS_TO_SUPPLIER_MAP.put(HashMap.class, HashMap::new);
        CLASS_TO_SUPPLIER_MAP.put(TreeMap.class, TreeMap::new);
    }

    private InstanceFactory() {
        if (null != DEFAULT_FACTORY) {
            throw new IllegalStateException("This class should never be instantiated");
        }
    }

    /**
     * Create an instance of the given class with its default constructor.
     *
     * @param targetClass class to create instance
     * @param <T>    Type of the class/instance
     * @return crated instance
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(Class<T> targetClass) {
        Supplier<T> supplier = CLASS_TO_SUPPLIER_MAP.get(targetClass);
        //No worries for race conditions here, instance may be replaced during first attempt.
        if (null == supplier) {
            Constructor<T> ctor = ReflectionHelper.getDefaultConstructor(targetClass, true);
            supplier = () -> ReflectionHelper.createInstanceUsingNoArgCtor(ctor);
            CLASS_TO_SUPPLIER_MAP.put(targetClass, supplier);
        }
        return supplier.get();
    }
}

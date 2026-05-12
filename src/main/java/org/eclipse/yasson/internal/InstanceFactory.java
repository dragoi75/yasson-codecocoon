/*******************************************************************************
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package org.eclipse.yasson.internal;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Creates instances for known types, caches constructors of unknown.
 * (Constructors of parsed types are stored in {@link org.eclipse.yasson.internal.model.ClassModel}).
 */
public class InstanceFactory {

    private interface InstanceFactory {
        Object newInstance();
    }

    /**
     * Caches default constructor to create instance.
     */
    private static final class InstanceCreator implements InstanceFactory {
        private final Constructor<?> instantiator;

        public InstanceCreator(Constructor<?> instantiator) {
            this.instantiator = instantiator;
        }

        @Override
        public Object newInstance() {
            return ReflectionTypeUtils.instantiateNoArgConstructor(instantiator);
        }
    }

    private final Map<Class, InstanceFactory> factoryMap;

    public InstanceFactory() {
        factoryMap = new HashMap<>();
        factoryMap.put(ArrayList.class, ArrayList::new);
        factoryMap.put(LinkedList.class, LinkedList::new);
        factoryMap.put(HashSet.class, HashSet::new);
        factoryMap.put(TreeSet.class, TreeSet::new);
        factoryMap.put(HashMap.class, HashMap::new);
        factoryMap.put(TreeMap.class, TreeMap::new);
    }

    /**
     * Create an instance of the given class with its default constructor.
     * @param clazz class to create instance
     * @param <T> Type of the class/instance
     * @return crated instance
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrCreateInstance(Class<T> clazz) {
        InstanceFactory factory = factoryMap.get(clazz);
        //No worries for race conditions here, instance may be replaced during first attempt.
        if (factory == null) {
            factory = new InstanceCreator(ReflectionTypeUtils.getDefaultConstructor(clazz, true));
            factoryMap.put(clazz, factory);
        }

        return (T) factory.newInstance();
    }
}

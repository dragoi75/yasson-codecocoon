/*
 * Copyright (c) 2015, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import jakarta.json.bind.JsonbException;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;
import org.eclipse.yasson.internal.serializer.AbstractWrappedItem;
import org.eclipse.yasson.internal.serializer.EmbeddedElement;
import org.eclipse.yasson.internal.serializer.ResolvedParameterizedType;

/**
 * Utility class for resolution of generics during unmarshalling.
 */
public class ReflectionHelper {

    private static final Logger LOG = Logger.getLogger(ReflectionHelper.class.getName());

    private ReflectionHelper() {
        throw new IllegalStateException("Utility classes should not be instantiated.");
    }

    /**
     * Get raw type by type.
     * Only for ParametrizedTypes, GenericArrayTypes and Classes.
     *
     * Empty optional is returned if raw type cannot be resolved.
     *
     * @param target Type to get class information from, not null.
     * @return Class of a type.
     */
    public static Optional<Class<?>> getOptionalRawType(Type target) {
        if (!(target instanceof Class)) {
            if (!(target instanceof ParameterizedType)) {
                if (target instanceof GenericArrayType) {
                    return Optional.of(((GenericArrayType) target).getClass());
                }
            } else {
                return Optional.of((Class<?>) ((ParameterizedType) target).getRawType());
            }
        } else {
            return Optional.of((Class<?>) target);
        }
        return Optional.empty();
    }

    /**
     * Get raw type by type.
     * Resolves only ParametrizedTypes, GenericArrayTypes and Classes.
     *
     * Exception is thrown if raw type cannot be resolved.
     *
     * @param target Type to get class information from, not null.
     * @return Class of a raw type.
     */
    public static Class<?> getRawType(Type target) {
        return getOptionalRawType(target).orElseThrow(() -> new JsonbException(Messages.getMessage(MessageKeys.TYPE_RESOLUTION_ERROR, target)));
    }

    /**
     * Get a raw type of any type.
     * If type is a {@link TypeVariable} recursively search {@link AbstractWrappedItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param runtimeInfo item containing wrapper class of a type field, not null.
     * @param target type to resolve, typically field type or generic bound, not null.
     * @return resolved raw class
     */
    public static Class<?> getRawType(RuntimeTypeInfo runtimeInfo, Type target) {
        if (!(target instanceof Class)) {
            if (!(target instanceof ParameterizedType)) {
                return getRawType(resolveActualType(runtimeInfo, target));
            } else {
                return (Class<?>) ((ParameterizedType) target).getRawType();
            }
        } else {
            return (Class<?>) target;
        }
    }

    /**
     * Resolve a type by item.
     * If type is a {@link TypeVariable} recursively search {@link AbstractWrappedItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param runtimeInfo item containing wrapper class of a type field, not null.
     * @param target type to resolve, typically field type or generic bound, not null.
     * @return resolved type
     */
    public static Type resolveActualType(RuntimeTypeInfo runtimeInfo, Type target) {
        return resolveActualType(runtimeInfo, target, true);
    }

    private static Type resolveActualType(RuntimeTypeInfo runtimeInfo, Type target, boolean logEnabled) {
        if (!(target instanceof WildcardType)) {
            if (!(target instanceof TypeVariable)) {
                if (target instanceof ParameterizedType && null != runtimeInfo) {
                    return resolveActualTypeArguments((ParameterizedType) target, runtimeInfo.getRuntimeType());
                }
            } else {
                return resolveItemTypeVariable(runtimeInfo, (TypeVariable<?>) target, logEnabled);
            }
        } else {
            return determineMostSpecificBound(runtimeInfo, (WildcardType) target, logEnabled);
        }
        return target;
    }

    /**
     * Resolves type by item information and wraps it with {@link Optional}.
     *
     * @param runtimeMetadata item information
     * @param target type
     * @return resolved type wrapped with Optional
     */
    public static Optional<Type> getOptionalType(RuntimeTypeInfo runtimeMetadata, Type target) {
        try {
            return Optional.of(resolveActualType(runtimeMetadata, target, false));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolve a bounded type variable type by its wrapper types.
     * Resolution could be done only if a compile time generic information is provided, either:
     * by generic field or subclass of a generic class.
     *
     * @param whether or not to log a warning message when bounds are not found
     * @param runtimeInfo         item to search "runtime" generic type of a TypeVariable.
     * @param genericVar type to search in item for, not null.
     * @return Type of a generic "runtime" bound, not null.
     */
    static Type resolveItemTypeVariable(RuntimeTypeInfo runtimeInfo, TypeVariable<?> genericVar, boolean logEnabled) {
        if (null == runtimeInfo) {
            //Bound not found, treat it as an Object.class
            if (logEnabled) {
                LOG.warning(Messages.getMessage(MessageKeys.GENERIC_BOUND_NOT_FOUND, genericVar, genericVar.getGenericDeclaration()));
            }
            return Object.class;
        }
        //Embedded items doesn't hold information about variable types
        if (runtimeInfo instanceof EmbeddedElement) {
            return resolveItemTypeVariable(runtimeInfo.getWrapper(), genericVar, logEnabled);
        }
        ParameterizedType wrapperParam = locateParameterizedSuperclass(runtimeInfo.getRuntimeType());
        VariableTypeInheritanceSearch finder = new VariableTypeInheritanceSearch();
        Type located = finder.searchParametrizedType(wrapperParam, genericVar);
        if (null != located) {
            if (located instanceof TypeVariable) {
                return resolveItemTypeVariable(runtimeInfo.getWrapper(), (TypeVariable<?>) located, logEnabled);
            }
            return located;
        }
        return resolveItemTypeVariable(runtimeInfo.getWrapper(), genericVar, logEnabled);
    }

    /**
     * Resolves {@link TypeVariable} arguments of generic types.
     *
     * @param toResolve type to resolve
     * @param searchTarget  type to search
     * @return resolved type
     */
    public static Type resolveActualTypeArguments(ParameterizedType toResolve, Type searchTarget) {
        final Type[] pendingArgs = toResolve.getActualTypeArguments();
        Type[] concreteArgs = new Type[pendingArgs.length];
        int idx = 0;
        while (pendingArgs.length > idx) {
            if ((pendingArgs[idx] instanceof TypeVariable)) {
                concreteArgs[idx] = new VariableTypeInheritanceSearch().searchParametrizedType(searchTarget, (TypeVariable<?>) pendingArgs[idx]);
                if (null == concreteArgs[idx]) {
                    //No generic information available
                    throw new IllegalStateException(Messages.getMessage(MessageKeys.GENERIC_BOUND_NOT_FOUND, pendingArgs[idx], searchTarget));
                }
            } else {
                concreteArgs[idx] = pendingArgs[idx];
            }
            if (concreteArgs[idx] instanceof ParameterizedType) {
                concreteArgs[idx] = resolveActualTypeArguments((ParameterizedType) concreteArgs[idx], searchTarget);
            }
            idx += 1;
        }
        return Arrays.equals(concreteArgs, pendingArgs) ? toResolve : new ResolvedParameterizedType(toResolve, concreteArgs);
    }

    /**
     * Create instance with constructor.
     *
     * @param ctor const not null
     * @param <T>         type of instance
     * @return instance
     */
    public static <T> T createInstanceUsingNoArgCtor(Constructor<T> ctor) {
        Objects.requireNonNull(ctor);
        try {
            return ctor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new JsonbException("Can't create instance", exception);
        }
    }

    /**
     * Get default no argument constructor of the class.
     *
     * @param targetClass    Class to get constructor from
     * @param <T>      Class generic type
     * @param isRequired if true, throws an exception if the default constructor is missing.
     *                 If false, returns null in that case
     * @return the constructor of the class, or null. Depending on required.
     */
    public static <T> Constructor<T> getDefaultConstructor(Class<T> targetClass, boolean isRequired) {
        Objects.requireNonNull(targetClass);
        return AccessController.doPrivileged((PrivilegedAction<Constructor<T>>) () -> {
            try {
                final Constructor<T> declaredCtor = targetClass.getDeclaredConstructor();
                if (Modifier.PROTECTED == declaredCtor.getModifiers()) {
                    declaredCtor.setAccessible(true);
                }
                return declaredCtor;
            } catch (NoSuchMethodException | RuntimeException exception) {
                if (isRequired) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.NO_DEFAULT_CONSTRUCTOR, targetClass), exception);
                }
                return null;
            }
        });
    }

    /**
     * For generic adapters like:
     * <p>
     * {@code
     * interface ContainerAdapter<T> extends JsonbAdapter<Box<T>, Crate<T>>...;
     * class IntegerBoxToCrateAdapter implements ContainerAdapter<Integer>...;
     * }
     * </p>
     * We need to find a JsonbAdapter class which will hold basic generic type arguments,
     * and resolve them if they are TypeVariables from there.
     *
     * @param searchClass          class to resolve parameterized interface
     * @param targetInterface interface to search
     * @return type of JsonbAdapter
     */
    public static ParameterizedType locateParameterizedType(Class<?> searchClass, Class<?> targetInterface) {
        Class currentClass = searchClass;
        while (Object.class != currentClass) {
            for (Type currentIface : currentClass.getGenericInterfaces()) {
                if (currentIface instanceof ParameterizedType && targetInterface.isAssignableFrom(ReflectionHelper.getRawType(((ParameterizedType) currentIface).getRawType()))) {
                    return (ParameterizedType) currentIface;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        throw new JsonbException(Messages.getMessage(MessageKeys.NON_PARAMETRIZED_TYPE, targetInterface));
    }

    /**
     * Check if type needs resolution. If type is a class or a parametrized type with all type arguments as classes
     * than it is considered resolved. If any of types is type variable or wildcard type is not resolved.
     *
     * @param target Type to check.
     * @return True if resolved
     */
    public static boolean isResolvedType(Type target) {
        if (target instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) target).getActualTypeArguments()) {
                if (!isResolvedType(argument)) {
                    return false;
                }
            }
            return true;
        }
        return target instanceof Class<?>;
    }

    private static ParameterizedType locateParameterizedSuperclass(Type target) {
        if (null == target || target instanceof ParameterizedType) {
            return (ParameterizedType) target;
        }
        if (!(target instanceof Class)) {
            throw new JsonbException("Can't resolve ParameterizedType superclass for: " + target);
        }
        return locateParameterizedSuperclass(((Class) target).getGenericSuperclass());
    }

    /**
     * Resolves a wildcard most specific upper or lower bound.
     *
     * @param runtimeInfo         Type.
     * @param wildcard Wildcard type.
     * @return The most specific type.
     */
    private static Type determineMostSpecificBound(RuntimeTypeInfo runtimeInfo, WildcardType wildcard, boolean logEnabled) {
        Class<?> mostSpecificClass = Object.class;
        for (Type upperType : wildcard.getUpperBounds()) {
            mostSpecificClass = getMostSpecificBound(runtimeInfo, mostSpecificClass, upperType, logEnabled);
        }
        for (Type lowerType : wildcard.getLowerBounds()) {
            mostSpecificClass = getMostSpecificBound(runtimeInfo, mostSpecificClass, lowerType, logEnabled);
        }
        return mostSpecificClass;
    }

    private static Class<?> getMostSpecificBound(RuntimeTypeInfo runtimeInfo, Class<?> mostSpecificClass, Type candidateType, boolean logEnabled) {
        if (Object.class == candidateType) {
            return mostSpecificClass;
        }
        //if bound is type variable search recursively for wrapper generic expansion
        Type concreteType = candidateType instanceof TypeVariable ? resolveActualType(runtimeInfo, candidateType, logEnabled) : candidateType;
        Class<?> rawTypeClass = getRawType(concreteType);
        //resolved class is a subclass of a result candidate
        if (mostSpecificClass.isAssignableFrom(rawTypeClass)) {
            mostSpecificClass = rawTypeClass;
        }
        return mostSpecificClass;
    }
}

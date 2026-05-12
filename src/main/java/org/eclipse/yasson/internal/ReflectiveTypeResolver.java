/*
 * Copyright (c) 2015, 2021 Oracle and/or its affiliates. All rights reserved.
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
import org.eclipse.yasson.internal.serializer.AbstractItem;
import org.eclipse.yasson.internal.serializer.EmbeddedItem;
import org.eclipse.yasson.internal.serializer.ResolvedParameterizedType;

/**
 * Utility class for resolution of generics during unmarshalling.
 */
public class ReflectiveTypeResolver {

    private static final Logger DEFAULT_LOG = Logger.getLogger(ReflectiveTypeResolver.class.getName());

    private ReflectiveTypeResolver() {
        throw new IllegalStateException("Utility classes should not be instantiated.");
    }

    /**
     * Get raw type by type.
     * Only for ParametrizedTypes, GenericArrayTypes and Classes.
     *
     * Empty optional is returned if raw type cannot be resolved.
     *
     * @param subject Type to get class information from, not null.
     * @return Class of a type.
     */
    public static Optional<Class<?>> getOptionalRawType(Type subject) {
        if (subject instanceof Class) {
            return Optional.of((Class<?>) subject);
        } else if (subject instanceof ParameterizedType) {
            return Optional.of((Class<?>) ((ParameterizedType) subject).getRawType());
        } else if (subject instanceof GenericArrayType) {
            return Optional.of(((GenericArrayType) subject).getClass());
        } else if (subject instanceof TypeVariable) {
            TypeVariable<?> genericParam = TypeVariable.class.cast(subject);
            if (Objects.nonNull(genericParam.getBounds())) {
                Optional<Class<?>> specificClass = Optional.empty();
                for (Type constraint : genericParam.getBounds()) {
                    Optional<Class<?>> resolvedRaw = getOptionalRawType(constraint);
                    if (resolvedRaw.isPresent() && !Object.class.equals(resolvedRaw.get())) {
                        if (!specificClass.isPresent() || specificClass.get().isAssignableFrom(resolvedRaw.get())) {
                            specificClass = Optional.of(resolvedRaw.get());
                        }
                    }
                }
                return specificClass;
            }
        }
        return Optional.empty();
    }

    /**
     * Get raw type by type.
     * Resolves only ParametrizedTypes, GenericArrayTypes and Classes.
     *
     * Exception is thrown if raw type cannot be resolved.
     *
     * @param subject Type to get class information from, not null.
     * @return Class of a raw type.
     */
    public static Class<?> getRawType(Type subject) {
        return getOptionalRawType(subject)
                .orElseThrow(() -> new JsonbException(Messages.getMessage(MessageKeys.TYPE_RESOLUTION_ERROR, subject)));
    }

    /**
     * Get a raw type of any type.
     * If type is a {@link TypeVariable} recursively search {@link AbstractItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param element item containing wrapper class of a type field, not null.
     * @param subject type to resolve, typically field type or generic bound, not null.
     * @return resolved raw class
     */
    public static Class<?> getRawType(RuntimeTypeInfo element, Type subject) {
        if (subject instanceof Class) {
            return (Class<?>) subject;
        } else if (subject instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) subject).getRawType();
        } else {
            return getRawType(resolveTypeDefault(element, subject));
        }
    }
    
    /**
     * Resolve a type by item.
     * If type is a {@link TypeVariable} recursively search {@link AbstractItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param element item containing wrapper class of a type field, not null.
     * @param subject type to resolve, typically field type or generic bound, not null.
     * @return resolved type
     */
    public static Type resolveTypeDefault(RuntimeTypeInfo element, Type subject) {
        return resolveTypeDefault(element, subject, true);
    }

    private static Type resolveTypeDefault(RuntimeTypeInfo element, Type subject, boolean shouldLog) {
        if (subject instanceof WildcardType) {
            return resolveTightestBound(element, (WildcardType) subject, shouldLog);
        } else if (subject instanceof TypeVariable) {
            return resolveItemTypeVariable(element, (TypeVariable<?>) subject, shouldLog);
        } else if (subject instanceof ParameterizedType && element != null) {
            return resolveGenericArguments((ParameterizedType) subject, element.getRuntimeType());
        }
        return subject;
    }

    /**
     * Resolves type by item information and wraps it with {@link Optional}.
     *
     * @param runtimeDescriptor item information
     * @param subject type
     * @return resolved type wrapped with Optional
     */
    public static Optional<Type> resolveTypeOptional(RuntimeTypeInfo runtimeDescriptor, Type subject) {
        try {
            return Optional.of(resolveTypeDefault(runtimeDescriptor, subject, false));
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
     * @param element         item to search "runtime" generic type of a TypeVariable.
     * @param genericParam type to search in item for, not null.
     * @return Type of a generic "runtime" bound, not null.
     */
    static Type resolveItemTypeVariable(RuntimeTypeInfo element, TypeVariable<?> genericParam, boolean shouldLog) {
        if (element == null) {
            Optional<Class<?>> rawClassOpt = getOptionalRawType(genericParam);
            if (rawClassOpt.isPresent()) {
                return rawClassOpt.get();
            }
            
            //Bound not found, treat it as an Object.class
            if (shouldLog) {
                DEFAULT_LOG.warning(Messages.getMessage(MessageKeys.GENERIC_BOUND_NOT_FOUND,
                        genericParam,
                                                   genericParam.getGenericDeclaration()));
            }
            return Object.class;
        }

        //Embedded items doesn't hold information about variable types
        if (element instanceof EmbeddedItem) {
            return resolveItemTypeVariable(element.getWrapper(), genericParam, shouldLog);
        }

        ParameterizedType wrapperGeneric = findParameterizedSupertype(element.getRuntimeType());

        VariableTypeInheritanceSearch inheritanceFinder = new VariableTypeInheritanceSearch();
        Type resolved = inheritanceFinder.searchParametrizedType(wrapperGeneric, genericParam);
        if (resolved != null) {
            if (resolved instanceof TypeVariable) {
                return resolveItemTypeVariable(element.getWrapper(), (TypeVariable<?>) resolved, shouldLog);
            }
            return resolved;
        }

        return resolveItemTypeVariable(element.getWrapper(), genericParam, shouldLog);
    }

    /**
     * Resolves {@link TypeVariable} arguments of generic types.
     *
     * @param targetParameterized type to resolve
     * @param lookupTarget  type to search
     * @return resolved type
     */
    public static Type resolveGenericArguments(ParameterizedType targetParameterized, Type lookupTarget) {
        final Type[] pendingArgs = targetParameterized.getActualTypeArguments();
        Type[] finalArgs = new Type[pendingArgs.length];
        for (int idx = 0; idx < pendingArgs.length; idx++) {
            if (!(pendingArgs[idx] instanceof TypeVariable)) {
                finalArgs[idx] = pendingArgs[idx];
            } else {
                finalArgs[idx] = new VariableTypeInheritanceSearch()
                        .searchParametrizedType(lookupTarget, (TypeVariable<?>) pendingArgs[idx]);
                if (finalArgs[idx] == null) {
                    //No generic information available
                    throw new IllegalStateException(Messages.getMessage(MessageKeys.GENERIC_BOUND_NOT_FOUND,
                                                                        pendingArgs[idx],
                            lookupTarget));
                }
            }
            if (finalArgs[idx] instanceof ParameterizedType) {
                finalArgs[idx] = resolveGenericArguments((ParameterizedType) finalArgs[idx], lookupTarget);
            }
        }
        return Arrays.equals(finalArgs, pendingArgs)
                ? targetParameterized
                : new ResolvedParameterizedType(targetParameterized, finalArgs);
    }

    /**
     * Create instance with constructor.
     *
     * @param ctor const not null
     * @param <T>         type of instance
     * @return instance
     */
    public static <T> T instantiateNoArg(Constructor<T> ctor) {
        Objects.requireNonNull(ctor);
        try {
            return ctor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new JsonbException("Can't create instance", ex);
        }
    }

    /**
     * Get default no argument constructor of the class.
     *
     * @param targetClass    Class to get constructor from
     * @param <T>      Class generic type
     * @param mustExist if true, throws an exception if the default constructor is missing.
     *                 If false, returns null in that case
     * @return the constructor of the class, or null. Depending on required.
     */
    public static <T> Constructor<T> getDefaultConstructor(Class<T> targetClass, boolean mustExist) {
        Objects.requireNonNull(targetClass);
        return AccessController.doPrivileged((PrivilegedAction<Constructor<T>>) () -> {
            try {
                final Constructor<T> foundCtor = targetClass.getDeclaredConstructor();
                if (foundCtor.getModifiers() == Modifier.PROTECTED) {
                    foundCtor.setAccessible(true);
                }
                return foundCtor;
            } catch (NoSuchMethodException | RuntimeException ex) {
                if (mustExist) {
                    throw new JsonbException(Messages.getMessage(MessageKeys.NO_DEFAULT_CONSTRUCTOR, targetClass), ex);
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
     * @param targetClass          class to resolve parameterized interface
     * @param interfaceClass interface to search
     * @return type of JsonbAdapter
     */
    public static ParameterizedType findParameterizedInterface(Class<?> targetClass, Class<?> interfaceClass) {
        Class activeClass = targetClass;
        while (activeClass != Object.class) {
            for (Type activeInterface : activeClass.getGenericInterfaces()) {
                if (activeInterface instanceof ParameterizedType
                        && interfaceClass.isAssignableFrom(
                        ReflectiveTypeResolver.getRawType(((ParameterizedType) activeInterface).getRawType()))) {
                    return (ParameterizedType) activeInterface;
                }
            }
            activeClass = activeClass.getSuperclass();
        }
        throw new JsonbException(Messages.getMessage(MessageKeys.NON_PARAMETRIZED_TYPE, interfaceClass));
    }

    /**
     * Check if type needs resolution. If type is a class or a parametrized type with all type arguments as classes
     * than it is considered resolved. If any of types is type variable or wildcard type is not resolved.
     *
     * @param subject Type to check.
     * @return True if resolved
     */
    public static boolean isResolvedType(Type subject) {
        if (subject instanceof ParameterizedType) {
            for (Type argumentType : ((ParameterizedType) subject).getActualTypeArguments()) {
                if (!isResolvedType(argumentType)) {
                    return false;
                }
            }
            return true;
        }
        return subject instanceof Class<?>;
    }

    private static ParameterizedType findParameterizedSupertype(Type subject) {
        if (subject == null || subject instanceof ParameterizedType) {
            return (ParameterizedType) subject;
        }
        if (!(subject instanceof Class)) {
            throw new JsonbException("Can't resolve ParameterizedType superclass for: " + subject);
        }
        return findParameterizedSupertype(((Class) subject).getGenericSuperclass());
    }

    /**
     * Resolves a wildcard most specific upper or lower bound.
     *
     * @param element         Type.
     * @param wildcardDescriptor Wildcard type.
     * @return The most specific type.
     */
    private static Type resolveTightestBound(RuntimeTypeInfo element, WildcardType wildcardDescriptor, boolean shouldLog) {
        Class<?> resolvedClass = Object.class;
        for (Type upperConstraint : wildcardDescriptor.getUpperBounds()) {
            resolvedClass = getMostSpecificBound(element, resolvedClass, upperConstraint, shouldLog);
        }
        for (Type lowerConstraint : wildcardDescriptor.getLowerBounds()) {
            resolvedClass = getMostSpecificBound(element, resolvedClass, lowerConstraint, shouldLog);
        }
        return resolvedClass;
    }

    private static Class<?> getMostSpecificBound(RuntimeTypeInfo element, Class<?> resolvedClass, Type constraint, boolean shouldLog) {
        if (constraint == Object.class) {
            return resolvedClass;
        }
        //if bound is type variable search recursively for wrapper generic expansion
        Type resolvedType = constraint instanceof TypeVariable ? resolveTypeDefault(element, constraint, shouldLog) : constraint;
        Class<?> resolvedRaw = getRawType(resolvedType);
        //resolved class is a subclass of a result candidate
        if (resolvedClass.isAssignableFrom(resolvedRaw)) {
            resolvedClass = resolvedRaw;
        }
        return resolvedClass;
    }
}

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
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.serializer.BaseItem;
import org.eclipse.yasson.internal.serializer.EmbeddedElement;
import org.eclipse.yasson.internal.serializer.ResolvedParameterizedTypeImpl;

/**
 * Utility class for resolution of generics during unmarshalling.
 */
public class ReflectionTypeResolver {

    private static final Logger LOG = Logger.getLogger(ReflectionTypeResolver.class.getName());

    private ReflectionTypeResolver() {
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
        return getOptionalRawType(target).orElseThrow(() -> new JsonbException(MessageBundle.getMessage(MessageKeyConstants.TYPE_RESOLUTION_ERROR, target)));
    }

    /**
     * Get a raw type of any type.
     * If type is a {@link TypeVariable} recursively search {@link BaseItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param descriptor item containing wrapper class of a type field, not null.
     * @param target type to resolve, typically field type or generic bound, not null.
     * @return resolved raw class
     */
    public static Class<?> getRawClass(RuntimeTypeDescriptor descriptor, Type target) {
        if (!(target instanceof Class)) {
            if (!(target instanceof ParameterizedType)) {
                return getRawType(resolveTypeDefault(descriptor, target));
            } else {
                return (Class<?>) ((ParameterizedType) target).getRawType();
            }
        } else {
            return (Class<?>) target;
        }
    }

    /**
     * Resolve a type by item.
     * If type is a {@link TypeVariable} recursively search {@link BaseItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param descriptor item containing wrapper class of a type field, not null.
     * @param target type to resolve, typically field type or generic bound, not null.
     * @return resolved type
     */
    public static Type resolveTypeDefault(RuntimeTypeDescriptor descriptor, Type target) {
        return resolveTypeDefault(descriptor, target, true);
    }

    private static Type resolveTypeDefault(RuntimeTypeDescriptor descriptor, Type target, boolean shouldReport) {
        if (!(target instanceof WildcardType)) {
            if (!(target instanceof TypeVariable)) {
                if (target instanceof ParameterizedType && null != descriptor) {
                    return resolveGenericArguments((ParameterizedType) target, descriptor.getRuntimeType());
                }
            } else {
                return resolveItemTypeVariable(descriptor, (TypeVariable<?>) target, shouldReport);
            }
        } else {
            return getMostSpecificBound(descriptor, (WildcardType) target, shouldReport);
        }
        return target;
    }

    /**
     * Resolves type by item information and wraps it with {@link Optional}.
     *
     * @param descriptor item information
     * @param target type
     * @return resolved type wrapped with Optional
     */
    public static Optional<Type> resolveTypeOptional(RuntimeTypeDescriptor descriptor, Type target) {
        try {
            return Optional.of(resolveTypeDefault(descriptor, target, false));
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
     * @param descriptor         item to search "runtime" generic type of a TypeVariable.
     * @param paramVar type to search in item for, not null.
     * @return Type of a generic "runtime" bound, not null.
     */
    static Type resolveItemTypeVariable(RuntimeTypeDescriptor descriptor, TypeVariable<?> paramVar, boolean shouldReport) {
        if (null == descriptor) {
            //Bound not found, treat it as an Object.class
            if (shouldReport) {
                LOG.warning(MessageBundle.getMessage(MessageKeyConstants.GENERIC_BOUND_NOT_FOUND, paramVar, paramVar.getGenericDeclaration()));
            }
            return Object.class;
        }
        //Embedded items doesn't hold information about variable types
        if (descriptor instanceof EmbeddedElement) {
            return resolveItemTypeVariable(descriptor.getWrapper(), paramVar, shouldReport);
        }
        ParameterizedType wrapperParam = locateParameterizedSuperclass(descriptor.getRuntimeType());
        TypeVariableInheritanceSearcher finder = new TypeVariableInheritanceSearcher();
        Type found = finder.findParametrizedType(wrapperParam, paramVar);
        if (null != found) {
            if (found instanceof TypeVariable) {
                return resolveItemTypeVariable(descriptor.getWrapper(), (TypeVariable<?>) found, shouldReport);
            }
            return found;
        }
        return resolveItemTypeVariable(descriptor.getWrapper(), paramVar, shouldReport);
    }

    /**
     * Resolves {@link TypeVariable} arguments of generic types.
     *
     * @param toResolve type to resolve
     * @param searchTarget  type to search
     * @return resolved type
     */
    public static Type resolveGenericArguments(ParameterizedType toResolve, Type searchTarget) {
        final Type[] pendingArgs = toResolve.getActualTypeArguments();
        Type[] finalArgs = new Type[pendingArgs.length];
        int idx = 0;
        while (pendingArgs.length > idx) {
            if ((pendingArgs[idx] instanceof TypeVariable)) {
                finalArgs[idx] = new TypeVariableInheritanceSearcher().findParametrizedType(searchTarget, (TypeVariable<?>) pendingArgs[idx]);
                if (null == finalArgs[idx]) {
                    //No generic information available
                    throw new IllegalStateException(MessageBundle.getMessage(MessageKeyConstants.GENERIC_BOUND_NOT_FOUND, pendingArgs[idx], searchTarget));
                }
            } else {
                finalArgs[idx] = pendingArgs[idx];
            }
            if (finalArgs[idx] instanceof ParameterizedType) {
                finalArgs[idx] = resolveGenericArguments((ParameterizedType) finalArgs[idx], searchTarget);
            }
            idx += 1;
        }
        return Arrays.equals(finalArgs, pendingArgs) ? toResolve : new ResolvedParameterizedTypeImpl(toResolve, finalArgs);
    }

    /**
     * Create instance with constructor.
     *
     * @param ctor const not null
     * @param <T>         type of instance
     * @return instance
     */
    public static <T> T instantiateNoArgs(Constructor<T> ctor) {
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
     * @param cls    Class to get constructor from
     * @param <T>      Class generic type
     * @param mandatory if true, throws an exception if the default constructor is missing.
     *                 If false, returns null in that case
     * @return the constructor of the class, or null. Depending on required.
     */
    public static <T> Constructor<T> getDefaultConstructor(Class<T> cls, boolean mandatory) {
        Objects.requireNonNull(cls);
        return AccessController.doPrivileged((PrivilegedAction<Constructor<T>>) () -> {
            try {
                final Constructor<T> foundCtor = cls.getDeclaredConstructor();
                if (Modifier.PROTECTED == foundCtor.getModifiers()) {
                    foundCtor.setAccessible(true);
                }
                return foundCtor;
            } catch (NoSuchMethodException ex) {
                if (mandatory) {
                    throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.NO_DEFAULT_CONSTRUCTOR, cls), ex);
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
     * @param genericInterface interface to search
     * @return type of JsonbAdapter
     */
    public static ParameterizedType findParameterizedInterface(Class<?> targetClass, Class<?> genericInterface) {
        Class cls = targetClass;
        while (Object.class != cls) {
            for (Type iface : cls.getGenericInterfaces()) {
                if (iface instanceof ParameterizedType && genericInterface.isAssignableFrom(ReflectionTypeResolver.getRawType(((ParameterizedType) iface).getRawType()))) {
                    return (ParameterizedType) iface;
                }
            }
            cls = cls.getSuperclass();
        }
        throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.NON_PARAMETRIZED_TYPE, genericInterface));
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
            for (Type arg : ((ParameterizedType) target).getActualTypeArguments()) {
                if (!isResolvedType(arg)) {
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
     * @param descriptor         Type.
     * @param wildcard Wildcard type.
     * @return The most specific type.
     */
    private static Type getMostSpecificBound(RuntimeTypeDescriptor descriptor, WildcardType wildcard, boolean shouldReport) {
        Class<?> resolvedClass = Object.class;
        for (Type upperType : wildcard.getUpperBounds()) {
            resolvedClass = getMostSpecificBound(descriptor, resolvedClass, upperType, shouldReport);
        }
        for (Type lowerType : wildcard.getLowerBounds()) {
            resolvedClass = getMostSpecificBound(descriptor, resolvedClass, lowerType, shouldReport);
        }
        return resolvedClass;
    }

    private static Class<?> getMostSpecificBound(RuntimeTypeDescriptor descriptor, Class<?> resolvedClass, Type typeConstraint, boolean shouldReport) {
        if (Object.class == typeConstraint) {
            return resolvedClass;
        }
        //if bound is type variable search recursively for wrapper generic expansion
        Type resolvedType = typeConstraint instanceof TypeVariable ? resolveTypeDefault(descriptor, typeConstraint, shouldReport) : typeConstraint;
        Class<?> rawTypeClass = getRawType(resolvedType);
        //resolved class is a subclass of a result candidate
        if (resolvedClass.isAssignableFrom(rawTypeClass)) {
            resolvedClass = rawTypeClass;
        }
        return resolvedClass;
    }
}

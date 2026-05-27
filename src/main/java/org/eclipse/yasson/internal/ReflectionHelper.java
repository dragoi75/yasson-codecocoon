/*
 * Copyright (c) 2015, 2022 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import jakarta.json.bind.JsonbException;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * Utility class for resolution of generics during unmarshalling.
 */
public class ReflectionHelper {

    private static final Logger LOGGER = Logger.getLogger(ReflectionHelper.class.getName());

    public static final class DefaultGenericArrayType implements GenericArrayType {

        private final Type component;

        @Override
        public int hashCode() {
            return Objects.hashCode(component);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof GenericArrayType)) {
                return false;
            } else {
                GenericArrayType otherArray = (GenericArrayType) other;
                return Objects.equals(component, otherArray.getGenericComponentType());
            }
        }

        // private constructor enforces use of static factory
        private DefaultGenericArrayType(Type component) {
            this.component = component;
        }

        /**
         * Returns a {@code Type} object representing the component type
         * of this array.
         *
         * @return a {@code Type} object representing the component type
         *     of this array
         * @since 1.5
         */
        public Type getGenericComponentType() {
            // return cached component type
            return component;
        }

        public String toString() {
            return getGenericComponentType().getTypeName() + "[]";
        }

    }

    /**
     * Resolve a bounded type variable type by its wrapper types.
     * Resolution could be done only if a compile time generic information is provided, either:
     * by generic field or subclass of a generic class.
     *
     * @param resolutionPath        chain to search "runtime" generic type of a TypeVariable.
     * @param genericParameter type to search in chain for, not null.
     * @param shouldReport         whether or not to log a warning message when bounds are not found
     * @return Type of a generic "runtime" bound, not null.
     */
    public static Type resolveItemTypeVariable(List<Type> resolutionPath, TypeVariable<?> genericParameter, boolean shouldReport) {
        //        if (chain == null) {
        //        Optional<Class<?>> optionalRawType = getOptionalRawType(typeVariable);
        //        if (optionalRawType.isPresent()) {
        //            return optionalRawType.get();
        //        }
        //            //Bound not found, treat it as an Object.class
        //            if (warn) {
        //                LOGGER.warning(Messages.getMessage(MessageKeys.GENERIC_BOUND_NOT_FOUND,
        //                                                   typeVariable,
        //                                                   typeVariable.getGenericDeclaration()));
        //            }
        //            return Object.class;
        //        }
        Type result = genericParameter;
        //        //Embedded items doesn't hold information about variable types
        //        if (chain instanceof EmbeddedItem) {
        //            return resolveItemVariableType(chain.getWrapper(), typeVariable, warn);
        //        }
        //
        //        ParameterizedType wrapperParameterizedType = findParameterizedSuperclass(chain.getRuntimeType());
        //
        //        VariableTypeInheritanceSearch search = new VariableTypeInheritanceSearch();
        //        Type foundType = search.searchParametrizedType(wrapperParameterizedType, typeVariable);
        //        if (foundType != null) {
        //            if (foundType instanceof TypeVariable) {
        //                return resolveItemVariableType(chain.getWrapper(), (TypeVariable<?>) foundType, warn);
        //            }
        //            return foundType;
        //        }
        //
        //        return resolveItemVariableType(chain.getWrapper(), typeVariable, warn);
        int index = resolutionPath.size() - 1;
        while (0 <= index) {
            Type rawClass = resolutionPath.get(index);
            Type temp = new VariableTypeInheritanceSearch().searchParametrizedType(rawClass, (TypeVariable<?>) result);
            if (null != temp) {
                result = temp;
            }
            if (!(result instanceof TypeVariable)) {
                break;
            }
            index -= 1;
        }
        if (result instanceof TypeVariable) {
            //            throw new JsonbException("Could not resolve: " + unresolvedType);
            return Object.class;
        }
        return result;
    }

    /**
     * Check if type needs resolution. If type is a class or a parametrized type with all type arguments as classes
     * than it is considered resolved. If any of types is type variable or wildcard type is not resolved.
     *
     * @param rawClass Type to check.
     * @return True if resolved
     */
    public static boolean isResolvedType(Type rawClass) {
        if (rawClass instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) rawClass).getActualTypeArguments()) {
                if (!isResolvedType(argument)) {
                    return false;
                }
            }
            return true;
        }
        return rawClass instanceof Class<?>;
    }

    private static Class<?> getMostSpecificBound(List<Type> resolutionPath, Class<?> resolvedClass, Type constraint, boolean shouldReport) {
        if (Object.class == constraint) {
            return resolvedClass;
        }
        //if bound is type variable search recursively for wrapper generic expansion
        Type resolvedConstraint = constraint instanceof TypeVariable ? determineType(resolutionPath, constraint, shouldReport) : constraint;
        Class<?> resolvedClassOpt = getRawType(resolvedConstraint);
        //resolved class is a subclass of a result candidate
        if (resolvedClass.isAssignableFrom(resolvedClassOpt)) {
            resolvedClass = resolvedClassOpt;
        }
        return resolvedClass;
    }

    /**
     * Get a raw type of any type.
     * If type is a {@link TypeVariable} recursively search type chain for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param resolutionPath hierarchy of all wrapping types.
     * @param rawClass  type to resolve, typically field type or generic bound, not null.
     * @return resolved raw class
     */
    public static Class<?> resolveRawType(List<Type> resolutionPath, Type rawClass) {
        if (!(rawClass instanceof Class)) {
            if (!(rawClass instanceof ParameterizedType)) {
                return getRawType(determineType(resolutionPath, rawClass));
            } else {
                return (Class<?>) ((ParameterizedType) rawClass).getRawType();
            }
        } else {
            return (Class<?>) rawClass;
        }
    }

    /**
     * Resolve a type by chain.
     * If type is a {@link TypeVariable} recursively search type chain for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param resolutionPath hierarchy of all wrapping types.
     * @param rawClass  type to resolve, typically field type or generic bound, not null.
     * @return resolved type
     */
    public static Type determineType(List<Type> resolutionPath, Type rawClass) {
        return determineType(resolutionPath, rawClass, true);
    }

    /**
     * Resolves a wildcard most specific upper or lower bound.
     *
     * @param resolutionPath         Type.
     * @param wildcard Wildcard type.
     * @return The most specific type.
     */
    private static Type determineMostSpecificBound(List<Type> resolutionPath, WildcardType wildcard, boolean shouldReport) {
        Class<?> resolvedClass = Object.class;
        for (Type upperLimit : wildcard.getUpperBounds()) {
            resolvedClass = getMostSpecificBound(resolutionPath, resolvedClass, upperLimit, shouldReport);
        }
        for (Type minConstraint : wildcard.getLowerBounds()) {
            resolvedClass = getMostSpecificBound(resolutionPath, resolvedClass, minConstraint, shouldReport);
        }
        return resolvedClass;
    }

    /**
     * Create instance with constructor.
     *
     * @param ctor const not null
     * @param <T>         type of instance
     * @return instance
     */
    public static <T> T instantiateNoArgConstructor(Constructor<T> ctor) {
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
     * @param klass    Class to get constructor from
     * @param <T>      Class generic type
     * @param mandatory if true, throws an exception if the default constructor is missing.
     *                 If false, returns null in that case
     * @return the constructor of the class, or null. Depending on required.
     */
    public static <T> Constructor<T> getDefaultConstructor(Class<T> klass, boolean mandatory) {
        Objects.requireNonNull(klass);
        return AccessController.doPrivileged((PrivilegedAction<Constructor<T>>) () -> {
            try {
                final Constructor<T> foundCtor = klass.getDeclaredConstructor();
                if (Modifier.PROTECTED == foundCtor.getModifiers()) {
                    foundCtor.setAccessible(true);
                }
                return foundCtor;
            } catch (NoSuchMethodException | RuntimeException ex) {
                if (mandatory) {
                    throw new JsonbException(MessageProvider.getMessage(MessageConstants.NO_DEFAULT_CONSTRUCTOR, klass), ex);
                }
                return null;
            }
        });
    }

    private static ParameterizedType locateParameterizedSuperclass(Type rawClass) {
        if (null == rawClass || rawClass instanceof ParameterizedType) {
            return (ParameterizedType) rawClass;
        }
        if (!(rawClass instanceof Class)) {
            throw new JsonbException("Can't resolve ParameterizedType superclass for: " + rawClass);
        }
        return locateParameterizedSuperclass(((Class) rawClass).getGenericSuperclass());
    }

    /**
     * Get raw type by type.
     * Resolves only ParametrizedTypes, GenericArrayTypes and Classes.
     *
     * Exception is thrown if raw type cannot be resolved.
     *
     * @param rawClass Type to get class information from, not null.
     * @return Class of a raw type.
     */
    public static Class<?> getRawType(Type rawClass) {
        return getOptionalRawType(rawClass).orElseThrow(() -> new JsonbException(MessageProvider.getMessage(MessageConstants.TYPE_RESOLUTION_ERROR, rawClass)));
    }

    /**
     * Get raw type by type.
     * Only for ParametrizedTypes, GenericArrayTypes and Classes.
     *
     * Empty optional is returned if raw type cannot be resolved.
     *
     * @param rawClass Type to get class information from, not null.
     * @return Class of a type.
     */
    public static Optional<Class<?>> getOptionalRawType(Type rawClass) {
        if (!(rawClass instanceof Class)) {
            if (!(rawClass instanceof ParameterizedType)) {
                if (!(rawClass instanceof GenericArrayType)) {
                    if (rawClass instanceof TypeVariable) {
                        TypeVariable<?> genericParameter = TypeVariable.class.cast(rawClass);
                        if (Objects.nonNull(genericParameter.getBounds())) {
                            Optional<Class<?>> concreteClass = Optional.empty();
                            for (Type constraint : genericParameter.getBounds()) {
                                Optional<Class<?>> resolvedClassOpt = getOptionalRawType(constraint);
                                if (resolvedClassOpt.isPresent() && !Object.class.equals(resolvedClassOpt.get())) {
                                    if (!concreteClass.isPresent() || concreteClass.get().isAssignableFrom(resolvedClassOpt.get())) {
                                        concreteClass = Optional.of(resolvedClassOpt.get());
                                    }
                                }
                            }
                            return concreteClass;
                        }
                    }
                } else {
                    return Optional.of(((GenericArrayType) rawClass).getClass());
                }
            } else {
                return Optional.of((Class<?>) ((ParameterizedType) rawClass).getRawType());
            }
        } else {
            return Optional.of((Class<?>) rawClass);
        }
        return Optional.empty();
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
     * @param targetInterfaceClass interface to search
     * @return type of JsonbAdapter
     */
    public static ParameterizedType locateParameterizedType(Class<?> searchClass, Class<?> targetInterfaceClass) {
        Class activeClass = searchClass;
        while (Object.class != activeClass) {
            for (Type examinedInterface : activeClass.getGenericInterfaces()) {
                if (examinedInterface instanceof ParameterizedType && targetInterfaceClass.isAssignableFrom(ReflectionHelper.getRawType(((ParameterizedType) examinedInterface).getRawType()))) {
                    return (ParameterizedType) examinedInterface;
                }
            }
            activeClass = activeClass.getSuperclass();
        }
        throw new JsonbException(MessageProvider.getMessage(MessageConstants.NON_PARAMETRIZED_TYPE, targetInterfaceClass));
    }

    private ReflectionHelper() {
        throw new IllegalStateException("Utility classes should not be instantiated.");
    }

    /**
     * Resolves {@link TypeVariable} arguments of generic types.
     *
     * @param parameterizedTarget type to resolve
     * @param searchTarget  type to search
     * @return resolved type
     */
    public static Type resolveGenericTypeArguments(ParameterizedType parameterizedTarget, Type searchTarget) {
        final Type[] pendingArgs = parameterizedTarget.getActualTypeArguments();
        Type[] finalArgs = new Type[pendingArgs.length];
        int index = 0;
        while (pendingArgs.length > index) {
            Type pendingArg = pendingArgs[index];
            if ((pendingArg instanceof TypeVariable) || (pendingArg instanceof GenericArrayType)) {
                Type varClass = pendingArg;
                if (varClass instanceof GenericArrayType) {
                    varClass = ((GenericArrayType) varClass).getGenericComponentType();
                }
                finalArgs[index] = new VariableTypeInheritanceSearch().searchParametrizedType(searchTarget, (TypeVariable<?>) varClass);
                if (null == finalArgs[index]) {
                    if (searchTarget instanceof Class) {
                        return Object.class;
                    }
                    //No generic information available
                    throw new IllegalStateException(MessageProvider.getMessage(MessageConstants.GENERIC_BOUND_NOT_FOUND, varClass, searchTarget));
                }
            } else {
                finalArgs[index] = pendingArg;
            }
            if (!(finalArgs[index] instanceof ParameterizedType)) {
                if (pendingArg instanceof GenericArrayType) {
                    finalArgs[index] = new DefaultGenericArrayType(finalArgs[index]);
                }
            } else {
                finalArgs[index] = resolveGenericTypeArguments((ParameterizedType) finalArgs[index], searchTarget);
            }
            index += 1;
        }
        return Arrays.equals(finalArgs, pendingArgs) ? parameterizedTarget : new ResolvedParameterizedType(parameterizedTarget, finalArgs);
    }

    private static Type determineType(List<Type> resolutionPath, Type rawClass, boolean shouldReport) {
        Type target = rawClass;
        if (rawClass instanceof GenericArrayType) {
            target = ((GenericArrayType) rawClass).getGenericComponentType();
            Type result = determineType(resolutionPath, target);
            return new DefaultGenericArrayType(result);
        }
        if (!(target instanceof WildcardType)) {
            if (!(target instanceof TypeVariable)) {
                if (target instanceof ParameterizedType) {
                    return resolveGenericTypeArguments((ParameterizedType) target, resolutionPath.get(resolutionPath.size() - 1));
                }
            } else {
                return resolveItemTypeVariable(resolutionPath, (TypeVariable<?>) target, shouldReport);
            }
        } else {
            return determineMostSpecificBound(resolutionPath, (WildcardType) target, shouldReport);
        }
        return rawClass;
    }

    /**
     * Resolves type by item information and wraps it with {@link Optional}.
     *
     * @param resolutionPath hierarchy of all wrapping types.
     * @param rawClass  type
     * @return resolved type wrapped with Optional
     */
    public static Optional<Type> resolveOptionalType(List<Type> resolutionPath, Type rawClass) {
        try {
            return Optional.of(determineType(resolutionPath, rawClass, false));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

}

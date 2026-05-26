/*
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeysEnum;

/**
 * Utility class for resolution of generics during unmarshalling.
 */
public class ReflectiveTypeResolver {

    private static final Logger LOGGER = Logger.getLogger(ReflectiveTypeResolver.class.getName());

    public static final class GenericArrayTypeImplementation implements GenericArrayType {
        private final Type componentType;

        @Override
        public boolean equals(Object other) {
            if (other instanceof GenericArrayType) {
                GenericArrayType otherArrayType = (GenericArrayType) other;

                return Objects.equals(componentType, otherArrayType.getGenericComponentType());
            } else {
                return false;
            }
        }

        public String toString() {
            return getGenericComponentType().getTypeName() + "[]";
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(componentType);
        }

        // private constructor enforces use of static factory
        private GenericArrayTypeImplementation(Type componentType) {
            this.componentType = componentType;
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
            return componentType; // return cached component type
        }

    }

    /**
     * Create instance with constructor.
     *
     * @param ctor const not null
     * @param <T>         type of instance
     * @return instance
     */
    public static <T> T createInstanceNoArgConstructor(Constructor<T> ctor) {
        Objects.requireNonNull(ctor);
        try {
            return ctor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException runtimeEx) {
            throw new JsonbException("Can't create instance", runtimeEx);
        }
    }

    /**
     * Get default no argument constructor of the class.
     *
     * @param targetClass    Class to get constructor from
     * @param <T>      Class generic type
     * @param mandatory if true, throws an exception if the default constructor is missing.
     *                 If false, returns null in that case
     * @return the constructor of the class, or null. Depending on required.
     */
    public static <T> Constructor<T> getDefaultConstructor(Class<T> targetClass, boolean mandatory) {
        Objects.requireNonNull(targetClass);
        return AccessController.doPrivileged((PrivilegedAction<Constructor<T>>) () -> {
            try {
                final Constructor<T> foundCtor = targetClass.getDeclaredConstructor();
                if (foundCtor.getModifiers() == Modifier.PROTECTED) {
                    foundCtor.setAccessible(true);
                }
                return foundCtor;
            } catch (NoSuchMethodException | RuntimeException runtimeEx) {
                if (mandatory) {
                    throw new JsonbException(MessageBundle.getMessage(MessageKeysEnum.NO_DEFAULT_CONSTRUCTOR, targetClass), runtimeEx);
                }
                return null;
            }
        });
    }

    private static ParameterizedType locateParameterizedSuperclass(Type candidate) {
        if (candidate == null || candidate instanceof ParameterizedType) {
            return (ParameterizedType) candidate;
        }
        if (!(candidate instanceof Class)) {
            throw new JsonbException("Can't resolve ParameterizedType superclass for: " + candidate);
        }
        return locateParameterizedSuperclass(((Class) candidate).getGenericSuperclass());
    }

    /**
     * Resolves a wildcard most specific upper or lower bound.
     *
     * @param resolutionPath         Type.
     * @param wildcardBound Wildcard type.
     * @return The most specific type.
     */
    private static Type getMostSpecificBound(List<Type> resolutionPath, WildcardType wildcardBound, boolean shouldWarn) {
        Class<?> resolvedClass = Object.class;
        for (Type upperLimit : wildcardBound.getUpperBounds()) {
            resolvedClass = getMostSpecificBound(resolutionPath, resolvedClass, upperLimit, shouldWarn);
        }
        for (Type lowerLimit : wildcardBound.getLowerBounds()) {
            resolvedClass = getMostSpecificBound(resolutionPath, resolvedClass, lowerLimit, shouldWarn);
        }
        return resolvedClass;
    }

    private static Class<?> getMostSpecificBound(List<Type> resolutionPath, Class<?> resolvedClass, Type constraint, boolean shouldWarn) {
        if (constraint == Object.class) {
            return resolvedClass;
        }
        //if bound is type variable search recursively for wrapper generic expansion
        Type resolvedConstraint = constraint instanceof TypeVariable ? inferType(resolutionPath, constraint, shouldWarn) : constraint;
        Class<?> constraintClass = getRawType(resolvedConstraint);
        //resolved class is a subclass of a result candidate
        if (resolvedClass.isAssignableFrom(constraintClass)) {
            resolvedClass = constraintClass;
        }
        return resolvedClass;
    }

    /**
     * Check if type needs resolution. If type is a class or a parametrized type with all type arguments as classes
     * than it is considered resolved. If any of types is type variable or wildcard type is not resolved.
     *
     * @param candidate Type to check.
     * @return True if resolved
     */
    public static boolean isResolvedType(Type candidate) {
        if (candidate instanceof ParameterizedType) {
            for (Type argument : ((ParameterizedType) candidate).getActualTypeArguments()) {
                if (!isResolvedType(argument)) {
                    return false;
                }
            }
            return true;
        }
        return candidate instanceof Class<?>;
    }

    /**
     * Resolves type by item information and wraps it with {@link Optional}.
     *
     * @param resolutionPath hierarchy of all wrapping types.
     * @param candidate  type
     * @return resolved type wrapped with Optional
     */
    public static Optional<Type> resolveOptionalType(List<Type> resolutionPath, Type candidate) {
        try {
            return Optional.of(inferType(resolutionPath, candidate, false));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Get raw type by type.
     * Resolves only ParametrizedTypes, GenericArrayTypes and Classes.
     *
     * Exception is thrown if raw type cannot be resolved.
     *
     * @param candidate Type to get class information from, not null.
     * @return Class of a raw type.
     */
    public static Class<?> getRawType(Type candidate) {
        return getOptionalRawType(candidate)
                .orElseThrow(() -> new JsonbException(MessageBundle.getMessage(MessageKeysEnum.TYPE_RESOLUTION_ERROR, candidate)));
    }

    /**
     * Resolve a type by chain.
     * If type is a {@link TypeVariable} recursively search type chain for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param resolutionPath hierarchy of all wrapping types.
     * @param candidate  type to resolve, typically field type or generic bound, not null.
     * @return resolved type
     */
    public static Type inferType(List<Type> resolutionPath, Type candidate) {
        return inferType(resolutionPath, candidate, true);
    }

    private static Type inferType(List<Type> resolutionPath, Type candidate, boolean shouldWarn) {
        Type pending = candidate;
        if (candidate instanceof GenericArrayType) {
            pending = ((GenericArrayType) candidate).getGenericComponentType();
            Type finalResult = inferType(resolutionPath, pending);
            return new GenericArrayTypeImplementation(finalResult);
        }
        if (pending instanceof WildcardType) {
            return getMostSpecificBound(resolutionPath, (WildcardType) pending, shouldWarn);
        } else if (pending instanceof TypeVariable) {
            return resolveItemTypeVariable(resolutionPath, (TypeVariable<?>) pending, shouldWarn);
        } else if (pending instanceof ParameterizedType) {
            return resolveGenericArguments((ParameterizedType) pending, resolutionPath.get(resolutionPath.size() - 1));
        }
        return candidate;
    }

    /**
     * Get a raw type of any type.
     * If type is a {@link TypeVariable} recursively search type chain for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param resolutionPath hierarchy of all wrapping types.
     * @param candidate  type to resolve, typically field type or generic bound, not null.
     * @return resolved raw class
     */
    public static Class<?> resolveRawType(List<Type> resolutionPath, Type candidate) {
        if (candidate instanceof Class) {
            return (Class<?>) candidate;
        } else if (candidate instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) candidate).getRawType();
        } else {
            return getRawType(inferType(resolutionPath, candidate));
        }
    }

    private ReflectiveTypeResolver() {
        throw new IllegalStateException("Utility classes should not be instantiated.");
    }

    /**
     * Resolve a bounded type variable type by its wrapper types.
     * Resolution could be done only if a compile time generic information is provided, either:
     * by generic field or subclass of a generic class.
     *
     * @param resolutionPath        chain to search "runtime" generic type of a TypeVariable.
     * @param genericParam type to search in chain for, not null.
     * @param shouldWarn         whether or not to log a warning message when bounds are not found
     * @return Type of a generic "runtime" bound, not null.
     */
    public static Type resolveItemTypeVariable(List<Type> resolutionPath, TypeVariable<?> genericParam, boolean shouldWarn) {
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
        Type result = genericParam;
        for (int index = resolutionPath.size() - 1; index >= 0; index--) {
            Type candidate = resolutionPath.get(index);
            Type temp = new ParameterizedTypeInheritanceSearch().findParametrizedType(candidate, (TypeVariable<?>) result);
            if (temp != null) {
                result = temp;
            }
            // If the type is a WildcardType we need to resolve the most specific type
            if (result instanceof WildcardType) {
                return getMostSpecificBound(resolutionPath, (WildcardType) result, shouldWarn);
            }
            if (!(result instanceof TypeVariable)) {
                break;
            }
        }
        if (result instanceof TypeVariable) {
            //            throw new JsonbException("Could not resolve: " + unresolvedType);
            return Object.class;
        }
        return result;

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
    }

    /**
     * Resolves {@link TypeVariable} arguments of generic types.
     *
     * @param paramToResolve type to resolve
     * @param searchTarget  type to search
     * @return resolved type
     */
    public static Type resolveGenericArguments(ParameterizedType paramToResolve, Type searchTarget) {
        final Type[] pendingArgs = paramToResolve.getActualTypeArguments();
        Type[] resultArgs = new Type[pendingArgs.length];
        for (int index = 0; index < pendingArgs.length; index++) {
            Type pendingArg = pendingArgs[index];
            if (!(pendingArg instanceof TypeVariable) && !(pendingArg instanceof GenericArrayType)) {
                resultArgs[index] = pendingArg;
            } else {
                Type varConstraint = pendingArg;
                if (varConstraint instanceof GenericArrayType) {
                    varConstraint = ((GenericArrayType) varConstraint).getGenericComponentType();
                }
                resultArgs[index] = new ParameterizedTypeInheritanceSearch()
                        .findParametrizedType(searchTarget, (TypeVariable<?>) varConstraint);

                if (resultArgs[index] == null) {
                    Type[] constraints = ((TypeVariable<?>) varConstraint).getBounds();
                    if (Objects.nonNull(constraints) && constraints.length > 0) {
                        resultArgs[index] = constraints[0];
                    }
                }

                if (resultArgs[index] == null) {
                    if (searchTarget instanceof Class) {
                        return Object.class;
                    }
                    //No generic information available
                    throw new IllegalStateException(MessageBundle.getMessage(MessageKeysEnum.GENERIC_BOUND_NOT_FOUND,
                            varConstraint,
                            searchTarget));
                }
            }
            if (resultArgs[index] instanceof ParameterizedType) {
                resultArgs[index] = resolveGenericArguments((ParameterizedType) resultArgs[index], searchTarget);
            } else if (pendingArg instanceof GenericArrayType) {
                resultArgs[index] = new GenericArrayTypeImplementation(resultArgs[index]);
            }
        }
        return Arrays.equals(resultArgs, pendingArgs)
                ? paramToResolve
                : new ResolvedParameterizedTypeDescriptor(paramToResolve, resultArgs);
    }

    /**
     * Get raw type by type.
     * Only for ParametrizedTypes, GenericArrayTypes and Classes.
     *
     * Empty optional is returned if raw type cannot be resolved.
     *
     * @param candidate Type to get class information from, not null.
     * @return Class of a type.
     */
    public static Optional<Class<?>> getOptionalRawType(Type candidate) {
        if (candidate instanceof Class) {
            return Optional.of((Class<?>) candidate);
        } else if (candidate instanceof ParameterizedType) {
            return Optional.of((Class<?>) ((ParameterizedType) candidate).getRawType());
        } else if (candidate instanceof GenericArrayType) {
            return Optional.of(((GenericArrayType) candidate).getClass());
        } else if (candidate instanceof TypeVariable) {
            TypeVariable<?> genericParam = TypeVariable.class.cast(candidate);
            if (Objects.nonNull(genericParam.getBounds())) {
                Optional<Class<?>> concreteClass = Optional.empty();
                for (Type constraint : genericParam.getBounds()) {
                    Optional<Class<?>> constraintClass = getOptionalRawType(constraint);
                    if (constraintClass.isPresent() && !Object.class.equals(constraintClass.get())) {
                        if (!concreteClass.isPresent() || concreteClass.get().isAssignableFrom(constraintClass.get())) {
                            concreteClass = Optional.of(constraintClass.get());
                        }
                    }
                }
                return concreteClass;
            }
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
     * @param targetClass          class to resolve parameterized interface
     * @param genericInterface interface to search
     * @return type of JsonbAdapter
     */
    public static ParameterizedType locateParameterizedType(Class<?> targetClass, Class<?> genericInterface) {
        Class activeClass = targetClass;
        while (activeClass != Object.class) {
            for (Type inspectedInterface : activeClass.getGenericInterfaces()) {
                if (inspectedInterface instanceof ParameterizedType
                        && genericInterface.isAssignableFrom(
                        ReflectiveTypeResolver.getRawType(((ParameterizedType) inspectedInterface).getRawType()))) {
                    return (ParameterizedType) inspectedInterface;
                }
            }
            activeClass = activeClass.getSuperclass();
        }
        throw new JsonbException(MessageBundle.getMessage(MessageKeysEnum.NON_PARAMETRIZED_TYPE, genericInterface));
    }

}

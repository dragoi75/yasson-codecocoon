/*******************************************************************************
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.serializer.AbstractItem;
import org.eclipse.yasson.internal.serializer.EmbeddedItem;
import org.eclipse.yasson.internal.serializer.ResolvedParameterizedType;

import javax.json.bind.JsonbException;
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

/**
 * Utility class for resolution of generics during unmarshalling.
 *
 * @author Roman Grigoriadi
 */
public class ReflectionHelper {

    private static final Logger REFLECTION_HELPER_LOG = Logger.getLogger(ReflectionHelper.class.getName());

    /**
     * Get raw type by type.
     * Only for ParametrizedTypes, GenericArrayTypes and Classes.
     *
     * Empty optional is returned if raw type cannot be resolved.
     *
     * @param targetClass Type to get class information from, not null.
     * @return Class of a type.
     */
    public static Optional<Class<?>> getOptionalRawType(Type targetClass) {
        if (targetClass instanceof Class) {
            return Optional.of((Class<?>) targetClass);
        } else if (targetClass instanceof ParameterizedType) {
            return Optional.of((Class<?>) ((ParameterizedType) targetClass).getRawType());
        } else if (targetClass instanceof GenericArrayType) {
            return Optional.of(((GenericArrayType) targetClass).getClass());
        }
        return Optional.empty();
        }

    /**
     * Get raw type by type.
     * Resolves only ParametrizedTypes, GenericArrayTypes and Classes.
     *
     * Exception is thrown if raw type cannot be resolved.
     *
     * @param targetClass Type to get class information from, not null.
     * @return Class of a raw type.
     */
    public static Class<?> getRawType(Type targetClass) {
        return getOptionalRawType(targetClass)
                .orElseThrow(()->new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.TYPE_RESOLUTION_ERROR, targetClass)));
    }

    /**
     * Get a raw type of any type.
     * If type is a {@link TypeVariable} recursively search {@link AbstractItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param entryInfo item containing wrapper class of a type field, not null.
     * @param targetClass type to resolve, typically field type or generic bound, not null.
     * @return resolved raw class
     */
    public static Class<?> getRawType(RuntimeTypeInfo entryInfo, Type targetClass) {
        if (targetClass instanceof Class) {
            return (Class<?>) targetClass;
        } else if (targetClass instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) targetClass).getRawType();
        } else {
            return getRawType(resolveGenericType(entryInfo, targetClass));
        }
    }

    /**
     * Resolve a type by item.
     * If type is a {@link TypeVariable} recursively search {@link AbstractItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param entryInfo item containing wrapper class of a type field, not null.
     * @param targetClass type to resolve, typically field type or generic bound, not null.
     * @return resolved type
     */
    public static Type resolveGenericType(RuntimeTypeInfo entryInfo, Type targetClass) {
        if (targetClass instanceof WildcardType) {
            return getMostSpecificBound(entryInfo, (WildcardType) targetClass);
        } else if (targetClass instanceof TypeVariable) {
            return resolveVariableTypeForItem(entryInfo, (TypeVariable<?>) targetClass);
        } else if (targetClass instanceof ParameterizedType && entryInfo != null) {
            return resolveGenericArguments((ParameterizedType) targetClass, entryInfo.getRuntimeType());
        }
        return targetClass;
    }


    public static Optional<Type> resolveTypeOptional(RuntimeTypeInfo runtimeData, Type targetClass) {
        try {
            return Optional.of(resolveGenericType(runtimeData, targetClass));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolve a bounded type variable type by its wrapper types.
     * Resolution could be done only if a compile time generic information is provided, either:
     * by generic field or subclass of a generic class.
     *
     * @param entryInfo item to search "runtime" generic type of a TypeVariable.
     * @param genericVar type to search in item for, not null.
     * @return Type of a generic "runtime" bound, not null.
     */
    public static Type resolveVariableTypeForItem(RuntimeTypeInfo entryInfo, TypeVariable<?> genericVar) {
        if (entryInfo == null) {
            //Bound not found, treat it as an Object.class
            REFLECTION_HELPER_LOG.warning(LocalizedMessages.getMessage(MessageKeyConstants.GENERIC_BOUND_NOT_FOUND, genericVar, genericVar.getGenericDeclaration()));
            return Object.class;
        }

        //Embedded items doesn't hold information about variable types
        if (entryInfo instanceof EmbeddedItem) {
            return resolveVariableTypeForItem(entryInfo.getWrapper(), genericVar);
        }

        ParameterizedType outerParameterized = findParameterizedSupertype(entryInfo.getRuntimeType());

        VariableTypeInheritanceSearch inheritanceFinder = new VariableTypeInheritanceSearch();
        Type locatedClass = inheritanceFinder.searchParametrizedType(outerParameterized, genericVar);
        if (locatedClass != null) {
            if (locatedClass instanceof TypeVariable) {
                return resolveVariableTypeForItem(entryInfo.getWrapper(), (TypeVariable<?>) locatedClass);
            }
            return locatedClass;
        }

        return resolveVariableTypeForItem(entryInfo.getWrapper(), genericVar);
    }

    /**
     * Resolves {@link TypeVariable} arguments of generic types.
     *
     * @param paramToResolve type to resolve
     * @param searchTarget type to search
     * @return resolved type
     */
    public static Type resolveGenericArguments(ParameterizedType paramToResolve, Type searchTarget) {
        final Type[] pendingParameters = paramToResolve.getActualTypeArguments();
        Type[] finalArguments = new Type[pendingParameters.length];
        for (int index = 0; index < pendingParameters.length; index++) {
            if (!(pendingParameters[index] instanceof TypeVariable)) {
                finalArguments[index] = pendingParameters[index];
            } else {
                finalArguments[index] = new VariableTypeInheritanceSearch().searchParametrizedType(searchTarget, (TypeVariable<?>) pendingParameters[index]);
                if (finalArguments[index] == null) {
                    //No generic information available
                    throw new IllegalStateException(LocalizedMessages.getMessage(MessageKeyConstants.GENERIC_BOUND_NOT_FOUND, pendingParameters[index], searchTarget));
                }
            }
            if (finalArguments[index] instanceof ParameterizedType) {
                finalArguments[index] = resolveGenericArguments((ParameterizedType) finalArguments[index], searchTarget);
            }
        }
        return Arrays.equals(finalArguments, pendingParameters) ?
                paramToResolve : new ResolvedParameterizedType(paramToResolve, finalArguments);
    }

    /**
     * Search for no argument constructor of a class and create instance.
     *
     * @param targetClass not null
     * @param <T> type of instance
     * @return instance
     */
    public static <T> T createInstanceNoArgs(Class<T> targetClass) {
        Objects.requireNonNull(targetClass);
        return AccessController.doPrivileged((PrivilegedAction<T>) () -> {
            try {
                final Constructor<T> ctor = targetClass.getDeclaredConstructor();
                if (ctor.getModifiers() == Modifier.PROTECTED) {
                    ctor.setAccessible(true);
                }
                return ctor.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                throw new JsonbException("Can't create instance", ex);
            } catch (NoSuchMethodException ex) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.NO_DEFAULT_CONSTRUCTOR, targetClass), ex);
            }
        });

    }

    /**
     * For generic adapters like:
     * <p>
     *     {@code
     *     interface ContainerAdapter<T> extends JsonbAdapter<Box<T>, Crate<T>>...;
     *     class IntegerBoxToCrateAdapter implements ContainerAdapter<Integer>...;
     *     }
     * </p>
     * We need to find a JsonbAdapter class which will hold basic generic type arguments,
     * and resolve them if they are TypeVariables from there.
     *
     * @param targetClass class to resolve parameterized interface
     * @param genericInterfaceClass interface to search
     *
     * @return type of JsonbAdapter
     */
    public static ParameterizedType findParameterizedInterfaceType(Class<?> targetClass, Class<?> genericInterfaceClass) {
        Class traversalClass = targetClass;
        while (traversalClass != Object.class) {
            for (Type candidateInterface : traversalClass.getGenericInterfaces()) {
                if (candidateInterface instanceof ParameterizedType &&
                        genericInterfaceClass.isAssignableFrom(
                                ReflectionHelper.getRawType(((ParameterizedType) candidateInterface).getRawType()))) {
                    return (ParameterizedType) candidateInterface;
                }
            }
            traversalClass = traversalClass.getSuperclass();
        }
        throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.NON_PARAMETRIZED_TYPE, genericInterfaceClass));
    }

    /**
     * Check if type needs resolution. If type is a class or a parametrized type with all type arguments as classes
     * than it is considered resolved. If any of types is type variable or wildcard type is not resolved.
     *
     * @param targetClass Type to check.
     * @return True if resolved
     */
    public static boolean isResolvedType(Type targetClass) {
        if (targetClass instanceof ParameterizedType) {
            for(Type argument : ((ParameterizedType) targetClass).getActualTypeArguments()) {
                if (!isResolvedType(argument)) {
                    return false;
                }
            }
            return true;
        }
        return targetClass instanceof Class<?>;
    }

    private static ParameterizedType findParameterizedSupertype(Type targetClass) {
        if (targetClass == null || targetClass instanceof ParameterizedType) {
            return (ParameterizedType) targetClass;
        }
        if (!(targetClass instanceof Class)) {
            throw new JsonbException("Can't resolve ParameterizedType superclass for: " + targetClass);
        }
        return findParameterizedSupertype(((Class) targetClass).getGenericSuperclass());
    }

    /**
     * Resolves a wildcard most specific upper or lower bound.
     *
     * @param entryInfo Type.
     * @param wildcardDescriptor Wildcard type.
     * @return The most specific type.
     */
    private static Type getMostSpecificBound(RuntimeTypeInfo entryInfo, WildcardType wildcardDescriptor) {
        Class<?> bestMatch = Object.class;
        for (Type upperLimit : wildcardDescriptor.getUpperBounds()) {
            bestMatch = getMostSpecificBound(entryInfo, bestMatch, upperLimit);
        }
        for (Type lowerLimit : wildcardDescriptor.getLowerBounds()) {
            bestMatch = getMostSpecificBound(entryInfo, bestMatch, lowerLimit);
        }
        return bestMatch;
    }

    private static Class<?> getMostSpecificBound(RuntimeTypeInfo entryInfo, Class<?> bestMatch, Type constraint) {
        if (constraint == Object.class) {
            return bestMatch;
        }
        //if bound is type variable search recursively for wrapper generic expansion
        Type resolvedBound = constraint instanceof TypeVariable ? resolveGenericType(entryInfo, constraint) : constraint;
        Class<?> boundType = getRawType(resolvedBound);
        //resolved class is a subclass of a result candidate
        if (bestMatch.isAssignableFrom(boundType)) {
            bestMatch = boundType;
        }
        return bestMatch;
    }
}

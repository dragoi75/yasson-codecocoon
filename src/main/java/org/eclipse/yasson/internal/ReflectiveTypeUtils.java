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

import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.ResourceBundleMessages;
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
public class ReflectiveTypeUtils {

    private static final Logger LOG = Logger.getLogger(ReflectiveTypeUtils.class.getName());

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
        }
        return Optional.empty();
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
                .orElseThrow(()->new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.TYPE_RESOLUTION_ERROR, candidate)));
    }

    /**
     * Get a raw type of any type.
     * If type is a {@link TypeVariable} recursively search {@link AbstractItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param elementInfo item containing wrapper class of a type field, not null.
     * @param candidate type to resolve, typically field type or generic bound, not null.
     * @return resolved raw class
     */
    public static Class<?> getRawType(RuntimeTypeInfo elementInfo, Type candidate) {
        if (candidate instanceof Class) {
            return (Class<?>) candidate;
        } else if (candidate instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) candidate).getRawType();
        } else {
            return getRawType(resolveGenericType(elementInfo, candidate));
        }
    }

    /**
     * Resolve a type by item.
     * If type is a {@link TypeVariable} recursively search {@link AbstractItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param elementInfo item containing wrapper class of a type field, not null.
     * @param candidate type to resolve, typically field type or generic bound, not null.
     * @return resolved type
     */
    public static Type resolveGenericType(RuntimeTypeInfo elementInfo, Type candidate) {
        if (candidate instanceof WildcardType) {
            return determineMostSpecificBound(elementInfo, (WildcardType) candidate);
        } else if (candidate instanceof TypeVariable) {
            return resolveItemTypeVariable(elementInfo, (TypeVariable<?>) candidate);
        } else if (candidate instanceof ParameterizedType && elementInfo != null) {
            return resolveTypeParameters((ParameterizedType) candidate, elementInfo.getRuntimeType());
        }
        return candidate;
    }


    public static Optional<Type> resolveTypeOptional(RuntimeTypeInfo runtimeInfo, Type candidate) {
        try {
            return Optional.of(resolveGenericType(runtimeInfo, candidate));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolve a bounded type variable type by its wrapper types.
     * Resolution could be done only if a compile time generic information is provided, either:
     * by generic field or subclass of a generic class.
     *
     * @param elementInfo item to search "runtime" generic type of a TypeVariable.
     * @param varDecl type to search in item for, not null.
     * @return Type of a generic "runtime" bound, not null.
     */
    public static Type resolveItemTypeVariable(RuntimeTypeInfo elementInfo, TypeVariable<?> varDecl) {
        if (elementInfo == null) {
            //Bound not found, treat it as an Object.class
            LOG.warning(ResourceBundleMessages.getMessage(MessageConstants.GENERIC_BOUND_NOT_FOUND, varDecl, varDecl.getGenericDeclaration()));
            return Object.class;
        }

        //Embedded items doesn't hold information about variable types
        if (elementInfo instanceof EmbeddedItem) {
            return resolveItemTypeVariable(elementInfo.getWrapper(), varDecl);
        }

        ParameterizedType wrapperParam = resolveParameterizedSuperclass(elementInfo.getRuntimeType());

        VariableTypeInheritanceSearch finder = new VariableTypeInheritanceSearch();
        Type resolvedType = finder.searchParametrizedType(wrapperParam, varDecl);
        if (resolvedType != null) {
            if (resolvedType instanceof TypeVariable) {
                return resolveItemTypeVariable(elementInfo.getWrapper(), (TypeVariable<?>) resolvedType);
            }
            return resolvedType;
        }

        return resolveItemTypeVariable(elementInfo.getWrapper(), varDecl);
    }

    /**
     * Resolves {@link TypeVariable} arguments of generic types.
     *
     * @param paramToResolve type to resolve
     * @param searchTarget type to search
     * @return resolved type
     */
    public static Type resolveTypeParameters(ParameterizedType paramToResolve, Type searchTarget) {
        final Type[] pendingArgs = paramToResolve.getActualTypeArguments();
        Type[] resolvedParams = new Type[pendingArgs.length];
        for (int index = 0; index < pendingArgs.length; index++) {
            if (!(pendingArgs[index] instanceof TypeVariable)) {
                resolvedParams[index] = pendingArgs[index];
            } else {
                resolvedParams[index] = new VariableTypeInheritanceSearch().searchParametrizedType(searchTarget, (TypeVariable<?>) pendingArgs[index]);
                if (resolvedParams[index] == null) {
                    //No generic information available
                    throw new IllegalStateException(ResourceBundleMessages.getMessage(MessageConstants.GENERIC_BOUND_NOT_FOUND, pendingArgs[index], searchTarget));
                }
            }
            if (resolvedParams[index] instanceof ParameterizedType) {
                resolvedParams[index] = resolveTypeParameters((ParameterizedType) resolvedParams[index], searchTarget);
            }
        }
        return Arrays.equals(resolvedParams, pendingArgs) ?
                paramToResolve : new ResolvedParameterizedType(paramToResolve, resolvedParams);
    }

    /**
     * Create instance with constructor.
     *
     * @param ctor const not null
     * @param <T> type of instance
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
     * @param targetClass Class to get constructor from
     * @param <T> Class generic type
     * @return constructor
     */
    public static <T> Constructor<T> getDefaultConstructor(Class<T> targetClass, boolean isRequired) {
        Objects.requireNonNull(targetClass);
        return AccessController.doPrivileged((PrivilegedAction<Constructor<T>>) () -> {
            try {
                final Constructor<T> declaredCtor = targetClass.getDeclaredConstructor();
                if (declaredCtor.getModifiers() == Modifier.PROTECTED) {
                    declaredCtor.setAccessible(true);
                }
                return declaredCtor;
            } catch (NoSuchMethodException ex) {
                if (isRequired) {
                    throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.NO_DEFAULT_CONSTRUCTOR, targetClass), ex);
                }
                return null;
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
     * @param interfaceClass interface to search
     *
     * @return type of JsonbAdapter
     */
    public static ParameterizedType findParameterizedInterface(Class<?> targetClass, Class<?> interfaceClass) {
        Class currentClass = targetClass;
        while (currentClass != Object.class) {
            for (Type currentIface : currentClass.getGenericInterfaces()) {
                if (currentIface instanceof ParameterizedType &&
                        interfaceClass.isAssignableFrom(
                                ReflectiveTypeUtils.getRawType(((ParameterizedType) currentIface).getRawType()))) {
                    return (ParameterizedType) currentIface;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        throw new JsonbException(ResourceBundleMessages.getMessage(MessageConstants.NON_PARAMETRIZED_TYPE, interfaceClass));
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
            for(Type argument : ((ParameterizedType) candidate).getActualTypeArguments()) {
                if (!isResolvedType(argument)) {
                    return false;
                }
            }
            return true;
        }
        return candidate instanceof Class<?>;
    }

    private static ParameterizedType resolveParameterizedSuperclass(Type candidate) {
        if (candidate == null || candidate instanceof ParameterizedType) {
            return (ParameterizedType) candidate;
        }
        if (!(candidate instanceof Class)) {
            throw new JsonbException("Can't resolve ParameterizedType superclass for: " + candidate);
        }
        return resolveParameterizedSuperclass(((Class) candidate).getGenericSuperclass());
    }

    /**
     * Resolves a wildcard most specific upper or lower bound.
     *
     * @param elementInfo Type.
     * @param wildcard Wildcard type.
     * @return The most specific type.
     */
    private static Type determineMostSpecificBound(RuntimeTypeInfo elementInfo, WildcardType wildcard) {
        Class<?> bestMatch = Object.class;
        for (Type upper : wildcard.getUpperBounds()) {
            bestMatch = getMostSpecificBound(elementInfo, bestMatch, upper);
        }
        for (Type lower : wildcard.getLowerBounds()) {
            bestMatch = getMostSpecificBound(elementInfo, bestMatch, lower);
        }
        return bestMatch;
    }

    private static Class<?> getMostSpecificBound(RuntimeTypeInfo elementInfo, Class<?> bestMatch, Type typeConstraint) {
        if (typeConstraint == Object.class) {
            return bestMatch;
        }
        //if bound is type variable search recursively for wrapper generic expansion
        Type resolvedType = typeConstraint instanceof TypeVariable ? resolveGenericType(elementInfo, typeConstraint) : typeConstraint;
        Class<?> rawConstraintClass = getRawType(resolvedType);
        //resolved class is a subclass of a result candidate
        if (bestMatch.isAssignableFrom(rawConstraintClass)) {
            bestMatch = rawConstraintClass;
        }
        return bestMatch;
    }
}

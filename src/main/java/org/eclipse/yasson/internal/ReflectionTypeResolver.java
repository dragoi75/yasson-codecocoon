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

import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageConstants;
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
public class ReflectionTypeResolver {

    private static final Logger LOG = Logger.getLogger(ReflectionTypeResolver.class.getName());

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
                .orElseThrow(()->new JsonbException(LocalizedMessages.getMessage(MessageConstants.TYPE_RESOLUTION_ERROR, subject)));
    }

    /**
     * Get a raw type of any type.
     * If type is a {@link TypeVariable} recursively search {@link AbstractItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param runtimeInfo item containing wrapper class of a type field, not null.
     * @param subject type to resolve, typically field type or generic bound, not null.
     * @return resolved raw class
     */
    public static Class<?> determineRawType(RuntimeTypeInfo runtimeInfo, Type subject) {
        if (subject instanceof Class) {
            return (Class<?>) subject;
        } else if (subject instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) subject).getRawType();
        } else {
            return getRawType(resolveActualType(runtimeInfo, subject));
        }
    }

    /**
     * Resolve a type by item.
     * If type is a {@link TypeVariable} recursively search {@link AbstractItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param runtimeInfo item containing wrapper class of a type field, not null.
     * @param subject type to resolve, typically field type or generic bound, not null.
     * @return resolved type
     */
    public static Type resolveActualType(RuntimeTypeInfo runtimeInfo, Type subject) {
        if (subject instanceof WildcardType) {
            return determineMostSpecificBound(runtimeInfo, (WildcardType) subject);
        } else if (subject instanceof TypeVariable) {
            return resolveVariableTypeForItem(runtimeInfo, (TypeVariable<?>) subject);
        } else if (subject instanceof ParameterizedType && runtimeInfo != null) {
            return resolveParameterizedArguments((ParameterizedType) subject, runtimeInfo.getRuntimeType());
        }
        return subject;
    }


    public static Optional<Type> resolveTypeOptional(RuntimeTypeInfo runtimeType, Type subject) {
        try {
            return Optional.of(resolveActualType(runtimeType, subject));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolve a bounded type variable type by its wrapper types.
     * Resolution could be done only if a compile time generic information is provided, either:
     * by generic field or subclass of a generic class.
     *
     * @param runtimeInfo item to search "runtime" generic type of a TypeVariable.
     * @param varParam type to search in item for, not null.
     * @return Type of a generic "runtime" bound, not null.
     */
    public static Type resolveVariableTypeForItem(RuntimeTypeInfo runtimeInfo, TypeVariable<?> varParam) {
        if (runtimeInfo == null) {
            //Bound not found, treat it as an Object.class
            LOG.warning(LocalizedMessages.getMessage(MessageConstants.GENERIC_BOUND_NOT_FOUND, varParam, varParam.getGenericDeclaration()));
            return Object.class;
        }

        //Embedded items doesn't hold information about variable types
        if (runtimeInfo instanceof EmbeddedItem) {
            return resolveVariableTypeForItem(runtimeInfo.getWrapper(), varParam);
        }

        ParameterizedType wrapperParam = locateParameterizedSuperclass(runtimeInfo.getRuntimeType());

        VariableTypeInheritanceSearch finder = new VariableTypeInheritanceSearch();
        Type located = finder.searchParametrizedType(wrapperParam, varParam);
        if (located != null) {
            if (located instanceof TypeVariable) {
                return resolveVariableTypeForItem(runtimeInfo.getWrapper(), (TypeVariable<?>) located);
            }
            return located;
        }

        return resolveVariableTypeForItem(runtimeInfo.getWrapper(), varParam);
    }

    /**
     * Resolves {@link TypeVariable} arguments of generic types.
     *
     * @param paramToResolve type to resolve
     * @param searchTarget type to search
     * @return resolved type
     */
    public static Type resolveParameterizedArguments(ParameterizedType paramToResolve, Type searchTarget) {
        final Type[] pendingArgs = paramToResolve.getActualTypeArguments();
        Type[] finalArgs = new Type[pendingArgs.length];
        for (int index = 0; index < pendingArgs.length; index++) {
            if (!(pendingArgs[index] instanceof TypeVariable)) {
                finalArgs[index] = pendingArgs[index];
            } else {
                finalArgs[index] = new VariableTypeInheritanceSearch().searchParametrizedType(searchTarget, (TypeVariable<?>) pendingArgs[index]);
                if (finalArgs[index] == null) {
                    //No generic information available
                    throw new IllegalStateException(LocalizedMessages.getMessage(MessageConstants.GENERIC_BOUND_NOT_FOUND, pendingArgs[index], searchTarget));
                }
            }
            if (finalArgs[index] instanceof ParameterizedType) {
                finalArgs[index] = resolveParameterizedArguments((ParameterizedType) finalArgs[index], searchTarget);
            }
        }
        return Arrays.equals(finalArgs, pendingArgs) ?
                paramToResolve : new ResolvedParameterizedType(paramToResolve, finalArgs);
    }

    /**
     * Search for no argument constructor of a class and create instance.
     *
     * @param typeClass not null
     * @param <T> type of instance
     * @return instance
     */
    public static <T> T createInstanceWithNoArgs(Class<T> typeClass) {
        Objects.requireNonNull(typeClass);
        return AccessController.doPrivileged((PrivilegedAction<T>) () -> {
            try {
                final Constructor<T> ctor = typeClass.getDeclaredConstructor();
                if (ctor.getModifiers() == Modifier.PROTECTED) {
                    ctor.setAccessible(true);
                }
                return ctor.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException cause) {
                throw new JsonbException("Can't create instance", cause);
            } catch (NoSuchMethodException cause) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.NO_DEFAULT_CONSTRUCTOR, typeClass), cause);
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
     * @param targetInterface interface to search
     *
     * @return type of JsonbAdapter
     */
    public static ParameterizedType locateParameterizedType(Class<?> targetClass, Class<?> targetInterface) {
        Class currClass = targetClass;
        while (currClass != Object.class) {
            for (Type ifaceType : currClass.getGenericInterfaces()) {
                if (ifaceType instanceof ParameterizedType &&
                        targetInterface.isAssignableFrom(
                                ReflectionTypeResolver.getRawType(((ParameterizedType) ifaceType).getRawType()))) {
                    return (ParameterizedType) ifaceType;
                }
            }
            currClass = currClass.getSuperclass();
        }
        throw new JsonbException(LocalizedMessages.getMessage(MessageConstants.NON_PARAMETRIZED_TYPE, targetInterface));
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
            for(Type argument : ((ParameterizedType) subject).getActualTypeArguments()) {
                if (!isResolvedType(argument)) {
                    return false;
                }
            }
            return true;
        }
        return subject instanceof Class<?>;
    }

    private static ParameterizedType locateParameterizedSuperclass(Type subject) {
        if (subject == null || subject instanceof ParameterizedType) {
            return (ParameterizedType) subject;
        }
        if (!(subject instanceof Class)) {
            throw new JsonbException("Can't resolve ParameterizedType superclass for: " + subject);
        }
        return locateParameterizedSuperclass(((Class) subject).getGenericSuperclass());
    }

    /**
     * Resolves a wildcard most specific upper or lower bound.
     *
     * @param runtimeInfo Type.
     * @param wildcard Wildcard type.
     * @return The most specific type.
     */
    private static Type determineMostSpecificBound(RuntimeTypeInfo runtimeInfo, WildcardType wildcard) {
        Class<?> candidateClass = Object.class;
        for (Type upper : wildcard.getUpperBounds()) {
            candidateClass = getMostSpecificBound(runtimeInfo, candidateClass, upper);
        }
        for (Type lower : wildcard.getLowerBounds()) {
            candidateClass = getMostSpecificBound(runtimeInfo, candidateClass, lower);
        }
        return candidateClass;
    }

    private static Class<?> getMostSpecificBound(RuntimeTypeInfo runtimeInfo, Class<?> candidateClass, Type constraint) {
        if (constraint == Object.class) {
            return candidateClass;
        }
        //if bound is type variable search recursively for wrapper generic expansion
        Type finalBound = constraint instanceof TypeVariable ? resolveActualType(runtimeInfo, constraint) : constraint;
        Class<?> boundClass = getRawType(finalBound);
        //resolved class is a subclass of a result candidate
        if (candidateClass.isAssignableFrom(boundClass)) {
            candidateClass = boundClass;
        }
        return candidateClass;
    }
}

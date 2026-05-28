/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *  Contributors:
 *  Roman Grigoriadi
 * ****************************************************************************
 */
package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.Messages;
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
public class ReflectiveTypeResolver {

    private static final Logger LOG = Logger.getLogger(ReflectiveTypeResolver.class.getName());

    /**
     * Get a raw type of any type.
     * If type is a {@link TypeVariable} recursively search {@link AbstractItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param element item containing wrapper class of a type field, not null.
     * @param target type to resolve, typically field type or generic bound, not null.
     * @return resolved raw class
     */
    public static Class<?> getRawType(RuntimeTypeInfo element, Type target) {
        if (!(target instanceof Class)) {
            if (!(target instanceof ParameterizedType)) {
                return getRawType(resolveActualType(element, target));
            } else {
                return (Class<?>) ((ParameterizedType) target).getRawType();
            }
        } else {
            return (Class<?>) target;
        }
    }

    /**
     * Resolves a wildcard most specific upper or lower bound.
     *
     * @param element Type.
     * @param wildcard Wildcard type.
     * @return The most specific type.
     */
    private static Type determineMostSpecificBound(RuntimeTypeInfo element, WildcardType wildcard) {
        Class<?> bestMatch = Object.class;
        for (Type upperLimit : wildcard.getUpperBounds()) {
            bestMatch = getMostSpecificBound(element, bestMatch, upperLimit);
        }
        for (Type lowerLimit : wildcard.getLowerBounds()) {
            bestMatch = getMostSpecificBound(element, bestMatch, lowerLimit);
        }
        return bestMatch;
    }

    private static Class<?> getMostSpecificBound(RuntimeTypeInfo element, Class<?> bestMatch, Type typeConstraint) {
        if (Object.class == typeConstraint) {
            return bestMatch;
        }
        //if bound is type variable search recursively for wrapper generic expansion
        Type inferredType = typeConstraint instanceof TypeVariable ? resolveActualType(element, typeConstraint) : typeConstraint;
        Class<?> rawClass = getRawType(inferredType);
        //resolved class is a subclass of a result candidate
        if (bestMatch.isAssignableFrom(rawClass)) {
            bestMatch = rawClass;
        }
        return bestMatch;
    }

    /**
     * Get default no argument constructor of the class.
     * @param targetClass Class to get constructor from
     * @param <T> Class generic type
     * @return constructor
     */
    public static <T> Constructor<T> getDefaultConstructor(Class<T> targetClass, boolean isMandatory) {
        Objects.requireNonNull(targetClass);
        return AccessController.doPrivileged((PrivilegedAction<Constructor<T>>) () -> {
            try {
                final Constructor<T> foundCtor = targetClass.getDeclaredConstructor();
                if (Modifier.PROTECTED == foundCtor.getModifiers()) {
                    foundCtor.setAccessible(true);
                }
                return foundCtor;
            } catch (NoSuchMethodException exception) {
                if (isMandatory) {
                    throw new JsonbException(Messages.getMessage(MessageKeyConstants.NO_DEFAULT_CONSTRUCTOR, targetClass), exception);
                }
                return null;
            }
        });
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
     * Create instance with constructor.
     *
     * @param ctorRef const not null
     * @param <T> type of instance
     * @return instance
     */
    public static <T> T instantiateNoArg(Constructor<T> ctorRef) {
        Objects.requireNonNull(ctorRef);
        try {
            return ctorRef.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new JsonbException("Can't create instance", exception);
        }
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
        Class clazzCursor = targetClass;
        while (Object.class != clazzCursor) {
            for (Type interfaceType : clazzCursor.getGenericInterfaces()) {
                if (interfaceType instanceof ParameterizedType && targetInterface.isAssignableFrom(ReflectiveTypeResolver.getRawType(((ParameterizedType) interfaceType).getRawType()))) {
                    return (ParameterizedType) interfaceType;
                }
            }
            clazzCursor = clazzCursor.getSuperclass();
        }
        throw new JsonbException(Messages.getMessage(MessageKeyConstants.NON_PARAMETRIZED_TYPE, targetInterface));
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
        return getOptionalRawType(target).orElseThrow(() -> new JsonbException(Messages.getMessage(MessageKeyConstants.TYPE_RESOLUTION_ERROR, target)));
    }

    public static Optional<Type> resolveAsOptional(RuntimeTypeInfo typeMeta, Type target) {
        try {
            return Optional.of(resolveActualType(typeMeta, target));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolve a type by item.
     * If type is a {@link TypeVariable} recursively search {@link AbstractItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param element item containing wrapper class of a type field, not null.
     * @param target type to resolve, typically field type or generic bound, not null.
     * @return resolved type
     */
    public static Type resolveActualType(RuntimeTypeInfo element, Type target) {
        if (!(target instanceof WildcardType)) {
            if (!(target instanceof TypeVariable)) {
                if (target instanceof ParameterizedType && null != element) {
                    return resolveGenericTypeArguments((ParameterizedType) target, element.getRuntimeType());
                }
            } else {
                return resolveVariableTypeForItem(element, (TypeVariable<?>) target);
            }
        } else {
            return determineMostSpecificBound(element, (WildcardType) target);
        }
        return target;
    }

    /**
     * Resolves {@link TypeVariable} arguments of generic types.
     *
     * @param targetParameterized type to resolve
     * @param searchTarget type to search
     * @return resolved type
     */
    public static Type resolveGenericTypeArguments(ParameterizedType targetParameterized, Type searchTarget) {
        final Type[] pendingArgs = targetParameterized.getActualTypeArguments();
        Type[] concreteArgs = new Type[pendingArgs.length];
        int idx = 0;
        while (pendingArgs.length > idx) {
            if ((pendingArgs[idx] instanceof TypeVariable)) {
                concreteArgs[idx] = new VariableTypeInheritanceSearch().searchParametrizedType(searchTarget, (TypeVariable<?>) pendingArgs[idx]);
                if (null == concreteArgs[idx]) {
                    //No generic information available
                    throw new IllegalStateException(Messages.getMessage(MessageKeyConstants.GENERIC_BOUND_NOT_FOUND, pendingArgs[idx], searchTarget));
                }
            } else {
                concreteArgs[idx] = pendingArgs[idx];
            }
            if (concreteArgs[idx] instanceof ParameterizedType) {
                concreteArgs[idx] = resolveGenericTypeArguments((ParameterizedType) concreteArgs[idx], searchTarget);
            }
            idx += 1;
        }
        return Arrays.equals(concreteArgs, pendingArgs) ? targetParameterized : new ResolvedParameterizedType(targetParameterized, concreteArgs);
    }

    /**
     * Resolve a bounded type variable type by its wrapper types.
     * Resolution could be done only if a compile time generic information is provided, either:
     * by generic field or subclass of a generic class.
     *
     * @param element item to search "runtime" generic type of a TypeVariable.
     * @param genericVar type to search in item for, not null.
     * @return Type of a generic "runtime" bound, not null.
     */
    public static Type resolveVariableTypeForItem(RuntimeTypeInfo element, TypeVariable<?> genericVar) {
        if (null == element) {
            //Bound not found, treat it as an Object.class
            LOG.warning(Messages.getMessage(MessageKeyConstants.GENERIC_BOUND_NOT_FOUND, genericVar, genericVar.getGenericDeclaration()));
            return Object.class;
        }
        //Embedded items doesn't hold information about variable types
        if (element instanceof EmbeddedItem) {
            return resolveVariableTypeForItem(element.getWrapper(), genericVar);
        }
        ParameterizedType wrapperParam = locateParameterizedSuperclass(element.getRuntimeType());
        VariableTypeInheritanceSearch inheritanceFinder = new VariableTypeInheritanceSearch();
        Type resolvedClass = inheritanceFinder.searchParametrizedType(wrapperParam, genericVar);
        if (null != resolvedClass) {
            if (resolvedClass instanceof TypeVariable) {
                return resolveVariableTypeForItem(element.getWrapper(), (TypeVariable<?>) resolvedClass);
            }
            return resolvedClass;
        }
        return resolveVariableTypeForItem(element.getWrapper(), genericVar);
    }

}

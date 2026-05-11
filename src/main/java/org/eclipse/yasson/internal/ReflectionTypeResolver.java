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

import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKey;
import org.eclipse.yasson.internal.serializer.BaseItem;
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

    private static final Logger LOG_OUTPUT = Logger.getLogger(ReflectionTypeResolver.class.getName());

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
        return getOptionalRawType(target).orElseThrow(() -> new JsonbException(MessageBundle.getMessage(MessageKey.TYPE_RESOLUTION_ERROR, target)));
    }

    /**
     * Get a raw type of any type.
     * If type is a {@link TypeVariable} recursively search {@link BaseItem} for resolution of typevar.
     * If type is a {@link WildcardType} find most specific upper / lower bound, which can be used. If most specific
     * bound is a {@link TypeVariable}, perform typevar resolution.
     *
     * @param elementInfo item containing wrapper class of a type field, not null.
     * @param target type to resolve, typically field type or generic bound, not null.
     * @return resolved raw class
     */
    public static Class<?> resolveRawClass(RuntimeTypeInfo elementInfo, Type target) {
        if (!(target instanceof Class)) {
            if (!(target instanceof ParameterizedType)) {
                return getRawType(resolveActualType(elementInfo, target));
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
     * @param elementInfo item containing wrapper class of a type field, not null.
     * @param target type to resolve, typically field type or generic bound, not null.
     * @return resolved type
     */
    public static Type resolveActualType(RuntimeTypeInfo elementInfo, Type target) {
        if (!(target instanceof WildcardType)) {
            if (!(target instanceof TypeVariable)) {
                if (target instanceof ParameterizedType && null != elementInfo) {
                    return resolveTypeParameters((ParameterizedType) target, elementInfo.getRuntimeType());
                }
            } else {
                return resolveItemTypeVariable(elementInfo, (TypeVariable<?>) target);
            }
        } else {
            return determineMostSpecificBound(elementInfo, (WildcardType) target);
        }
        return target;
    }

    public static Optional<Type> resolveTypeOptional(RuntimeTypeInfo runtimeData, Type target) {
        try {
            return Optional.of(resolveActualType(runtimeData, target));
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
     * @param variableParam type to search in item for, not null.
     * @return Type of a generic "runtime" bound, not null.
     */
    public static Type resolveItemTypeVariable(RuntimeTypeInfo elementInfo, TypeVariable<?> variableParam) {
        if (null == elementInfo) {
            //Bound not found, treat it as an Object.class
            LOG_OUTPUT.warning(MessageBundle.getMessage(MessageKey.GENERIC_BOUND_NOT_FOUND, variableParam, variableParam.getGenericDeclaration()));
            return Object.class;
        }
        //Embedded items doesn't hold information about variable types
        if (elementInfo instanceof EmbeddedItem) {
            return resolveItemTypeVariable(elementInfo.getWrapper(), variableParam);
        }
        ParameterizedType wrapperParam = findParameterizedSuperClass(elementInfo.getRuntimeType());
        VariableTypeInheritanceSearch finder = new VariableTypeInheritanceSearch();
        Type foundClass = finder.searchParametrizedType(wrapperParam, variableParam);
        if (null != foundClass) {
            if (foundClass instanceof TypeVariable) {
                return resolveItemTypeVariable(elementInfo.getWrapper(), (TypeVariable<?>) foundClass);
            }
            return foundClass;
        }
        return resolveItemTypeVariable(elementInfo.getWrapper(), variableParam);
    }

    /**
     * Resolves {@link TypeVariable} arguments of generic types.
     *
     * @param toResolve type to resolve
     * @param searchTarget type to search
     * @return resolved type
     */
    public static Type resolveTypeParameters(ParameterizedType toResolve, Type searchTarget) {
        final Type[] pendingArgs = toResolve.getActualTypeArguments();
        Type[] finalArgs = new Type[pendingArgs.length];
        int index = 0;
        while (pendingArgs.length > index) {
            if ((pendingArgs[index] instanceof TypeVariable)) {
                finalArgs[index] = new VariableTypeInheritanceSearch().searchParametrizedType(searchTarget, (TypeVariable<?>) pendingArgs[index]);
                if (null == finalArgs[index]) {
                    //No generic information available
                    throw new IllegalStateException(MessageBundle.getMessage(MessageKey.GENERIC_BOUND_NOT_FOUND, pendingArgs[index], searchTarget));
                }
            } else {
                finalArgs[index] = pendingArgs[index];
            }
            if (finalArgs[index] instanceof ParameterizedType) {
                finalArgs[index] = resolveTypeParameters((ParameterizedType) finalArgs[index], searchTarget);
            }
            index += 1;
        }
        return Arrays.equals(finalArgs, pendingArgs) ? toResolve : new ResolvedParameterizedType(toResolve, finalArgs);
    }

    /**
     * Create instance with constructor.
     *
     * @param ctor const not null
     * @param <T> type of instance
     * @return instance
     */
    public static <T> T createNoArgInstance(Constructor<T> ctor) {
        Objects.requireNonNull(ctor);
        try {
            return ctor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException cause) {
            throw new JsonbException("Can't create instance", cause);
        }
    }

    /**
     * Get default no argument constructor of the class.
     * @param targetClass Class to get constructor from
     * @param <T> Class generic type
     * @return constructor
     */
    public static <T> Constructor<T> getDefaultConstructor(Class<T> targetClass, boolean mandatory) {
        Objects.requireNonNull(targetClass);
        return AccessController.doPrivileged((PrivilegedAction<Constructor<T>>) () -> {
            try {
                final Constructor<T> foundCtor = targetClass.getDeclaredConstructor();
                if (Modifier.PROTECTED == foundCtor.getModifiers()) {
                    foundCtor.setAccessible(true);
                }
                return foundCtor;
            } catch (NoSuchMethodException cause) {
                if (mandatory) {
                    throw new JsonbException(MessageBundle.getMessage(MessageKey.NO_DEFAULT_CONSTRUCTOR, targetClass), cause);
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
     * @param candidateClass class to resolve parameterized interface
     * @param genericInterface interface to search
     *
     * @return type of JsonbAdapter
     */
    public static ParameterizedType findParameterizedInterfaceType(Class<?> candidateClass, Class<?> genericInterface) {
        Class activeClass = candidateClass;
        while (Object.class != activeClass) {
            for (Type activeInterface : activeClass.getGenericInterfaces()) {
                if (activeInterface instanceof ParameterizedType && genericInterface.isAssignableFrom(ReflectionTypeResolver.getRawType(((ParameterizedType) activeInterface).getRawType()))) {
                    return (ParameterizedType) activeInterface;
                }
            }
            activeClass = activeClass.getSuperclass();
        }
        throw new JsonbException(MessageBundle.getMessage(MessageKey.NON_PARAMETRIZED_TYPE, genericInterface));
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

    private static ParameterizedType findParameterizedSuperClass(Type target) {
        if (null == target || target instanceof ParameterizedType) {
            return (ParameterizedType) target;
        }
        if (!(target instanceof Class)) {
            throw new JsonbException("Can't resolve ParameterizedType superclass for: " + target);
        }
        return findParameterizedSuperClass(((Class) target).getGenericSuperclass());
    }

    /**
     * Resolves a wildcard most specific upper or lower bound.
     *
     * @param elementInfo Type.
     * @param wildcardDesc Wildcard type.
     * @return The most specific type.
     */
    private static Type determineMostSpecificBound(RuntimeTypeInfo elementInfo, WildcardType wildcardDesc) {
        Class<?> resolvedClass = Object.class;
        for (Type upper : wildcardDesc.getUpperBounds()) {
            resolvedClass = getMostSpecificBound(elementInfo, resolvedClass, upper);
        }
        for (Type lower : wildcardDesc.getLowerBounds()) {
            resolvedClass = getMostSpecificBound(elementInfo, resolvedClass, lower);
        }
        return resolvedClass;
    }

    private static Class<?> getMostSpecificBound(RuntimeTypeInfo elementInfo, Class<?> resolvedClass, Type typeConstraint) {
        if (Object.class == typeConstraint) {
            return resolvedClass;
        }
        //if bound is type variable search recursively for wrapper generic expansion
        Type resolvedType = typeConstraint instanceof TypeVariable ? resolveActualType(elementInfo, typeConstraint) : typeConstraint;
        Class<?> rawType = getRawType(resolvedType);
        //resolved class is a subclass of a result candidate
        if (resolvedClass.isAssignableFrom(rawType)) {
            resolvedClass = rawType;
        }
        return resolvedClass;
    }
}

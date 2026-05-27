/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.yasson.internal.deserializer;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.DefaultDeserializationContext;
import org.eclipse.yasson.internal.JsonBindingContext;
import org.eclipse.yasson.internal.JsonbConfigurationProperties;
import org.eclipse.yasson.internal.JsonbDateFormatter;
import org.eclipse.yasson.internal.JsonbNumberFormatter;
import org.eclipse.yasson.internal.ReflectionHelper;
import org.eclipse.yasson.internal.components.AdapterBindingInfo;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.deserializer.types.TypeDeserializers;
import org.eclipse.yasson.internal.model.BeanPropertyDescriptor;
import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.model.CreatorModel;
import org.eclipse.yasson.internal.model.JsonbCreator;
import org.eclipse.yasson.internal.model.customization.ClassSerializationConfig;
import org.eclipse.yasson.internal.model.customization.ComponentBindingCustomization;
import org.eclipse.yasson.internal.model.customization.SerializationCustomizer;
import org.eclipse.yasson.internal.model.customization.PropertyCustomization;
import org.eclipse.yasson.internal.model.customization.TypeInheritanceSettings;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;
import static jakarta.json.bind.JsonbConfig.PROPERTY_NAMING_STRATEGY;
import static jakarta.json.stream.JsonParser.Event;

/**
 * Creator of the deserialization models for deserialized types.
 * <br>
 * This class servers also as a cache for all previously created model deserializers.
 */
public class DeserializationModelFactory {

    private static final ModelParser<Object> EMPTY_MODEL_PARSER = (value, context) -> null;

    private static final Map<Class<?>, ModelParser<Object>> CREATOR_PARSERS_MAP;

    private static final Set<JsonParser.Event> MAP_KEY_EVENT_TYPES = new HashSet<>();

    static {
        MAP_KEY_EVENT_TYPES.add(Event.KEY_NAME);
        MAP_KEY_EVENT_TYPES.addAll(PositionChecker.Checker.VALUES.getEvents());
        Map<Class<?>, ModelParser<Object>> tmpValuesMap = new HashMap<>();
        tmpValuesMap.put(byte.class, (value, context) -> (byte) 0);
        tmpValuesMap.put(short.class, (value, context) -> (short) 0);
        tmpValuesMap.put(int.class, (value, context) -> 0);
        tmpValuesMap.put(long.class, (value, context) -> 0L);
        tmpValuesMap.put(float.class, (value, context) -> 0.0F);
        tmpValuesMap.put(double.class, (value, context) -> 0.0);
        tmpValuesMap.put(char.class, (value, context) -> '\u0000');
        tmpValuesMap.put(boolean.class, (value, context) -> false);
        tmpValuesMap.put(Optional.class, (value, context) -> Optional.empty());
        tmpValuesMap.put(OptionalInt.class, (value, context) -> OptionalInt.empty());
        tmpValuesMap.put(OptionalLong.class, (value, context) -> OptionalLong.empty());
        tmpValuesMap.put(OptionalDouble.class, (value, context) -> OptionalDouble.empty());
        CREATOR_PARSERS_MAP = Map.copyOf(tmpValuesMap);
    }

    private final Map<CachedEntry, ModelParser<JsonParser>> modelRegistry = new ConcurrentHashMap<>();

    private final JsonBindingContext jsonBinding;

    private final Map<Class<?>, Class<?>> userTypeMap;

    private static final class CachedEntry {

        private final Type targetType;

        private final JsonbNumberFormatter decimalFormatter;

        private final JsonbDateFormatter isoDateFormat;

        @Override
        public int hashCode() {
            return Objects.hash(targetType, decimalFormatter, isoDateFormat);
        }

        @Override
        public String toString() {
            return "CachedItem{" + "type=" + targetType + ", numberFormatter=" + decimalFormatter + ", dateFormatter=" + isoDateFormat + '}';
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (null == obj || obj.getClass() != getClass()) {
                return false;
            }
            CachedEntry otherEntry = (CachedEntry) obj;
            return Objects.equals(targetType, otherEntry.targetType) && Objects.equals(decimalFormatter, otherEntry.decimalFormatter) && Objects.equals(isoDateFormat, otherEntry.isoDateFormat);
        }

        CachedEntry(Type targetType, JsonbNumberFormatter decimalFormatter, JsonbDateFormatter isoDateFormat) {
            this.targetType = targetType;
            this.decimalFormatter = decimalFormatter;
            this.isoDateFormat = isoDateFormat;
        }

    }

    private Class<?> resolveImplementationClass(Class<?> rawClass, SerializationCustomizer serializationCustomizer) {
        if (rawClass.isInterface()) {
            Class<?> implType = null;
            //annotation
            if (serializationCustomizer instanceof PropertyCustomization) {
                implType = ((PropertyCustomization) serializationCustomizer).getImplementationClass();
            }
            //JsonbConfig
            if (null == implType) {
                implType = jsonBinding.getConfigProperties().getUserTypeMapping().get(rawClass);
            }
            if (null != implType) {
                if (!rawClass.isAssignableFrom(implType)) {
                    throw new JsonbException(MessageProvider.getMessage(MessageConstants.IMPL_CLASS_INCOMPATIBLE, implType, rawClass));
                }
                return implType;
            }
        }
        return rawClass;
    }

    private ModelParser<JsonParser> createTypeDeserializer(Class<?> rawClass, SerializationCustomizer serializationCustomizer, ModelParser<Object> delegateParser, Set<Event> eventSet) {
        return TypeDeserializers.getTypeDeserializer(rawClass, serializationCustomizer, jsonBinding.getConfigProperties(), delegateParser, eventSet);
    }

    private Optional<AdapterBindingInfo> findAdapterBinding(Type targetType, ComponentBindingCustomization classConfig) {
        return jsonBinding.getComponentMatcher().getDeserializeAdapterBinding(targetType, classConfig);
    }

    private Optional<DeserializerBinding<?>> findUserDeserializer(Type targetType, ComponentBindingCustomization classConfig) {
        return jsonBinding.getComponentMatcher().getDeserializerBinding(targetType, classConfig);
    }

    private ModelParser<JsonParser> createContextSwitcher(LinkedList<Type> typeQueue, ModelParser<Object> memberParser, Class<?> rawClass, Type targetType, SerializationCustomizer customizer) {
        ClassDescriptor classDescriptor = jsonBinding.getMappingContext().getOrCreateClassModel(rawClass);
        ModelParser<JsonParser> modelParser = createDeserializerChain(typeQueue, targetType, customizer, classDescriptor);
        return new ContextSwitcher(memberParser, modelParser);
    }

    private ModelParser<JsonParser> createTypeDeserializer(Class<?> rawClass, SerializationCustomizer serializationCustomizer, ModelParser<Object> delegateParser) {
        return createTypeDeserializer(rawClass, serializationCustomizer, delegateParser, PositionChecker.Checker.VALUES.getEvents());
    }

    private List<String> getCreatorParamsList(JsonbCreator factoryMethod) {
        return Arrays.stream(factoryMethod.getParams()).map(CreatorModel::getName).collect(Collectors.toList());
    }

    private ModelParser<JsonParser> buildObjectDeserializer(LinkedList<Type> typeQueue, Type targetType, SerializationCustomizer customizer, ClassDescriptor classDescriptor, Class<?> rawClass, CachedEntry cacheEntry) {
        ClassSerializationConfig classConfig = classDescriptor.getClassCustomization();
        Optional<DeserializerBinding<?>> bindingOpt = findUserDeserializer(targetType, (ComponentBindingCustomization) customizer);
        if (bindingOpt.isPresent()) {
            UserDefinedDeserializer customDeserializer = new UserDefinedDeserializer(bindingOpt.get().getJsonbDeserializer(), JustReturn.instance(), targetType, classConfig);
            modelRegistry.put(cacheEntry, customDeserializer);
            return customDeserializer;
        }
        JsonbCreator factoryMethod = classConfig.getCreator();
        boolean hasFactory = null != factoryMethod;
        List<String> parameterNames = hasFactory ? getCreatorParamsList(factoryMethod) : Collections.emptyList();
        Function<String, String> nameMapper = propertyNameTransformer();
        Map<String, ModelParser<JsonParser>> parserMap = new LinkedHashMap<>();
        Map<String, ModelParser<Object>> defaultCreationValues = new HashMap<>();
        for (BeanPropertyDescriptor propertyDescriptor : classDescriptor.getSortedProperties()) {
            if (!propertyDescriptor.isWritable() || parameterNames.contains(propertyDescriptor.getReadName())) {
                continue;
            }
            ModelParser<JsonParser> modelParser = processMemberType(typeQueue, propertyDescriptor, hasFactory);
            parserMap.put(nameMapper.apply(propertyDescriptor.getReadName()), modelParser);
        }
        for (String name : parameterNames) {
            CreatorModel creationModel = factoryMethod.findByName(name);
            ModelParser<JsonParser> modelParser = createTypeProcessor(typeQueue, creationModel.getType(), creationModel.getCustomization(), JustReturn.instance());
            String paramKey = nameMapper.apply(creationModel.getName());
            parserMap.put(paramKey, modelParser);
            if (!creationModel.getCustomization().isRequired()) {
                Class<?> paramRawClass = ReflectionHelper.getRawType(creationModel.getType());
                defaultCreationValues.put(paramKey, CREATOR_PARSERS_MAP.getOrDefault(paramRawClass, EMPTY_MODEL_PARSER));
            } else {
                defaultCreationValues.put(paramKey, new RequiredCreatorParameter(paramKey));
            }
        }
        ModelParser<JsonParser> instanceFactory;
        TypeInheritanceSettings inheritanceSettings = classConfig.getPolymorphismConfig();
        Set<String> excludedProperties = gatherIgnoredProperties(inheritanceSettings);
        boolean errorOnUnknown = jsonBinding.getConfigProperties().getConfigFailOnUnknownProperties();
        if (!hasFactory) {
            ModelParser<JsonParser> typeAdapter = new ObjectDeserializer(parserMap, nameMapper, rawClass, errorOnUnknown, excludedProperties);
            instanceFactory = new DefaultObjectInstanceCreator(typeAdapter, rawClass, classDescriptor.getDefaultConstructor());
        } else {
            instanceFactory = new JsonbCreatorDeserializer(parserMap, defaultCreationValues, factoryMethod, rawClass, nameMapper, errorOnUnknown, excludedProperties);
        }
        PositionChecker positionValidator = new PositionChecker(instanceFactory, rawClass, Event.START_OBJECT);
        if (null != inheritanceSettings && !inheritanceSettings.isInherited()) {
            instanceFactory = new PolymorphicInstanceFactory(rawClass, this, inheritanceSettings, positionValidator);
            positionValidator = new PositionChecker(instanceFactory, rawClass, Event.START_OBJECT);
        }
        ModelParser<JsonParser> nullValidator = new NullCheckDeserializer(positionValidator, JustReturn.instance());
        modelRegistry.put(cacheEntry, nullValidator);
        return nullValidator;
    }

    private ModelParser<JsonParser> buildGenericArrayDeserializer(CachedEntry cacheEntry, Class<?> rawClass, LinkedList<Type> typeQueue, SerializationCustomizer customizer) {
        GenericArrayType targetType = (GenericArrayType) cacheEntry.targetType;
        Class<?> elementClass = ReflectionHelper.getRawType(targetType.getGenericComponentType());
        ModelParser<JsonParser> typeParser = createTypeProcessor(typeQueue, targetType.getGenericComponentType(), customizer, JustReturn.instance());
        return buildArrayCommonDeserializer(cacheEntry, rawClass, elementClass, typeParser);
    }

    private ModelParser<JsonParser> buildArrayCommonDeserializer(CachedEntry cacheEntry, Class<?> rawClass, Class<?> elementClass, ModelParser<JsonParser> typeParser) {
        ArrayDeserializer arrayReader = new ArrayDeserializer(typeParser);
        ArrayInstanceCreator instanceFactory = ArrayInstanceCreator.create(rawClass, elementClass, arrayReader);
        PositionChecker positionValidator = new PositionChecker(instanceFactory, rawClass, Event.START_ARRAY);
        NullCheckDeserializer nullValidator = new NullCheckDeserializer(positionValidator, JustReturn.instance());
        modelRegistry.put(cacheEntry, nullValidator);
        return nullValidator;
    }

    private ModelParser<JsonParser> createDeserializerChain(LinkedList<Type> typeQueue, Type targetType, SerializationCustomizer customizer, ClassDescriptor classDescriptor) {
        if (typeQueue.contains(targetType)) {
            return new CyclicReferenceDeserializer(targetType);
        }
        try {
            typeQueue.add(targetType);
            return createDeserializerChainInternal(typeQueue, targetType, customizer, classDescriptor);
        } finally {
            typeQueue.removeLast();
        }
    }

    private ModelParser<JsonParser> createTypeProcessor(LinkedList<Type> typeQueue, Type targetType, SerializationCustomizer serializationCustomizer, ModelParser<Object> memberParser, Set<Event> eventSet) {
        Type resolvedType = ReflectionHelper.determineType(typeQueue, targetType);
        Class<?> rawClass = ReflectionHelper.getRawType(resolvedType);
        Optional<DeserializerBinding<?>> bindingOpt = findUserDeserializer(resolvedType, (ComponentBindingCustomization) serializationCustomizer);
        if (bindingOpt.isPresent()) {
            //TODO remove or not? fix for deserializer cycle
            //            ModelDeserializer<JsonParser> exactType = createNewChain(chain, memberDeserializer, rawType,
            //            resolved, customization);
            //            return new UserDefinedDeserializer(deserializerBinding.get().getJsonbDeserializer(),
            //                                               exactType,
            //                                               memberDeserializer,
            //                                               resolved,
            //                                               customization);
            return new UserDefinedDeserializer(bindingOpt.get().getJsonbDeserializer(), memberParser, resolvedType, serializationCustomizer);
        }
        Optional<AdapterBindingInfo> adapterInfoOpt = findAdapterBinding(resolvedType, (ComponentBindingCustomization) serializationCustomizer);
        if (adapterInfoOpt.isPresent()) {
            AdapterBindingInfo bindingInfo = adapterInfoOpt.get();
            ModelParser<JsonParser> typeParser = createTypeDeserializer(ReflectionHelper.getRawType(bindingInfo.getToType()), serializationCustomizer, JustReturn.instance(), eventSet);
            if (null == typeParser) {
                typeParser = createDeserializerChain(bindingInfo.getToType());
            }
            ModelParser<JsonParser> adapterModel = typeParser;
            AdapterDeserializer adapterHandler = new AdapterDeserializer(bindingInfo, memberParser);
            return (jsonReader, deserializer) -> {
                DefaultDeserializationContext localContext = new DefaultDeserializationContext(deserializer);
                Object parsedValue = adapterModel.deserializeModel(jsonReader, localContext);
                return adapterHandler.deserializeModel(parsedValue, deserializer);
            };
        }
        ModelParser<JsonParser> typeParser = createTypeDeserializer(rawClass, serializationCustomizer, memberParser, eventSet);
        if (null == typeParser) {
            Class<?> implementationClass = resolveImplementationClass(rawClass, serializationCustomizer);
            return createContextSwitcher(typeQueue, memberParser, implementationClass, resolvedType, serializationCustomizer);
        }
        return typeParser;
    }

    private ModelParser<JsonParser> buildCollectionDeserializer(CachedEntry cacheEntry, Class<?> rawClass, LinkedList<Type> typeQueue, SerializationCustomizer customizer) {
        Type targetType = cacheEntry.targetType;
        Type collectionType = targetType instanceof ParameterizedType ? ((ParameterizedType) targetType).getActualTypeArguments()[0] : Object.class;
        collectionType = ReflectionHelper.determineType(typeQueue, collectionType);
        ModelParser<JsonParser> typeParser = createTypeProcessor(typeQueue, collectionType, customizer, JustReturn.instance());
        CollectionDeserializer collectionParser = new CollectionDeserializer(typeParser);
        CollectionInstanceCreator collectionCreator = new CollectionInstanceCreator(collectionParser, targetType);
        PositionChecker positionValidator = new PositionChecker(collectionCreator, rawClass, Event.START_ARRAY);
        NullCheckDeserializer nullValidator = new NullCheckDeserializer(positionValidator, JustReturn.instance());
        modelRegistry.put(cacheEntry, nullValidator);
        return nullValidator;
    }

    private CachedEntry buildCachedItem(Type targetType, SerializationCustomizer serializationCustomizer) {
        return new CachedEntry(targetType, serializationCustomizer.getDeserializeNumberFormatter(), serializationCustomizer.getDeserializeDateFormatter());
    }

    private ModelParser<JsonParser> buildArrayDeserializer(CachedEntry cacheEntry, Class<?> rawClass, LinkedList<Type> typeQueue, SerializationCustomizer customizer) {
        JsonbConfigurationProperties jsonbConfig = jsonBinding.getConfigProperties();
        if (rawClass.equals(byte[].class) && !jsonbConfig.getBinaryDataStrategy().equals(BinaryDataStrategy.BYTE)) {
            String approach = jsonbConfig.getBinaryDataStrategy();
            ModelParser<JsonParser> typeParser = createTypeProcessor(typeQueue, String.class, customizer, JustReturn.instance());
            ModelParser<JsonParser> base64Parser = ArrayInstanceCreator.createBase64Deserializer(approach, typeParser);
            NullCheckDeserializer nullValidator = new NullCheckDeserializer(base64Parser, JustReturn.instance());
            modelRegistry.put(cacheEntry, nullValidator);
            return nullValidator;
        }
        Class<?> arrayClass = rawClass.getComponentType();
        ModelParser<JsonParser> typeParser = createTypeProcessor(typeQueue, arrayClass, customizer, JustReturn.instance());
        return buildArrayCommonDeserializer(cacheEntry, rawClass, arrayClass, typeParser);
    }

    private ModelParser<JsonParser> processMemberType(LinkedList<Type> typeQueue, BeanPropertyDescriptor propertyDescriptor, boolean hasFactory) {
        ModelParser<Object> memberParser;
        Type targetType = propertyDescriptor.getPropertyDeserializationType();
        memberParser = new ValueSetterDeserializer(propertyDescriptor.getSetValueHandle());
        if (hasFactory) {
            memberParser = new DeferredDeserializer(memberParser);
        }
        return createTypeProcessor(typeQueue, targetType, propertyDescriptor.getCustomization(), memberParser);
    }

    /**
     * Create new instance.
     *
     * @param jsonBinding jsonb context
     */
    public DeserializationModelFactory(JsonBindingContext jsonBinding) {
        this.jsonBinding = jsonBinding;
        this.userTypeMap = jsonBinding.getConfigProperties().getUserTypeMapping();
    }

    /**
     * Starts deserializer creation process.
     *
     * @param targetType type the deserializer is created for
     * @return created deserializer
     */
    public ModelParser<JsonParser> createDeserializerChain(Type targetType) {
        LinkedList<Type> typeQueue = new LinkedList<>();
        ClassDescriptor classDescriptor = jsonBinding.getMappingContext().getOrCreateClassModel(ReflectionHelper.getRawType(targetType));
        return createDeserializerChain(typeQueue, targetType, classDescriptor.getClassCustomization(), classDescriptor);
    }

    private ModelParser<JsonParser> createTypeProcessor(LinkedList<Type> typeQueue, Type targetType, SerializationCustomizer serializationCustomizer, ModelParser<Object> memberParser) {
        return createTypeProcessor(typeQueue, targetType, serializationCustomizer, memberParser, PositionChecker.Checker.VALUES.getEvents());
    }

    private OptionalDeserializer buildOptionalDeserializer(LinkedList<Type> typeQueue, Type targetType, SerializationCustomizer customizer, CachedEntry cacheEntry) {
        Type collectionType = targetType instanceof ParameterizedType ? ((ParameterizedType) targetType).getActualTypeArguments()[0] : Object.class;
        ModelParser<JsonParser> typeParser = createTypeProcessor(typeQueue, collectionType, customizer, JustReturn.instance());
        OptionalDeserializer optionalReader = new OptionalDeserializer(typeParser, JustReturn.instance());
        modelRegistry.put(cacheEntry, optionalReader);
        return optionalReader;
    }

    private ModelParser<JsonParser> createDeserializerChainInternal(LinkedList<Type> typeQueue, Type targetType, SerializationCustomizer customizer, ClassDescriptor classDescriptor) {
        Class<?> rawClass = classDescriptor.getType();
        CachedEntry cacheEntry = buildCachedItem(targetType, customizer);
        if (!modelRegistry.containsKey(cacheEntry)) {
            if (userTypeMap.containsKey(rawClass)) {
                Class<?> userRawClass = userTypeMap.get(rawClass);
                ModelParser<JsonParser> modelParser = createDeserializerChain(userRawClass);
                modelRegistry.put(cacheEntry, modelParser);
                return modelParser;
            }
        } else {
            return modelRegistry.get(cacheEntry);
        }
        Optional<AdapterBindingInfo> adapterInfoOpt = findAdapterBinding(targetType, (ComponentBindingCustomization) customizer);
        if (adapterInfoOpt.isPresent()) {
            AdapterBindingInfo bindingInfo = adapterInfoOpt.get();
            Class<?> targetClass = ReflectionHelper.getRawType(bindingInfo.getToType());
            ClassDescriptor targetDescriptor = jsonBinding.getMappingContext().getOrCreateClassModel(targetClass);
            ModelParser<JsonParser> typeParser = createTypeDeserializer(targetClass, targetDescriptor.getClassCustomization(), JustReturn.instance());
            if (null == typeParser) {
                typeParser = createDeserializerChain(bindingInfo.getToType());
            }
            ModelParser<JsonParser> adapterModel = typeParser;
            AdapterDeserializer adapterHandler = new AdapterDeserializer(bindingInfo, JustReturn.instance());
            ModelParser<JsonParser> adapterParserModel = (jsonReader, deserializer) -> {
                Object parsedValue = adapterModel.deserializeModel(jsonReader, deserializer);
                return adapterHandler.deserializeModel(parsedValue, deserializer);
            };
            modelRegistry.put(cacheEntry, adapterParserModel);
            return adapterParserModel;
        }
        ModelParser<JsonParser> typeParser = createTypeDeserializer(rawClass, customizer, JustReturn.instance());
        if (null != typeParser) {
            modelRegistry.put(cacheEntry, typeParser);
            return typeParser;
        }
        if (!Collection.class.isAssignableFrom(rawClass)) {
            if (!Map.class.isAssignableFrom(rawClass)) {
                if (!rawClass.isArray()) {
                    if (!(targetType instanceof GenericArrayType)) {
                        if (!Optional.class.isAssignableFrom(rawClass)) {
                            return buildObjectDeserializer(typeQueue, targetType, customizer, classDescriptor, rawClass, cacheEntry);
                        } else {
                            return buildOptionalDeserializer(typeQueue, targetType, customizer, cacheEntry);
                        }
                    } else {
                        return buildGenericArrayDeserializer(cacheEntry, rawClass, typeQueue, customizer);
                    }
                } else {
                    return buildArrayDeserializer(cacheEntry, rawClass, typeQueue, customizer);
                }
            } else {
                return buildMapDeserializer(cacheEntry, rawClass, typeQueue, customizer);
            }
        } else {
            return buildCollectionDeserializer(cacheEntry, rawClass, typeQueue, customizer);
        }
    }

    private ModelParser<JsonParser> buildMapDeserializer(CachedEntry cacheEntry, Class<?> rawClass, LinkedList<Type> typeQueue, SerializationCustomizer customizer) {
        Type targetType = cacheEntry.targetType;
        Type mapKeyType = targetType instanceof ParameterizedType ? ((ParameterizedType) targetType).getActualTypeArguments()[0] : Object.class;
        Type mapValueType = targetType instanceof ParameterizedType ? ((ParameterizedType) targetType).getActualTypeArguments()[1] : Object.class;
        ModelParser<JsonParser> keyParser = createTypeProcessor(typeQueue, mapKeyType, ClassSerializationConfig.emptyConfig(), JustReturn.instance(), MAP_KEY_EVENT_TYPES);
        ModelParser<JsonParser> valueParser = createTypeProcessor(typeQueue, mapValueType, customizer, JustReturn.instance());
        MapDeserializer mapParser = new MapDeserializer(keyParser, valueParser);
        MapInstanceCreator mapFactory = new MapInstanceCreator(mapParser, jsonBinding.getConfigProperties(), rawClass);
        PositionChecker positionValidator = new PositionChecker(mapFactory, rawClass, PositionChecker.Checker.CONTAINER);
        NullCheckDeserializer nullValidator = new NullCheckDeserializer(positionValidator, JustReturn.instance());
        modelRegistry.put(cacheEntry, nullValidator);
        return nullValidator;
    }

    private Function<String, String> propertyNameTransformer() {
        boolean ignoreCase = jsonBinding.getConfig().getProperty(PROPERTY_NAMING_STRATEGY).filter(propertyName -> propertyName.equals(PropertyNamingStrategy.CASE_INSENSITIVE)).isPresent();
        return ignoreCase ? String::toLowerCase : input -> input;
    }

    private Set<String> gatherIgnoredProperties(TypeInheritanceSettings inheritanceSettings) {
        Set<String> excludedProperties = new HashSet<>();
        if (null != inheritanceSettings) {
            TypeInheritanceSettings activeSettings = inheritanceSettings;
            while (null != activeSettings) {
                excludedProperties.add(activeSettings.getFieldName());
                activeSettings = activeSettings.getParentConfig();
            }
        }
        return excludedProperties;
    }

}

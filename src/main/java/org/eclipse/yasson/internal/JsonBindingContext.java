/*
 * Copyright (c) 2016, 2022 Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.logging.Logger;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParserFactory;

import org.eclipse.yasson.internal.components.JsonbComponentInstanceCreatorFactory;
import org.eclipse.yasson.internal.deserializer.DeserializationModelFactory;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;
import org.eclipse.yasson.internal.serializer.SerializationModelBuilder;
import org.eclipse.yasson.spi.JsonbComponentInstanceCreator;

/**
 * Jsonb context holding central components and configuration of jsonb runtime. Scoped to instance of Jsonb runtime.
 */
public class JsonBindingContext {

    private static final Logger JSONB_CONTEXT_LOG = Logger.getLogger(JsonBindingContext.class.getName());

    private final JsonbConfig bindingConfig;

    private final ClassModelContext modelContext;

    private final DeserializationModelFactory deserializationFactory;

    private final SerializationModelBuilder serializationFactory;

    private final JsonbComponentInstanceCreator componentFactory;

    private final JsonProvider jsonProcessingProvider;

    private final JsonParserFactory parserFactory;

    private final ComponentBindingResolver bindingResolver;

    private final AnnotationIntrospector introspector;

    private final JsonbConfigurationProperties configurationProperties;

    /**
     * Creates and initialize context.
     *
     * @param bindingConfig  jsonb jsonbConfig not null
     * @param jsonProcessingProvider provider of JSONP
     */
    public JsonBindingContext(JsonbConfig bindingConfig, JsonProvider jsonProcessingProvider) {
        Objects.requireNonNull(bindingConfig);
        this.bindingConfig = bindingConfig;
        this.modelContext = new ClassModelContext(this);
        this.componentFactory = createComponentInstanceCreator();
        this.bindingResolver = new ComponentBindingResolver(this);
        this.introspector = new AnnotationIntrospector(this);
        this.jsonProcessingProvider = jsonProcessingProvider;
        this.parserFactory = createJsonParserFactory();
        this.configurationProperties = new JsonbConfigurationProperties(bindingConfig);
        this.deserializationFactory = new DeserializationModelFactory(this);
        this.serializationFactory = new SerializationModelBuilder(this);
    }

    /**
     * Gets {@link JsonbConfig}.
     *
     * @return Configuration.
     */
    public JsonbConfig getConfig() {
        return bindingConfig;
    }

    /**
     * Gets mapping context.
     *
     * @return Mapping context.
     */
    public ClassModelContext getMappingContext() {
        return modelContext;
    }

    /**
     * Get chain model creator.
     *
     * @return chain model creator
     */
    public DeserializationModelFactory getChainModelCreator() {
        return deserializationFactory;
    }

    /**
     * Get serialization model creator.
     *
     * @return serialization model creator
     */
    public SerializationModelBuilder getSerializationModelCreator() {
        return serializationFactory;
    }

    /**
     * Gets JSONP provider.
     *
     * @return JSONP provider.
     */
    public JsonProvider getJsonProvider() {
        return jsonProcessingProvider;
    }

    /**
     * Implementation creating instances of user components used by JSONB, such as adapters and strategies.
     *
     * @return Instance creator.
     */
    public JsonbComponentInstanceCreator getComponentInstanceCreator() {
        return componentFactory;
    }

    /**
     * Component matcher for lookup of (de)serializers and adapters.
     *
     * @return Component matcher.
     */
    public ComponentBindingResolver getComponentMatcher() {
        return bindingResolver;
    }

    /**
     * Gets component for annotation parsing.
     *
     * @return Annotation introspector.
     */
    public AnnotationIntrospector getAnnotationIntrospector() {
        return introspector;
    }

    public JsonbConfigurationProperties getConfigProperties() {
        return configurationProperties;
    }

    public JsonParserFactory getJsonParserFactory() {
        return parserFactory;
    }

    private JsonParserFactory createJsonParserFactory() {
        return jsonProcessingProvider.createParserFactory(generateJsonpProperties(bindingConfig));
    }

    /**
     * Propagates properties from JsonbConfig to JSONP generator / parser factories.
     *
     * @param bindingConfig jsonb config
     * @return properties for JSONP generator / parser
     */
    protected Map<String, ?> generateJsonpProperties(JsonbConfig bindingConfig) {
        //JSONP 1.0 actually ignores the value, just checks the key is present. Only set if JsonbConfig.FORMATTING is true.
        final Optional<Object> optionalValue = bindingConfig.getProperty(JsonbConfig.FORMATTING);
        final Map<String, Object> factoryMap = new HashMap<>();
        if (optionalValue.isPresent()) {
            final Object entryObj = optionalValue.get();
            if (!(entryObj instanceof Boolean)) {
                throw new JsonbException(MessageProvider.getMessage(MessageConstants.JSONB_CONFIG_FORMATTING_ILLEGAL_VALUE));
            }
            if ((Boolean) entryObj) {
                factoryMap.put(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
            }
            return factoryMap;
        }
        return factoryMap;
    }

    private JsonbComponentInstanceCreator createComponentInstanceCreator() {
        ServiceLoader<JsonbComponentInstanceCreator> serviceFinder = AccessController
                .doPrivileged((PrivilegedAction<ServiceLoader<JsonbComponentInstanceCreator>>) () -> ServiceLoader
                        .load(JsonbComponentInstanceCreator.class));
        List<JsonbComponentInstanceCreator> instanceProviders = new ArrayList<>();
        for (JsonbComponentInstanceCreator incomingInstance : serviceFinder) {
            instanceProviders.add(incomingInstance);
        }
        if (instanceProviders.isEmpty()) {
            // No service provider found - use the defaults
            return JsonbComponentInstanceCreatorFactory.getComponentInstanceCreator();
        }
        instanceProviders.sort(Comparator.comparingInt(JsonbComponentInstanceCreator::getPriority).reversed());
        JsonbComponentInstanceCreator incomingInstance = instanceProviders.get(0);
        JSONB_CONTEXT_LOG.finest("Component instance creator:" + incomingInstance.getClass());
        return incomingInstance;
    }

}

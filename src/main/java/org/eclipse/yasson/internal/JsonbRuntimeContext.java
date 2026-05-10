/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.logging.Logger;

import jakarta.json.bind.JsonbConfig;
import jakarta.json.spi.JsonProvider;

import org.eclipse.yasson.internal.components.JsonbComponentInstanceCreatorFactory;
import org.eclipse.yasson.spi.JsonbComponentInstanceCreator;

/**
 * Jsonb context holding central components and configuration of jsonb runtime. Scoped to instance of Jsonb runtime.
 */
public class JsonbRuntimeContext {

    private static final Logger RUNTIME_LOG = Logger.getLogger(JsonbRuntimeContext.class.getName());

    private final JsonbConfig config;

    private final MappingContext mapContext;

    private final JsonbComponentInstanceCreator componentFactory;

    private final JsonProvider provider;

    private final ComponentMatcher componentFilter;

    private final AnnotationIntrospector annotationInspector;

    private final JsonbConfigurationProperties configurationProperties;

    private final InstanceFactory factory;

    /**
     * Creates and initialize context.
     *
     * @param config  jsonb jsonbConfig not null
     * @param provider provider of JSONP
     */
    public JsonbRuntimeContext(JsonbConfig config, JsonProvider provider) {
        Objects.requireNonNull(config);
        this.config = config;
        this.mapContext = new MappingContext(this);
        this.factory = InstanceFactory.getSingleton();
        this.componentFactory = initializeComponentInstanceCreator(factory);
        this.componentFilter = new ComponentMatcher(this);
        this.annotationInspector = new AnnotationIntrospector(this);
        this.provider = provider;
        this.configurationProperties = new JsonbConfigurationProperties(config);
    }

    /**
     * Gets {@link JsonbConfig}.
     *
     * @return Configuration.
     */
    public JsonbConfig getConfig() {
        return config;
    }

    /**
     * Gets mapping context.
     *
     * @return Mapping context.
     */
    public MappingContext getMappingContext() {
        return mapContext;
    }

    /**
     * Gets JSONP provider.
     *
     * @return JSONP provider.
     */
    public JsonProvider getJsonProvider() {
        return provider;
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
    public ComponentMatcher getComponentMatcher() {
        return componentFilter;
    }

    /**
     * Gets component for annotation parsing.
     *
     * @return Annotation introspector.
     */
    public AnnotationIntrospector getAnnotationIntrospector() {
        return annotationInspector;
    }

    public JsonbConfigurationProperties getConfigProperties() {
        return configurationProperties;
    }

    /**
     * Returns component for creating instances of non-parsed types.
     *
     * @return InstanceCreator
     */
    public InstanceFactory getInstanceCreator() {
        return factory;
    }

    private JsonbComponentInstanceCreator initializeComponentInstanceCreator(InstanceFactory factory) {
        ServiceLoader<JsonbComponentInstanceCreator> serviceProviders = AccessController
                .doPrivileged((PrivilegedAction<ServiceLoader<JsonbComponentInstanceCreator>>) () -> ServiceLoader
                        .load(JsonbComponentInstanceCreator.class));
        List<JsonbComponentInstanceCreator> creatorList = new ArrayList<>();
        for (JsonbComponentInstanceCreator componentCandidate : serviceProviders) {
            creatorList.add(componentCandidate);
        }
        if (creatorList.isEmpty()) {
            // No service provider found - use the defaults
            return JsonbComponentInstanceCreatorFactory.getComponentInstanceCreator(factory);
        }
        creatorList.sort(Comparator.comparingInt(JsonbComponentInstanceCreator::getPriority).reversed());
        JsonbComponentInstanceCreator componentCandidate = creatorList.get(0);
        RUNTIME_LOG.finest("Component instance creator:" + componentCandidate.getClass());
        return componentCandidate;
    }

}

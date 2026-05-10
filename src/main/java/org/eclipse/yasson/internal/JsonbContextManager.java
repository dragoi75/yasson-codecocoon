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
public class JsonbContextManager {

    private static final Logger LOG = Logger.getLogger(JsonbContextManager.class.getName());

    private final JsonbConfig configSettings;

    private final MappingContext mapperContext;

    private final JsonbComponentInstanceCreator componentFactory;

    private final JsonProvider providerService;

    private final ComponentMatcher matcher;

    private final JsonbAnnotationIntrospector annotationInspector;

    private final JsonbConfigurationProperties configurationProperties;

    private final InstanceCreator instanceFactory;

    /**
     * Creates and initialize context.
     *
     * @param configSettings  jsonb jsonbConfig not null
     * @param providerService provider of JSONP
     */
    public JsonbContextManager(JsonbConfig configSettings, JsonProvider providerService) {
        Objects.requireNonNull(configSettings);
        this.configSettings = configSettings;
        this.mapperContext = new MappingContext(this);
        this.instanceFactory = InstanceCreator.getSingleton();
        this.componentFactory = initializeComponentInstanceCreator(instanceFactory);
        this.matcher = new ComponentMatcher(this);
        this.annotationInspector = new JsonbAnnotationIntrospector(this);
        this.providerService = providerService;
        this.configurationProperties = new JsonbConfigurationProperties(configSettings);
    }

    /**
     * Gets {@link JsonbConfig}.
     *
     * @return Configuration.
     */
    public JsonbConfig getConfig() {
        return configSettings;
    }

    /**
     * Gets mapping context.
     *
     * @return Mapping context.
     */
    public MappingContext getMappingContext() {
        return mapperContext;
    }

    /**
     * Gets JSONP provider.
     *
     * @return JSONP provider.
     */
    public JsonProvider getJsonProvider() {
        return providerService;
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
        return matcher;
    }

    /**
     * Gets component for annotation parsing.
     *
     * @return Annotation introspector.
     */
    public JsonbAnnotationIntrospector getAnnotationIntrospector() {
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
    public InstanceCreator getInstanceCreator() {
        return instanceFactory;
    }

    private JsonbComponentInstanceCreator initializeComponentInstanceCreator(InstanceCreator instanceFactory) {
        ServiceLoader<JsonbComponentInstanceCreator> serviceProvider = AccessController
                .doPrivileged((PrivilegedAction<ServiceLoader<JsonbComponentInstanceCreator>>) () -> ServiceLoader
                        .load(JsonbComponentInstanceCreator.class));
        List<JsonbComponentInstanceCreator> creatorList = new ArrayList<>();
        for (JsonbComponentInstanceCreator candidate : serviceProvider) {
            creatorList.add(candidate);
        }
        if (creatorList.isEmpty()) {
            // No service provider found - use the defaults
            return JsonbComponentInstanceCreatorFactory.getComponentInstanceCreator(instanceFactory);
        }
        creatorList.sort(Comparator.comparingInt(JsonbComponentInstanceCreator::getPriority).reversed());
        JsonbComponentInstanceCreator candidate = creatorList.get(0);
        LOG.finest("Component instance creator:" + candidate.getClass());
        return candidate;
    }

}

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

    private static final Logger JSONB_RUNTIME_LOG = Logger.getLogger(JsonbRuntimeContext.class.getName());

    private final JsonbConfig runtimeConfig;

    private final MappingContext mapContext;

    private final JsonbComponentInstanceCreator componentFactory;

    private final JsonProvider jsonSource;

    private final ComponentMatcher componentResolver;

    private final JsonbAnnotationIntrospector annotationInspector;

    private final JsonbConfigurationProperties configurationProperties;

    private final InstanceCreator instanceFactory;

    /**
     * Implementation creating instances of user components used by JSONB, such as adapters and strategies.
     *
     * @return Instance creator.
     */
    public JsonbComponentInstanceCreator getComponentInstanceCreator() {
        return componentFactory;
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

    /**
     * Gets {@link JsonbConfig}.
     *
     * @return Configuration.
     */
    public JsonbConfig getConfig() {
        return runtimeConfig;
    }

    /**
     * Creates and initialize context.
     *
     * @param runtimeConfig  jsonb jsonbConfig not null
     * @param jsonSource provider of JSONP
     */
    public JsonbRuntimeContext(JsonbConfig runtimeConfig, JsonProvider jsonSource) {
        Objects.requireNonNull(runtimeConfig);
        this.runtimeConfig = runtimeConfig;
        this.mapContext = new MappingContext(this);
        this.instanceFactory = InstanceCreator.getSingleton();
        this.componentFactory = initializeComponentInstanceCreator(instanceFactory);
        this.componentResolver = new ComponentMatcher(this);
        this.annotationInspector = new JsonbAnnotationIntrospector(this);
        this.jsonSource = jsonSource;
        this.configurationProperties = new JsonbConfigurationProperties(runtimeConfig);
    }

    private JsonbComponentInstanceCreator initializeComponentInstanceCreator(InstanceCreator instanceFactory) {
        ServiceLoader<JsonbComponentInstanceCreator> serviceProvider = AccessController
                .doPrivileged((PrivilegedAction<ServiceLoader<JsonbComponentInstanceCreator>>) () -> ServiceLoader
                        .load(JsonbComponentInstanceCreator.class));
        List<JsonbComponentInstanceCreator> creatorList = new ArrayList<>();
        for (JsonbComponentInstanceCreator componentFactoryParam : serviceProvider) {
            creatorList.add(componentFactoryParam);
        }
        if (creatorList.isEmpty()) {
            // No service provider found - use the defaults
            return JsonbComponentInstanceCreatorFactory.getComponentInstanceCreator(instanceFactory);
        }
        creatorList.sort(Comparator.comparingInt(JsonbComponentInstanceCreator::getPriority).reversed());
        JsonbComponentInstanceCreator componentFactoryParam = creatorList.get(0);
        JSONB_RUNTIME_LOG.finest("Component instance creator:" + componentFactoryParam.getClass());
        return componentFactoryParam;
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
        return jsonSource;
    }

    /**
     * Component matcher for lookup of (de)serializers and adapters.
     *
     * @return Component matcher.
     */
    public ComponentMatcher getComponentMatcher() {
        return componentResolver;
    }

}

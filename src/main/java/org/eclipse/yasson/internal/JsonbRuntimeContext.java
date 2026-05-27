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
import org.eclipse.yasson.spi.JsonbComponentFactory;

/**
 * Jsonb context holding central components and configuration of jsonb runtime. Scoped to instance of Jsonb runtime.
 */
public class JsonbRuntimeContext {

    private static final Logger JSONB_LOG = Logger.getLogger(JsonbRuntimeContext.class.getName());

    private final JsonbConfig configOptions;

    private final MappingContext mapContext;

    private final JsonbComponentFactory componentFactory;

    private final JsonProvider jsonSupplier;

    private final ComponentBindingResolver componentResolver;

    private final AnnotationIntrospector annotationInspector;

    private final JsonbConfigProperties configProps;

    private final InstanceCreator objectFactory;

    /**
     * Gets JSONP provider.
     *
     * @return JSONP provider.
     */
    public JsonProvider getJsonProvider() {
        return jsonSupplier;
    }

    private JsonbComponentFactory initializeComponentInstanceCreator(InstanceCreator objectFactory) {
        ServiceLoader<JsonbComponentFactory> serviceSupplier = AccessController
                .doPrivileged((PrivilegedAction<ServiceLoader<JsonbComponentFactory>>) () -> ServiceLoader
                        .load(JsonbComponentFactory.class));
        List<JsonbComponentFactory> factoryList = new ArrayList<>();
        for (JsonbComponentFactory factory : serviceSupplier) {
            factoryList.add(factory);
        }
        if (factoryList.isEmpty()) {
            // No service provider found - use the defaults
            return JsonbComponentInstanceCreatorFactory.getComponentInstanceCreator(objectFactory);
        }
        factoryList.sort(Comparator.comparingInt(JsonbComponentFactory::getPriority).reversed());
        JsonbComponentFactory factory = factoryList.get(0);
        JSONB_LOG.finest("Component instance creator:" + factory.getClass());
        return factory;
    }

    /**
     * Returns component for creating instances of non-parsed types.
     *
     * @return InstanceCreator
     */
    public InstanceCreator getInstanceCreator() {
        return objectFactory;
    }

    /**
     * Gets component for annotation parsing.
     *
     * @return Annotation introspector.
     */
    public AnnotationIntrospector getAnnotationIntrospector() {
        return annotationInspector;
    }

    /**
     * Gets {@link JsonbConfig}.
     *
     * @return Configuration.
     */
    public JsonbConfig getConfig() {
        return configOptions;
    }

    public JsonbConfigProperties getConfigProperties() {
        return configProps;
    }

    /**
     * Component matcher for lookup of (de)serializers and adapters.
     *
     * @return Component matcher.
     */
    public ComponentBindingResolver getComponentMatcher() {
        return componentResolver;
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
     * Creates and initialize context.
     *
     * @param configOptions  jsonb jsonbConfig not null
     * @param jsonSupplier provider of JSONP
     */
    public JsonbRuntimeContext(JsonbConfig configOptions, JsonProvider jsonSupplier) {
        Objects.requireNonNull(configOptions);
        this.configOptions = configOptions;
        this.mapContext = new MappingContext(this);
        this.objectFactory = InstanceCreator.getSingleton();
        this.componentFactory = initializeComponentInstanceCreator(objectFactory);
        this.componentResolver = new ComponentBindingResolver(this);
        this.annotationInspector = new AnnotationIntrospector(this);
        this.jsonSupplier = jsonSupplier;
        this.configProps = new JsonbConfigProperties(configOptions);
    }

    /**
     * Implementation creating instances of user components used by JSONB, such as adapters and strategies.
     *
     * @return Instance creator.
     */
    public JsonbComponentFactory getComponentInstanceCreator() {
        return componentFactory;
    }

}

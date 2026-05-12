/*******************************************************************************
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.internal.components.JsonbComponentInstanceCreatorFactory;
import org.eclipse.yasson.spi.JsonbComponentInstanceCreator;

import javax.json.bind.JsonbConfig;
import javax.json.spi.JsonProvider;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * Jsonb context holding central components and configuration of jsonb runtime. Scoped to instance of Jsonb runtime.
 *
 * @author Roman Grigoriadi
 */
public class JsonbBindingContext {
    
    private static final Logger ERROR_RECORDER = Logger.getLogger(JsonbBindingContext.class.getName());

    private final JsonbConfig bindingConfig;

    private final MappingContext mapperContext;

    private final JsonbComponentInstanceCreator componentFactory;

    private final JsonProvider jsonSource;

    private final ComponentMatcher componentFilter;

    private final AnnotationIntrospector annotationInspector;

    private final JsonbConfigProperties configurationProperties;

    private final InstanceCreator instanceFactory;

    /**
     * Creates and initialize context.
     *
     * @param bindingConfig jsonb jsonbConfig not null
     * @param jsonSource provider of JSONP
     */
    public JsonbBindingContext(JsonbConfig bindingConfig, JsonProvider jsonSource) {
        Objects.requireNonNull(bindingConfig);
        this.bindingConfig = bindingConfig;
        this.mapperContext = new MappingContext(this);
        this.instanceFactory = new InstanceCreator();
        this.componentFactory = initializeComponentInstanceCreator(instanceFactory);
        this.componentFilter = new ComponentMatcher(this);
        this.annotationInspector = new AnnotationIntrospector(this);
        this.jsonSource = jsonSource;
        this.configurationProperties = new JsonbConfigProperties(bindingConfig);
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
    public MappingContext getMappingContext() {
        return mapperContext;
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


    public JsonbConfigProperties getConfigProperties() {
        return configurationProperties;
    }


    /**
     * Returns component for creating instances of non-parsed types.
     * @return InstanceCreator
     */
    public InstanceCreator getInstanceCreator() {
        return instanceFactory;
    }

    private JsonbComponentInstanceCreator initializeComponentInstanceCreator(InstanceCreator instanceFactory) {
        ServiceLoader<JsonbComponentInstanceCreator> serviceRegistry = AccessController
                .doPrivileged((PrivilegedAction<ServiceLoader<JsonbComponentInstanceCreator>>) () -> ServiceLoader
                        .load(JsonbComponentInstanceCreator.class));
        List<JsonbComponentInstanceCreator> componentFactoryList = new ArrayList<>();
        for (JsonbComponentInstanceCreator candidate : serviceRegistry) {
            componentFactoryList.add(candidate);
        }
        if (componentFactoryList.isEmpty()) {
            // No service provider found - use the defaults
            return JsonbComponentInstanceCreatorFactory.getComponentInstanceCreator(instanceFactory);
        }
        componentFactoryList.sort(Comparator.comparingInt(JsonbComponentInstanceCreator::getPriority).reversed());
        JsonbComponentInstanceCreator candidate = componentFactoryList.get(0);
        ERROR_RECORDER.finest("Component instance creator:" + candidate.getClass());
        return candidate;
    }

}

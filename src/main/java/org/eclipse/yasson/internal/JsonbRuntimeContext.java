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
public class JsonbRuntimeContext {
    
    private static final Logger RUNTIME_TRACE = Logger.getLogger(JsonbRuntimeContext.class.getName());

    private final JsonbConfig configSettings;

    private final MappingContext mappingState;

    private final JsonbComponentInstanceCreator componentFactory;

    private final JsonProvider jsonEngine;

    private final ComponentMatcher componentResolver;

    private final AnnotationIntrospector annotationInspector;

    private final JsonbConfigProperties configOptions;

    private final InstanceFactory instanceFactory;


    /**
     * Returns component for creating instances of non-parsed types.
     * @return InstanceCreator
     */
    public InstanceFactory getInstanceCreator() {
        return instanceFactory;
    }

    /**
     * Implementation creating instances of user components used by JSONB, such as adapters and strategies.
     *
     * @return Instance creator.
     */
    public JsonbComponentInstanceCreator getComponentInstanceCreator() {
        return componentFactory;
    }

    private JsonbComponentInstanceCreator initializeComponentInstanceCreator(InstanceFactory instanceFactory) {
        ServiceLoader<JsonbComponentInstanceCreator> serviceRegistry = AccessController
                .doPrivileged((PrivilegedAction<ServiceLoader<JsonbComponentInstanceCreator>>) () -> ServiceLoader
                        .load(JsonbComponentInstanceCreator.class));
        List<JsonbComponentInstanceCreator> creatorList = new ArrayList<>();
        for (JsonbComponentInstanceCreator candidate : serviceRegistry) {
            creatorList.add(candidate);
        }
        if (creatorList.isEmpty()) {
            // No service provider found - use the defaults
            return JsonbComponentInstanceCreatorFactory.getComponentInstanceCreator(instanceFactory);
        }
        creatorList.sort(Comparator.comparingInt(JsonbComponentInstanceCreator::getPriority).reversed());
        JsonbComponentInstanceCreator candidate = creatorList.get(0);
        RUNTIME_TRACE.finest("Component instance creator:" + candidate.getClass());
        return candidate;
    }

    /**
     * Gets JSONP provider.
     *
     * @return JSONP provider.
     */
    public JsonProvider getJsonProvider() {
        return jsonEngine;
    }

    public JsonbConfigProperties getConfigProperties() {
        return configOptions;
    }

    /**
     * Component matcher for lookup of (de)serializers and adapters.
     *
     * @return Component matcher.
     */
    public ComponentMatcher getComponentMatcher() {
        return componentResolver;
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
     * Gets mapping context.
     *
     * @return Mapping context.
     */
    public MappingContext getMappingContext() {
        return mappingState;
    }

    /**
     * Creates and initialize context.
     *
     * @param configSettings jsonb jsonbConfig not null
     * @param jsonEngine provider of JSONP
     */
    public JsonbRuntimeContext(JsonbConfig configSettings, JsonProvider jsonEngine) {
        Objects.requireNonNull(configSettings);
        this.configSettings = configSettings;
        this.mappingState = new MappingContext(this);
        this.instanceFactory = new InstanceFactory();
        this.componentFactory = initializeComponentInstanceCreator(instanceFactory);
        this.componentResolver = new ComponentMatcher(this);
        this.annotationInspector = new AnnotationIntrospector(this);
        this.jsonEngine = jsonEngine;
        this.configOptions = new JsonbConfigProperties(configSettings);
    }

    /**
     * Gets {@link JsonbConfig}.
     *
     * @return Configuration.
     */
    public JsonbConfig getConfig() {
        return configSettings;
    }

}

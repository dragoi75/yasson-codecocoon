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
    
    private static final Logger JSONB_RUNTIME_LOGGER = Logger.getLogger(JsonbRuntimeContext.class.getName());

    private final JsonbConfig config;

    private final ClassModelRegistry classModelRegistry;

    private final JsonbComponentInstanceCreator componentFactory;

    private final JsonProvider jsonSource;

    private final ComponentMatcher matcher;

    private final AnnotationIntrospector introspector;

    private final JsonbConfigProperties configProps;

    private final InstanceCreator instanceFactory;

    /**
     * Creates and initialize context.
     *
     * @param config jsonb jsonbConfig not null
     * @param jsonSource provider of JSONP
     */
    public JsonbRuntimeContext(JsonbConfig config, JsonProvider jsonSource) {
        Objects.requireNonNull(config);
        this.config = config;
        this.classModelRegistry = new ClassModelRegistry(this);
        this.instanceFactory = new InstanceCreator();
        this.componentFactory = initializeComponentInstanceCreator(instanceFactory);
        this.matcher = new ComponentMatcher(this);
        this.introspector = new AnnotationIntrospector(this);
        this.jsonSource = jsonSource;
        this.configProps = new JsonbConfigProperties(config);
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
    public ClassModelRegistry getMappingContext() {
        return classModelRegistry;
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
        return matcher;
    }

    /**
     * Gets component for annotation parsing.
     *
     * @return Annotation introspector.
     */
    public AnnotationIntrospector getAnnotationIntrospector() {
        return introspector;
    }


    public JsonbConfigProperties getConfigProperties() {
        return configProps;
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
        JSONB_RUNTIME_LOGGER.finest("Component instance creator:" + candidate.getClass());
        return candidate;
    }

}

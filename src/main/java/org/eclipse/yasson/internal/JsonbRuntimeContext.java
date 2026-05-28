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
    
    private static final Logger JSONB_RUNTIME_AUDITOR = Logger.getLogger(JsonbRuntimeContext.class.getName());

    private final JsonbConfig jsonbConfiguration;

    private final ClassMappingContext classMappingCtx;

    private final JsonbComponentInstanceCreator componentFactory;

    private final JsonProvider jsonService;

    private final ComponentBindingResolver bindingResolver;

    private final AnnotationIntrospector annotationInspector;

    private final JsonbConfigurationProperties configurationProperties;

    private final InstanceCreator instantiator;


    /**
     * Component matcher for lookup of (de)serializers and adapters.
     *
     * @return Component matcher.
     */
    public ComponentBindingResolver getComponentMatcher() {
        return bindingResolver;
    }

    private JsonbComponentInstanceCreator initializeComponentInstanceCreator(InstanceCreator instantiator) {
        ServiceLoader<JsonbComponentInstanceCreator> componentCreatorServices = AccessController
                .doPrivileged((PrivilegedAction<ServiceLoader<JsonbComponentInstanceCreator>>) () -> ServiceLoader
                        .load(JsonbComponentInstanceCreator.class));
        List<JsonbComponentInstanceCreator> creatorList = new ArrayList<>();
        for (JsonbComponentInstanceCreator candidate : componentCreatorServices) {
            creatorList.add(candidate);
        }
        if (creatorList.isEmpty()) {
            // No service provider found - use the defaults
            return JsonbComponentInstanceCreatorFactory.getComponentInstanceCreator(instantiator);
        }
        creatorList.sort(Comparator.comparingInt(JsonbComponentInstanceCreator::getPriority).reversed());
        JsonbComponentInstanceCreator candidate = creatorList.get(0);
        JSONB_RUNTIME_AUDITOR.finest("Component instance creator:" + candidate.getClass());
        return candidate;
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
     * Creates and initialize context.
     *
     * @param jsonbConfiguration jsonb jsonbConfig not null
     * @param jsonService provider of JSONP
     */
    public JsonbRuntimeContext(JsonbConfig jsonbConfiguration, JsonProvider jsonService) {
        Objects.requireNonNull(jsonbConfiguration);
        this.jsonbConfiguration = jsonbConfiguration;
        this.classMappingCtx = new ClassMappingContext(this);
        this.instantiator = new InstanceCreator();
        this.componentFactory = initializeComponentInstanceCreator(instantiator);
        this.bindingResolver = new ComponentBindingResolver(this);
        this.annotationInspector = new AnnotationIntrospector(this);
        this.jsonService = jsonService;
        this.configurationProperties = new JsonbConfigurationProperties(jsonbConfiguration);
    }

    public JsonbConfigurationProperties getConfigProperties() {
        return configurationProperties;
    }

    /**
     * Gets mapping context.
     *
     * @return Mapping context.
     */
    public ClassMappingContext getMappingContext() {
        return classMappingCtx;
    }

    /**
     * Gets JSONP provider.
     *
     * @return JSONP provider.
     */
    public JsonProvider getJsonProvider() {
        return jsonService;
    }

    /**
     * Returns component for creating instances of non-parsed types.
     * @return InstanceCreator
     */
    public InstanceCreator getInstanceCreator() {
        return instantiator;
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
     * Gets {@link JsonbConfig}.
     *
     * @return Configuration.
     */
    public JsonbConfig getConfig() {
        return jsonbConfiguration;
    }

}

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

import org.eclipse.yasson.internal.components.JsonbComponentInstanceCreator;
import org.eclipse.yasson.internal.components.JsonbComponentInstanceCreatorFactory;

import javax.json.bind.JsonbConfig;
import javax.json.spi.JsonProvider;
import java.util.HashSet;
import java.util.Objects;

/**
 * Jsonb context holding central components and configuration of jsonb runtime. Scoped to instance of Jsonb runtime.
 *
 * @author Roman Grigoriadi
 */
public class JsonbRuntimeContext {

    private final JsonbConfig configSettings;

    private final ClassMappingRegistry mappingRegistry;

    private final JsonbComponentInstanceCreator instanceFactory;

    private final JsonProvider jsonFactory;

    private final ComponentBindingResolver bindingResolver;

    private final AnnotationIntrospector annotationInspector;

    private final JsonbConfigurationProperties configurationProperties;


    /**
     * Gets mapping context.
     *
     * @return Mapping context.
     */
    public ClassMappingRegistry getMappingContext() {
        return mappingRegistry;
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
     * Implementation creating instances of user components used by JSONB, such as adapters and strategies.
     *
     * @return Instance creator.
     */
    public JsonbComponentInstanceCreator getComponentInstanceCreator() {
        return instanceFactory;
    }

    /**
     * Gets JSONP provider.
     *
     * @return JSONP provider.
     */
    public JsonProvider getJsonProvider() {
        return jsonFactory;
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
     * Creates and initialize context.
     *
     * @param configSettings jsonb jsonbConfig not null
     * @param jsonFactory provider of JSONP
     */
    public JsonbRuntimeContext(JsonbConfig configSettings, JsonProvider jsonFactory) {
        Objects.requireNonNull(configSettings);
        this.configSettings = configSettings;
        this.mappingRegistry = new ClassMappingRegistry(this);
        this.instanceFactory = JsonbComponentInstanceCreatorFactory.getComponentInstanceCreator();
        this.bindingResolver = new ComponentBindingResolver(this);
        this.annotationInspector = new AnnotationIntrospector(this);
        this.jsonFactory = jsonFactory;
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

}

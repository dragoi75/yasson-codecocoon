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
public class JsonbConfigurationContext {

    private final JsonbConfig jsonbSettings;

    private final MappingRegistry mappingRegistry;

    private final JsonbComponentInstanceCreator componentFactory;

    private final JsonProvider jsonService;

    private final ComponentMatcher componentSelector;

    private final AnnotationIntrospector annotationInspector;

    private final JsonbConfigurationProperties configurationProperties;

    /**
     * Creates and initialize context.
     *
     * @param jsonbSettings jsonb jsonbConfig not null
     * @param jsonService provider of JSONP
     */
    public JsonbConfigurationContext(JsonbConfig jsonbSettings, JsonProvider jsonService) {
        Objects.requireNonNull(jsonbSettings);
        this.jsonbSettings = jsonbSettings;
        this.mappingRegistry = new MappingRegistry(this);
        this.componentFactory = JsonbComponentInstanceCreatorFactory.getComponentInstanceCreator();
        this.componentSelector = new ComponentMatcher(this);
        this.annotationInspector = new AnnotationIntrospector(this);
        this.jsonService = jsonService;
        this.configurationProperties = new JsonbConfigurationProperties(jsonbSettings);
    }

    /**
     * Gets {@link JsonbConfig}.
     *
     * @return Configuration.
     */
    public JsonbConfig getConfig() {
        return jsonbSettings;
    }

    /**
     * Gets mapping context.
     *
     * @return Mapping context.
     */
    public MappingRegistry getMappingContext() {
        return mappingRegistry;
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
        return componentSelector;
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

}

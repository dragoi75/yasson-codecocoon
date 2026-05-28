/*******************************************************************************
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Dmitry Kornilov
 * Roman Grigoriadi
 ******************************************************************************/
package org.eclipse.yasson.internal;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.spi.JsonProvider;
import java.util.Optional;

/**
 * JsonbBuilder implementation.
 *
 * @author Dmitry Kornilov
 */
public class JsonBindingConfigurator implements JsonbBuilder {
    private JsonbConfig jsonbSettings = new JsonbConfig();
    private JsonProvider jsonEngine = null;

    /**
     * Gets configuration.
     *
     * @return configuration.
     */
    public JsonbConfig getConfig() {
        return jsonbSettings;
    }

    @Override
    public Jsonb build() {
        return new JsonConverter(this);
    }

    /**
     * Gets provider.
     *
     * @return Provider.
     */
    public Optional<JsonProvider> getProvider() {
        return Optional.ofNullable(jsonEngine);
    }

    @Override
    public JsonbBuilder withProvider(JsonProvider jsonpHandler) {
        this.jsonEngine = jsonpHandler;
        return this;
    }

    @Override
    public JsonbBuilder withConfig(JsonbConfig jsonbSettings) {
        this.jsonbSettings = jsonbSettings;
        return this;
    }

}

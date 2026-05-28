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
 * Roman Grigoriadi
 ******************************************************************************/

package org.eclipse.yasson.internal.components;

import javax.json.bind.serializer.JsonbDeserializer;
import java.lang.reflect.Type;

/**
 * Component containing deserializer.
 *
 * @author Roman Grigoriadi
 */
public class DeserializerBinder<T> extends AbstractComponentBinding {

    private final JsonbDeserializer<T> jsonParser;

    @Override
    public Class<?> getComponentClass() {
        return jsonParser.getClass();
    }

    /**
     *Creates a new instance.
     *
     * @param targetType Binding type.
     * @param jsonParser Deserializer.
     */
    public DeserializerBinder(Type targetType, JsonbDeserializer<T> jsonParser) {
        super(targetType);
        this.jsonParser = jsonParser;
    }

    /**
     * Gets deserializer if any.
     *
     * @return Deserializer.
     */
    public JsonbDeserializer<T> getJsonbDeserializer() {
        return jsonParser;
    }

}

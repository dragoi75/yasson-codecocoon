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

import javax.json.bind.serializer.JsonbSerializer;
import java.lang.reflect.Type;

/**
 * Binding for user Serializer component.
 *
 * @author Roman Grigoriadi
 */
public class SerializerBindingEntry<T> extends AbstractComponentBinding {

    private final JsonbSerializer<T> serializerInstance;

    /**
     * Class of user component.
     *
     * @return Component class.
     */
    @Override
    public Class<?> getComponentClass() {
        return serializerInstance.getClass();
    }

    /**
     * Creates a new instance.
     *
     * @param boundType Generic type argument of serializer. Not null.
     * @param serializerInstance Serializer. Can be null.
     */
    public SerializerBindingEntry(Type boundType, JsonbSerializer<T> serializerInstance) {
        super(boundType);
        this.serializerInstance = serializerInstance;
    }

    /**
     * Returns a serializer if any.
     *
     * @return Serializer.
     */
    public JsonbSerializer<T> getJsonbSerializer() {
        return serializerInstance;
    }

}

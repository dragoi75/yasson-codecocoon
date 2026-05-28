/*******************************************************************************
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * Customization for a property of a class.
 *
 * @author Roman Grigoriadi
 */
public class PropertySerializationConfig extends CustomizationBase {

    private final String jsonReadKey;

    private final String jsonWriteKey;

    private final JsonbNumberFormatter numberSerializer;

    private final JsonbNumberFormatter numberDeserializer;

    private final JsonbDateFormatter dateSerializer;

    private final JsonbDateFormatter dateDeserializer;

    private boolean skipOnRead;

    private boolean skipOnWrite;

    private final Class implementationType;


    @Override
    public JsonbDateFormatter getDeserializeDateFormatter() {
        return dateDeserializer;
    }

    @Override
    public JsonbDateFormatter getSerializeDateFormatter() {
        return dateSerializer;
    }

    /**
     * The flag indicating whether the value of the underlying type/property should be processed during serialization process or not.
     *
     * @return true indicates that the underlying type/property should be included in serialization process and false indicates it should not
     */
    public boolean isReadTransient() {
        return skipOnRead;
    }

    /**
     * Implementation class if property is interface type.
     *
     * @return class implementing property interface
     */
    public Class getImplementationClass() {
        return implementationType;
    }

    /**
     * Name if specified for property getter with {@link javax.json.bind.annotation.JsonbProperty}.
     *
     * @return write name
     */
    public String getJsonWriteName() {
        return jsonWriteKey;
    }

    @Override
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        return numberDeserializer;
    }

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param customizationHandler not null
     */
    public PropertySerializationConfig(PropertyCustomizationBuilder customizationHandler) {
        super(customizationHandler);
        this.jsonReadKey = customizationHandler.getJsonReadName();
        this.jsonWriteKey = customizationHandler.getJsonWriteName();
        this.numberSerializer = customizationHandler.getSerializeNumberFormatter();
        this.numberDeserializer = customizationHandler.getDeserializeNumberFormatter();
        this.dateSerializer = customizationHandler.getSerializeDateFormatter();
        this.dateDeserializer = customizationHandler.getDeserializeDateFormatter();
        this.skipOnRead = customizationHandler.isReadTransient();
        this.skipOnWrite = customizationHandler.isWriteTransient();
        this.implementationType = customizationHandler.getImplementationClass();
    }

    /**
     * Name if specified for property setter with {@link javax.json.bind.annotation.JsonbProperty}.
     *
     * @return read name
     */
    public String getJsonReadName() {
        return jsonReadKey;
    }

    /**
     * The flag indicating whether the value of the underlying type/property should be processed during deserialization process or not.
     *
     * @return true indicates that the underlying type/property should be included in deserialization process and false indicates it should not
     */
    public boolean isWriteTransient() {
        return skipOnWrite;
    }

    @Override
    public JsonbNumberFormatter getSerializeNumberFormatter() {
        return numberSerializer;
    }

}
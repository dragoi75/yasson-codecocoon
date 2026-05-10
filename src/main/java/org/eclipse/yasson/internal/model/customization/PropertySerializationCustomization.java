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

import org.eclipse.yasson.internal.serializer.JsonbDateTimeFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * Customization for a property of a class.
 *
 * @author Roman Grigoriadi
 */
public class PropertySerializationCustomization extends CustomizationBase {

    private final String inputJsonKey;

    private final String outputJsonKey;

    private final JsonbNumberFormatter numberOutputFormatter;

    private final JsonbNumberFormatter numberInputFormatter;

    private final JsonbDateTimeFormatter dateOutputFormatter;

    private final JsonbDateTimeFormatter dateInputFormatter;

    private boolean transientOnRead;

    private boolean transientOnWrite;

    private final Class concreteClass;

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param propertyCustomizer not null
     */
    public PropertySerializationCustomization(PropertyCustomizationBuilder propertyCustomizer) {
        super(propertyCustomizer);
        this.inputJsonKey = propertyCustomizer.getJsonReadName();
        this.outputJsonKey = propertyCustomizer.getJsonWriteName();
        this.numberOutputFormatter = propertyCustomizer.getSerializeNumberFormatter();
        this.numberInputFormatter = propertyCustomizer.getDeserializeNumberFormatter();
        this.dateOutputFormatter = propertyCustomizer.getSerializeDateFormatter();
        this.dateInputFormatter = propertyCustomizer.getDeserializeDateFormatter();
        this.transientOnRead = propertyCustomizer.isReadTransient();
        this.transientOnWrite = propertyCustomizer.isWriteTransient();
        this.concreteClass = propertyCustomizer.getImplementationClass();
    }

    /**
     * Name if specified for property setter with {@link javax.json.bind.annotation.JsonbProperty}.
     *
     * @return read name
     */
    public String getJsonReadName() {
        return inputJsonKey;
    }

    /**
     * Name if specified for property getter with {@link javax.json.bind.annotation.JsonbProperty}.
     *
     * @return write name
     */
    public String getJsonWriteName() {
        return outputJsonKey;
    }

    @Override
    public JsonbNumberFormatter getSerializeNumberFormatter() {
        return numberOutputFormatter;
    }

    @Override
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        return numberInputFormatter;
    }

    @Override
    public JsonbDateTimeFormatter getSerializeDateFormatter() {
        return dateOutputFormatter;
    }

    @Override
    public JsonbDateTimeFormatter getDeserializeDateFormatter() {
        return dateInputFormatter;
    }


    /**
     * The flag indicating whether the value of the underlying type/property should be processed during serialization process or not.
     *
     * @return true indicates that the underlying type/property should be included in serialization process and false indicates it should not
     */
    public boolean isReadTransient() {
        return transientOnRead;
    }

    /**
     * The flag indicating whether the value of the underlying type/property should be processed during deserialization process or not.
     *
     * @return true indicates that the underlying type/property should be included in deserialization process and false indicates it should not
     */
    public boolean isWriteTransient() {
        return transientOnWrite;
    }

    /**
     * Implementation class if property is interface type.
     *
     * @return class implementing property interface
     */
    public Class getImplementationClass() {
        return concreteClass;
    }

}
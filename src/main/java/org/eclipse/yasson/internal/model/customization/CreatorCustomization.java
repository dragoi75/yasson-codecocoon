package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.model.PropertyModel;
import org.eclipse.yasson.internal.serializer.JsonbDateTimeFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * Customization for creator (constructor / factory methods) parameters.
 */
public class CreatorCustomization extends CustomizationBase {

    private JsonbNumberFormatter numberFormatter;

    private JsonbDateTimeFormatter dateFormatter;

    private PropertyModel propertyModel;

    @Override
    public JsonbDateTimeFormatter getSerializeDateFormatter() {
        throw new UnsupportedOperationException("Serialization is not supported for creator parameters.");
    }

    /**
     * Set property referenced model.
     * @param propertyModel referenced property model
     */
    public void setPropertyModel(PropertyModel propertyModel) {
        this.propertyModel = propertyModel;
    }

    @Override
    public boolean isNillable() {
        throw new UnsupportedOperationException("Not supported for creator parameters.");
    }

    public CreatorCustomization(CustomizationBuilder customization, JsonbNumberFormatter numberFormatter, JsonbDateTimeFormatter dateFormatter) {
        super(customization);
        this.numberFormatter = numberFormatter;
        this.dateFormatter = dateFormatter;
    }

    @Override
    public JsonbNumberFormatter getSerializeNumberFormatter() {
        throw new UnsupportedOperationException("Serialization is not supported for creator parameters.");
    }

    @Override
    public JsonbDateTimeFormatter getDeserializeDateFormatter() {
        if (null == dateFormatter) {
            if (null != propertyModel) {
                return propertyModel.getCustomization().getDeserializeDateFormatter();
            }
        } else {
            return dateFormatter;
        }
        return null;
    }

    @Override
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        if (null == numberFormatter) {
            if (null != propertyModel) {
                return propertyModel.getCustomization().getDeserializeNumberFormatter();
            }
        } else {
            return numberFormatter;
        }
        return null;
    }

}

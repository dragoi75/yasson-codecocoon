package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.model.PropertyModel;
import org.eclipse.yasson.internal.serializer.JsonbDateFormatter;
import org.eclipse.yasson.internal.serializer.JsonbNumberFormatter;

/**
 * Customization for creator (constructor / factory methods) parameters.
 */
public class CreatorCustomization extends CustomizationBase {

    private JsonbNumberFormatter numberFormatter;

    private JsonbDateFormatter dateFormatter;

    private PropertyModel propertyModel;

    @Override
    public boolean isNillable() {
        throw new UnsupportedOperationException("Not supported for creator parameters.");
    }

    @Override
    public JsonbDateFormatter getDeserializeDateFormatter() {
        if (null == dateFormatter) {
            if (null != propertyModel) {
                return propertyModel.getCustomization().getDeserializeDateFormatter();
            }
        } else {
            return dateFormatter;
        }
        return null;
    }

    public CreatorCustomization(CustomizationBuilder customization, JsonbNumberFormatter numberFormatter, JsonbDateFormatter dateFormatter) {
        super(customization);
        this.numberFormatter = numberFormatter;
        this.dateFormatter = dateFormatter;
    }

    @Override
    public JsonbDateFormatter getSerializeDateFormatter() {
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

    @Override
    public JsonbNumberFormatter getSerializeNumberFormatter() {
        throw new UnsupportedOperationException("Serialization is not supported for creator parameters.");
    }

}

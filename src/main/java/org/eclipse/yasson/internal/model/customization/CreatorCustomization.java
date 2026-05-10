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
    public JsonbNumberFormatter getDeserializeNumberFormatter() {
        if (numberFormatter != null) {
            return numberFormatter;
        } else if (propertyModel != null) {
            return propertyModel.getCustomization().getDeserializeNumberFormatter();
        }
        return null;
    }

    @Override
    public JsonbDateTimeFormatter getSerializeDateFormatter() {
        throw new UnsupportedOperationException("Serialization is not supported for creator parameters.");
    }

    @Override
    public JsonbDateTimeFormatter getDeserializeDateFormatter() {
        if (dateFormatter != null) {
            return dateFormatter;
        } else if (propertyModel != null) {
            return propertyModel.getCustomization().getDeserializeDateFormatter();
        }
        return null;
    }

    @Override
    public boolean isNillable() {
        throw new UnsupportedOperationException("Not supported for creator parameters.");
    }

    /**
     * Set property referenced model.
     * @param propertyModel referenced property model
     */
    public void setPropertyModel(PropertyModel propertyModel) {
        this.propertyModel = propertyModel;
    }
}

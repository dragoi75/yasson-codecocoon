package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.components.DeserializerBinder;
import org.eclipse.yasson.internal.components.SerializerBindingEntry;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;

/**
 * Customization which is aware of bound components, such as adapters and (de)serializers.
 */
public interface ComponentBindingCustomization {

    /**
     * Adapter wrapper class with resolved generic information.
     *
     * @return components wrapper
     */
    TypeAdapterBinding getAdapterBinding();

    /**
     * Serializer wrapper with resolved generic info.
     *
     * @return serializer wrapper
     */
    SerializerBindingEntry getSerializerBinding();

    /**
     * Deserializer wrapper with resolved generic info.
     *
     * @return deserializer wrapper
     */
    DeserializerBinder getDeserializerBinding();
}

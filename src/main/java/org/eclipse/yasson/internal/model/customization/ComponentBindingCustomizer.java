package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.components.AdapterBindingDescriptor;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;

/**
 * Customization which is aware of bound components, such as adapters and (de)serializers.
 */
public interface ComponentBindingCustomizer {

    /**
     * Deserializer wrapper with resolved generic info.
     *
     * @return deserializer wrapper
     */
    JsonbDeserializerBinding getDeserializerBinding();

    /**
     * Serializer wrapper with resolved generic info.
     *
     * @return serializer wrapper
     */
    SerializerBinding getSerializerBinding();

    /**
     * Adapter wrapper class with resolved generic information.
     *
     * @return components wrapper
     */
    AdapterBindingDescriptor getAdapterBinding();

}

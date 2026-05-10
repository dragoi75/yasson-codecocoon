package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.components.AdapterBindingDescriptor;
import org.eclipse.yasson.internal.components.JsonbDeserializerBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;

/**
 * Common properties of {@link ClassCustomization} and {@link PropertySerializationCustomization}.
 */
abstract class CustomizationBase implements SerializationCustomization, ComponentBindingCustomizer {

    private final AdapterBindingDescriptor adapterBinding;

    private final SerializerBinding serializerBinding;

    private final JsonbDeserializerBinding deserializerBinding;

    private final boolean nillable;

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param builder not null
     */
    public CustomizationBase(CustomizationBuilder builder) {
        this.nillable = builder.isNillable();
        this.adapterBinding = builder.getAdapterInfo();
        this.serializerBinding = builder.getSerializerBinding();
        this.deserializerBinding = builder.getDeserializerBinding();
    }

    /**
     * Copy constructor.
     *
     * @param other other customization instance
     */
    public CustomizationBase(CustomizationBase other) {
        this.nillable = other.isNillable();
        this.adapterBinding = other.getAdapterBinding();
        this.serializerBinding = other.getSerializerBinding();
        this.deserializerBinding = other.getDeserializerBinding();
    }

    /**
     * Returns true if <i>nillable</i> customization is present.
     *
     * @return True if <i>nillable</i> customization is present.
     */
    public boolean isNillable() {
        return nillable;
    }

    /**
     * Adapter wrapper class with resolved generic information.
     *
     * @return components wrapper
     */
    public AdapterBindingDescriptor getAdapterBinding() {
        return adapterBinding;
    }

    /**
     * Serializer wrapper with resolved generic info.
     *
     * @return serializer wrapper
     */
    public SerializerBinding getSerializerBinding() {
        return serializerBinding;
    }

    /**
     * Deserializer wrapper with resolved generic info.
     *
     * @return deserializer wrapper
     */
    public JsonbDeserializerBinding getDeserializerBinding() {
        return deserializerBinding;
    }


}

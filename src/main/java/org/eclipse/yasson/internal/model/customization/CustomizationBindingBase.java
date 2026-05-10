package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.components.AdapterBinding;
import org.eclipse.yasson.internal.components.DeserializerBinding;
import org.eclipse.yasson.internal.components.SerializerBinding;

/**
 * Common properties of {@link ClassCustomization} and {@link PropertyCustomization}.
 */
abstract class CustomizationBindingBase implements SerializationCustomization, ComponentBoundCustomization {

    private final AdapterBinding adapterBinder;

    private final SerializerBinding serializerBinder;

    private final DeserializerBinding deserializerBinder;

    private final boolean allowNull;

    /**
     * Copies properties from builder an creates immutable instance.
     *
     * @param customizer not null
     */
    public CustomizationBindingBase(CustomizationBuilder customizer) {
        this.allowNull = customizer.isNillable();
        this.adapterBinder = customizer.getAdapterInfo();
        this.serializerBinder = customizer.getSerializerBinding();
        this.deserializerBinder = customizer.getDeserializerBinding();
    }

    /**
     * Copy constructor.
     *
     * @param peerBinding other customization instance
     */
    public CustomizationBindingBase(CustomizationBindingBase peerBinding) {
        this.allowNull = peerBinding.isNillable();
        this.adapterBinder = peerBinding.getAdapterBinding();
        this.serializerBinder = peerBinding.getSerializerBinding();
        this.deserializerBinder = peerBinding.getDeserializerBinding();
    }

    /**
     * Returns true if <i>nillable</i> customization is present.
     *
     * @return True if <i>nillable</i> customization is present.
     */
    public boolean isNillable() {
        return allowNull;
    }

    /**
     * Adapter wrapper class with resolved generic information.
     *
     * @return components wrapper
     */
    public AdapterBinding getAdapterBinding() {
        return adapterBinder;
    }

    /**
     * Serializer wrapper with resolved generic info.
     *
     * @return serializer wrapper
     */
    public SerializerBinding getSerializerBinding() {
        return serializerBinder;
    }

    /**
     * Deserializer wrapper with resolved generic info.
     *
     * @return deserializer wrapper
     */
    public DeserializerBinding getDeserializerBinding() {
        return deserializerBinder;
    }


}

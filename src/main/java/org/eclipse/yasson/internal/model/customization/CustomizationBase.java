package org.eclipse.yasson.internal.model.customization;

import org.eclipse.yasson.internal.components.DeserializerBinder;
import org.eclipse.yasson.internal.components.SerializerBindingEntry;
import org.eclipse.yasson.internal.components.TypeAdapterBinding;

/**
 * Common properties of {@link ClassCustomization} and {@link PropertySerializationConfig}.
 */
abstract class CustomizationBase implements Customization, ComponentBindingCustomization {

    private final TypeAdapterBinding adapterBinding;

    private final SerializerBindingEntry serializerBinding;

    private final DeserializerBinder deserializerBinding;

    private final boolean nillable;


    /**
     * Serializer wrapper with resolved generic info.
     *
     * @return serializer wrapper
     */
    public SerializerBindingEntry getSerializerBinding() {
        return serializerBinding;
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
     * Deserializer wrapper with resolved generic info.
     *
     * @return deserializer wrapper
     */
    public DeserializerBinder getDeserializerBinding() {
        return deserializerBinding;
    }

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
     * Adapter wrapper class with resolved generic information.
     *
     * @return components wrapper
     */
    public TypeAdapterBinding getAdapterBinding() {
        return adapterBinding;
    }

}

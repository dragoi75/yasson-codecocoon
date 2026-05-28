/**
 * ****************************************************************************
 *  Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved.
 *  Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 *  which accompanies this distribution.
 *  The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *  and the Eclipse Distribution License is available at
 *  http://www.eclipse.org/org/documents/edl-v10.php.
 *  <p>
 *  Contributors:
 *      Dmitry Kornilov - initial implementation
 * ****************************************************************************
 */
package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.model.ClassDescriptor;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.Messages;
import org.eclipse.yasson.internal.serializer.ConfigurableValueTypeSerializer;
import org.eclipse.yasson.internal.serializer.ContainerSerializerProvider;
import org.eclipse.yasson.internal.serializer.DefaultSerializers;
import org.eclipse.yasson.internal.serializer.TypeSerializerBuilder;
import org.eclipse.yasson.internal.model.JsonbPropertyInfo;
import javax.json.bind.JsonbException;
import javax.json.bind.serializer.JsonbSerializer;
import javax.json.bind.serializer.SerializationContext;
import javax.json.stream.JsonGenerationException;
import javax.json.stream.JsonGenerator;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * JSONB marshaller. Created each time marshalling operation called.
 *
 * @author Dmitry Kornilov
 * @author Roman Grigoriadi
 */
public class ObjectMarshaller extends ObjectProcessingContext implements SerializationContext {

    private static final Logger LOG = Logger.getLogger(ObjectMarshaller.class.getName());

    private final Type resolvedClass;

    /**
     * Serializes root element.
     *
     * @param <T> Root type
     * @param sourceObject Root.
     * @param outputWriter JSON generator.
     */
    @SuppressWarnings("unchecked")
    public <T> void serializeRootObject(T sourceObject, JsonGenerator outputWriter) {
        if (null == sourceObject) {
            getJsonbContext().getConfigProperties().getNullSerializer().serialize(null, outputWriter, this);
            return;
        }
        final JsonbSerializer<T> primarySerializer = (JsonbSerializer<T>) getRootSerializer(sourceObject.getClass());
        if (jsonbContext.getConfigProperties().isStrictIJson() && primarySerializer instanceof ConfigurableValueTypeSerializer) {
            throw new JsonbException(Messages.getMessage(MessageKeyConstants.IJSON_ENABLED_SINGLE_VALUE));
        }
        primarySerializer.serialize(sourceObject, outputWriter, this);
    }

    @Override
    public <T> void serialize(T value, JsonGenerator outputWriter) {
        Objects.requireNonNull(value);
        serializeRootObject(value, outputWriter);
    }

    @Override
    public <T> void serialize(String propertyName, T value, JsonGenerator outputWriter) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(value);
        outputWriter.writeKey(propertyName);
        serializeRootObject(value, outputWriter);
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     * Leaves generator open for further interaction after completion.
     *
     * @param value object to marshall
     * @param outWriter generator to use
     */
    public void marshalWithoutClose(Object value, JsonGenerator outWriter) {
        marshal(value, outWriter, false);
    }

    private JsonbSerializer<?> getRootSerializer(Class<?> targetClass) {
        final ContainerSerializerProvider containerProvider = getMappingContext().getSerializerProvider(targetClass);
        if (null != containerProvider) {
            return containerProvider.provideSerializer(new JsonbPropertyInfo().withRuntimeType(resolvedClass));
        }
        TypeSerializerBuilder typeBuilder = new TypeSerializerBuilder(jsonbContext).setObjectClass(targetClass).setType(resolvedClass);
        if (!DefaultSerializers.getInstance().isKnownType(targetClass)) {
            ClassDescriptor descriptor = getMappingContext().getOrCreateClassModel(targetClass);
            typeBuilder.setCustomization(descriptor.getCustomization());
        }
        return typeBuilder.buildSerializer();
    }

    /**
     * Creates Marshaller for generation to String.
     *
     * @param marshallingContext Current context.
     * @param baseClass Type of root object.
     */
    public ObjectMarshaller(JsonbContext marshallingContext, Type baseClass) {
        super(marshallingContext);
        this.resolvedClass = baseClass;
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     *
     * @param value object to marshall
     * @param outWriter generator to use
     * @param shouldTerminate if generator should be closed
     */
    public void marshal(Object value, JsonGenerator outWriter, boolean shouldTerminate) {
        try {
            serializeRootObject(value, outWriter);
        } catch (JsonbException bindingException) {
            LOG.severe(bindingException.getMessage());
            throw bindingException;
        } catch (Exception bindingException) {
            LOG.severe(bindingException.getMessage());
            throw new JsonbException(Messages.getMessage(MessageKeyConstants.INTERNAL_ERROR, bindingException.getMessage()), bindingException);
        } finally {
            try {
                if (shouldTerminate) {
                    outWriter.close();
                }
            } catch (JsonGenerationException generationException) {
                LOG.severe(generationException.getMessage());
            }
        }
    }

    /**
     * Creates Marshaller for generation to String.
     *
     * @param marshallingContext Current context.
     */
    public ObjectMarshaller(JsonbContext marshallingContext) {
        super(marshallingContext);
        this.resolvedClass = null;
    }

    /**
     * Marshals given object to provided Writer or OutputStream.
     * Closes the generator on completion.
     *
     * @param value object to marshall
     * @param outWriter generator to use
     */
    public void marshal(Object value, JsonGenerator outWriter) {
        marshal(value, outWriter, true);
    }

}

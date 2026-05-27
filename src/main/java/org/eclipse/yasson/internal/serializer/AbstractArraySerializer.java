/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal.serializer;

import java.util.Base64;
import java.util.Map;
import java.util.function.Function;

import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.stream.JsonGenerator;

import org.eclipse.yasson.internal.JsonBindingContext;
import org.eclipse.yasson.internal.SerializationContextImpl;
import org.eclipse.yasson.internal.properties.MessageConstants;
import org.eclipse.yasson.internal.properties.MessageProvider;

/**
 * Array container serializer.
 */
abstract class AbstractArraySerializer implements ModelMarshaller {

    private static final Map<Class<?>, Function<ModelMarshaller, AbstractArraySerializer>> ARRAY_SERIALIZER_FACTORIES;

    static {
        ARRAY_SERIALIZER_FACTORIES = Map.of(boolean[].class, BooleanArrayEncoder::new,
                                   byte[].class, BinaryArraySerializer::new,
                                   char[].class, CharArraySerializer::new,
                                   double[].class, PrimitiveDoubleArraySerializer::new,
                                   float[].class, FloatArrayEncoder::new,
                                   int[].class, IntArraySerializer::new,
                                   long[].class, LongArrayEncoder::new,
                                   short[].class, ShortArraySerializerImpl::new);
    }

    private final ModelMarshaller modelMarshaller;

    private static final class BinaryArraySerializer extends AbstractArraySerializer {

        @Override
        public void serializeElements(Object element, JsonGenerator jsonGenerator, SerializationContextImpl serializationContext) {
            byte[] bytes = (byte[]) element;
            for (byte byteValue : bytes) {
                getValueSerializer().marshal(byteValue, jsonGenerator, serializationContext);
            }
        }

        BinaryArraySerializer(ModelMarshaller modelMarshaller) {
            super(modelMarshaller);
        }

    }

    private static final class Base64ByteArrayEncoder implements ModelMarshaller {

        private final Base64.Encoder base64Encoder;

        private Base64.Encoder getEncoder(String encodingStrategy) {
            switch (encodingStrategy) {
            case BinaryDataStrategy.BASE_64:
                return Base64.getEncoder();
            case BinaryDataStrategy.BASE_64_URL:
                return Base64.getUrlEncoder();
            default:
                throw new JsonbException(MessageProvider.getMessage(MessageConstants.INTERNAL_ERROR, "Invalid strategy: " + encodingStrategy));
            }
        }

        @Override
        public void marshal(Object element, JsonGenerator jsonGenerator, SerializationContextImpl context) {
            byte[] bytes = (byte[]) element;
            jsonGenerator.write(base64Encoder.encodeToString(bytes));
        }

        Base64ByteArrayEncoder(String encodingStrategy) {
            this.base64Encoder = getEncoder(encodingStrategy);
        }

    }

    private static final class ShortArraySerializerImpl extends AbstractArraySerializer {

        @Override
        public void serializeElements(Object element, JsonGenerator jsonGenerator, SerializationContextImpl serializationContext) {
            short[] bytes = (short[]) element;
            for (short shortValue : bytes) {
                getValueSerializer().marshal(shortValue, jsonGenerator, serializationContext);
            }
        }

        ShortArraySerializerImpl(ModelMarshaller modelMarshaller) {
            super(modelMarshaller);
        }

    }

    private static final class IntArraySerializer extends AbstractArraySerializer {

        @Override
        public void serializeElements(Object element, JsonGenerator jsonGenerator, SerializationContextImpl serializationContext) {
            int[] bytes = (int[]) element;
            for (int intValue : bytes) {
                getValueSerializer().marshal(intValue, jsonGenerator, serializationContext);
            }
        }

        IntArraySerializer(ModelMarshaller modelMarshaller) {
            super(modelMarshaller);
        }

    }

    private static final class LongArrayEncoder extends AbstractArraySerializer {

        @Override
        public void serializeElements(Object element, JsonGenerator jsonGenerator, SerializationContextImpl serializationContext) {
            long[] bytes = (long[]) element;
            for (long item : bytes) {
                getValueSerializer().marshal(item, jsonGenerator, serializationContext);
            }
        }

        LongArrayEncoder(ModelMarshaller modelMarshaller) {
            super(modelMarshaller);
        }

    }

    private static final class FloatArrayEncoder extends AbstractArraySerializer {

        @Override
        public void serializeElements(Object element, JsonGenerator jsonGenerator, SerializationContextImpl serializationContext) {
            float[] bytes = (float[]) element;
            for (float item : bytes) {
                getValueSerializer().marshal(item, jsonGenerator, serializationContext);
            }
        }

        FloatArrayEncoder(ModelMarshaller modelMarshaller) {
            super(modelMarshaller);
        }

    }

    private static final class PrimitiveDoubleArraySerializer extends AbstractArraySerializer {

        @Override
        public void serializeElements(Object element, JsonGenerator jsonGenerator, SerializationContextImpl serializationContext) {
            double[] bytes = (double[]) element;
            for (double item : bytes) {
                getValueSerializer().marshal(item, jsonGenerator, serializationContext);
            }
        }

        PrimitiveDoubleArraySerializer(ModelMarshaller modelMarshaller) {
            super(modelMarshaller);
        }

    }

    private static final class BooleanArrayEncoder extends AbstractArraySerializer {

        @Override
        public void serializeElements(Object element, JsonGenerator jsonGenerator, SerializationContextImpl serializationContext) {
            boolean[] bytes = (boolean[]) element;
            for (boolean byteValue : bytes) {
                getValueSerializer().marshal(byteValue, jsonGenerator, serializationContext);
            }
        }

        BooleanArrayEncoder(ModelMarshaller modelMarshaller) {
            super(modelMarshaller);
        }

    }

    private static final class CharArraySerializer extends AbstractArraySerializer {

        @Override
        public void serializeElements(Object element, JsonGenerator jsonGenerator, SerializationContextImpl serializationContext) {
            char[] bytes = (char[]) element;
            for (char charValue : bytes) {
                getValueSerializer().marshal(charValue, jsonGenerator, serializationContext);
            }
        }

        CharArraySerializer(ModelMarshaller modelMarshaller) {
            super(modelMarshaller);
        }

    }

    private static final class ObjectArrayEncoder extends AbstractArraySerializer {

        @Override
        public void serializeElements(Object element, JsonGenerator jsonGenerator, SerializationContextImpl serializationContext) {
            Object[] bytes = (Object[]) element;
            for (Object obj : bytes) {
                getValueSerializer().marshal(obj, jsonGenerator, serializationContext);
            }
        }

        ObjectArrayEncoder(ModelMarshaller modelMarshaller) {
            super(modelMarshaller);
        }

    }

    @Override
    public void marshal(Object element, JsonGenerator jsonGenerator, SerializationContextImpl serializationContext) {
        jsonGenerator.writeStartArray();
        serializeElements(element, jsonGenerator, serializationContext);
        jsonGenerator.writeEnd();
    }

    protected ModelMarshaller getValueSerializer() {
        return modelMarshaller;
    }

    protected AbstractArraySerializer(ModelMarshaller modelMarshaller) {
        this.modelMarshaller = modelMarshaller;
    }

    abstract void serializeElements(Object value, JsonGenerator generator, SerializationContextImpl context);

    public static ModelMarshaller createEncoder(Class<?> componentType,
                                                JsonBindingContext bindingContext,
                                                ModelMarshaller modelMarshaller) {
        String binaryStrategy = bindingContext.getConfigProperties().getBinaryDataStrategy();
        if (byte[].class.equals(componentType) && !binaryStrategy.equals(BinaryDataStrategy.BYTE)) {
            return new Base64ByteArrayEncoder(binaryStrategy);
        }
        if (ARRAY_SERIALIZER_FACTORIES.containsKey(componentType)) {
            return ARRAY_SERIALIZER_FACTORIES.get(componentType).apply(modelMarshaller);
        }
        return new ObjectArrayEncoder(modelMarshaller);
    }

}

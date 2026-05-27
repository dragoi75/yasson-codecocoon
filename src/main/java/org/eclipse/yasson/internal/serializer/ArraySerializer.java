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

import org.eclipse.yasson.internal.DefaultSerializationContext;
import org.eclipse.yasson.internal.JsonbContext;
import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

/**
 * Array container serializer.
 */
abstract class ArraySerializer implements ModelMarshaller {

    private static final Map<Class<?>, Function<ModelMarshaller, ArraySerializer>> ARRAY_SERIALIZERS;

    static {
        ARRAY_SERIALIZERS = Map.of(boolean[].class, BooleanArraySerializer::new,
                                   byte[].class, ByteArraySerializer::new,
                                   char[].class, CharacterArraySerializer::new,
                                   double[].class, DoubleArraySerializer::new,
                                   float[].class, FloatArraySerializer::new,
                                   int[].class, IntegerArraySerializer::new,
                                   long[].class, LongArraySerializer::new,
                                   short[].class, ShortArraySerializer::new);
    }

    private final ModelMarshaller valueSerializer;

    private static final class ByteArraySerializer extends ArraySerializer {

        @Override
        public void serializeArray(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            byte[] array = (byte[]) value;
            for (byte b : array) {
                getValueSerializer().marshal(b, generator, context);
            }
        }

        ByteArraySerializer(ModelMarshaller valueSerializer) {
            super(valueSerializer);
        }

    }

    private static final class Base64ByteArraySerializer implements ModelMarshaller {

        private final Base64.Encoder encoder;

        private Base64.Encoder getEncoder(String strategy) {
            switch (strategy) {
            case BinaryDataStrategy.BASE_64:
                return Base64.getEncoder();
            case BinaryDataStrategy.BASE_64_URL:
                return Base64.getUrlEncoder();
            default:
                throw new JsonbException(Messages.getMessage(MessageKeys.INTERNAL_ERROR, "Invalid strategy: " + strategy));
            }
        }

        @Override
        public void marshal(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            byte[] array = (byte[]) value;
            generator.write(encoder.encodeToString(array));
        }

        Base64ByteArraySerializer(String strategy) {
            this.encoder = getEncoder(strategy);
        }

    }

    private static final class ShortArraySerializer extends ArraySerializer {

        @Override
        public void serializeArray(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            short[] array = (short[]) value;
            for (short s : array) {
                getValueSerializer().marshal(s, generator, context);
            }
        }

        ShortArraySerializer(ModelMarshaller valueSerializer) {
            super(valueSerializer);
        }

    }

    private static final class IntegerArraySerializer extends ArraySerializer {

        @Override
        public void serializeArray(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            int[] array = (int[]) value;
            for (int i : array) {
                getValueSerializer().marshal(i, generator, context);
            }
        }

        IntegerArraySerializer(ModelMarshaller valueSerializer) {
            super(valueSerializer);
        }

    }

    private static final class LongArraySerializer extends ArraySerializer {

        @Override
        public void serializeArray(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            long[] array = (long[]) value;
            for (long l : array) {
                getValueSerializer().marshal(l, generator, context);
            }
        }

        LongArraySerializer(ModelMarshaller valueSerializer) {
            super(valueSerializer);
        }

    }

    private static final class FloatArraySerializer extends ArraySerializer {

        @Override
        public void serializeArray(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            float[] array = (float[]) value;
            for (float f : array) {
                getValueSerializer().marshal(f, generator, context);
            }
        }

        FloatArraySerializer(ModelMarshaller valueSerializer) {
            super(valueSerializer);
        }

    }

    private static final class DoubleArraySerializer extends ArraySerializer {

        @Override
        public void serializeArray(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            double[] array = (double[]) value;
            for (double d : array) {
                getValueSerializer().marshal(d, generator, context);
            }
        }

        DoubleArraySerializer(ModelMarshaller valueSerializer) {
            super(valueSerializer);
        }

    }

    private static final class BooleanArraySerializer extends ArraySerializer {

        @Override
        public void serializeArray(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            boolean[] array = (boolean[]) value;
            for (boolean b : array) {
                getValueSerializer().marshal(b, generator, context);
            }
        }

        BooleanArraySerializer(ModelMarshaller valueSerializer) {
            super(valueSerializer);
        }

    }

    private static final class CharacterArraySerializer extends ArraySerializer {

        @Override
        public void serializeArray(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            char[] array = (char[]) value;
            for (char c : array) {
                getValueSerializer().marshal(c, generator, context);
            }
        }

        CharacterArraySerializer(ModelMarshaller valueSerializer) {
            super(valueSerializer);
        }

    }

    private static final class ObjectArraySerializer extends ArraySerializer {

        @Override
        public void serializeArray(Object value, JsonGenerator generator, DefaultSerializationContext context) {
            Object[] array = (Object[]) value;
            for (Object o : array) {
                getValueSerializer().marshal(o, generator, context);
            }
        }

        ObjectArraySerializer(ModelMarshaller valueSerializer) {
            super(valueSerializer);
        }

    }

    abstract void serializeArray(Object value, JsonGenerator generator, DefaultSerializationContext context);

    @Override
    public void marshal(Object value, JsonGenerator generator, DefaultSerializationContext context) {
        generator.writeStartArray();
        serializeArray(value, generator, context);
        generator.writeEnd();
    }

    protected ModelMarshaller getValueSerializer() {
        return valueSerializer;
    }

    public static ModelMarshaller create(Class<?> arrayType,
                                         JsonbContext jsonbContext,
                                         ModelMarshaller modelSerializer) {
        String binaryDataStrategy = jsonbContext.getConfigProperties().getBinaryDataStrategy();
        if (byte[].class.equals(arrayType) && !binaryDataStrategy.equals(BinaryDataStrategy.BYTE)) {
            return new Base64ByteArraySerializer(binaryDataStrategy);
        }
        if (ARRAY_SERIALIZERS.containsKey(arrayType)) {
            return ARRAY_SERIALIZERS.get(arrayType).apply(modelSerializer);
        }
        return new ObjectArraySerializer(modelSerializer);
    }

    protected ArraySerializer(ModelMarshaller valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

}

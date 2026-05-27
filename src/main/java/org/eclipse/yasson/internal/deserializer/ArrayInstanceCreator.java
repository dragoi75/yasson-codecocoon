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
package org.eclipse.yasson.internal.deserializer;

import java.lang.reflect.Array;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.stream.JsonParser;
import org.eclipse.yasson.internal.DeserializationContextImplementation;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Creator of the array instance based upon the array type.
 */
abstract class ArrayInstanceCreator implements ModelUnmarshaller<JsonParser> {

    private static final Map<Class<?>, Function<ModelUnmarshaller<JsonParser>, ArrayInstanceCreator>> CACHE;

    static {
        CACHE = Map.of(boolean[].class, BooleanArrayCreator::new, byte[].class, ByteArrayCreator::new, char[].class, CharArrayCreator::new, double[].class, DoubleArrayCreator::new, float[].class, FloatArrayCreator::new, int[].class, IntegerArrayCreator::new, long[].class, LongArrayCreator::new, short[].class, ShortArrayCreator::new);
    }

    private final ModelUnmarshaller<JsonParser> delegate;

    private static final class IntegerArrayCreator extends ArrayInstanceCreator {

        @Override
        protected Object resolveArrayInstance(Collection<Object> collection) {
            int[] intArray = new int[collection.size()];
            int i = 0;
            for (Object obj : collection) {
                intArray[i] = (int) obj;
                i += 1;
            }
            return intArray;
        }

        private IntegerArrayCreator(ModelUnmarshaller<JsonParser> delegate) {
            super(delegate);
        }

    }

    private static final class ByteArrayCreator extends ArrayInstanceCreator {

        @Override
        protected Object resolveArrayInstance(Collection<Object> collection) {
            byte[] byteArray = new byte[collection.size()];
            int i = 0;
            for (Object obj : collection) {
                byteArray[i] = (byte) obj;
                i += 1;
            }
            return byteArray;
        }

        private ByteArrayCreator(ModelUnmarshaller<JsonParser> delegate) {
            super(delegate);
        }

    }

    private static final class ShortArrayCreator extends ArrayInstanceCreator {

        @Override
        protected Object resolveArrayInstance(Collection<Object> collection) {
            short[] shortArray = new short[collection.size()];
            int i = 0;
            for (Object obj : collection) {
                shortArray[i] = (short) obj;
                i += 1;
            }
            return shortArray;
        }

        private ShortArrayCreator(ModelUnmarshaller<JsonParser> delegate) {
            super(delegate);
        }

    }

    private static final class LongArrayCreator extends ArrayInstanceCreator {

        @Override
        protected Object resolveArrayInstance(Collection<Object> collection) {
            long[] longArray = new long[collection.size()];
            int i = 0;
            for (Object obj : collection) {
                longArray[i] = (long) obj;
                i += 1;
            }
            return longArray;
        }

        private LongArrayCreator(ModelUnmarshaller<JsonParser> delegate) {
            super(delegate);
        }

    }

    private static final class FloatArrayCreator extends ArrayInstanceCreator {

        @Override
        protected Object resolveArrayInstance(Collection<Object> collection) {
            float[] floatArray = new float[collection.size()];
            int i = 0;
            for (Object obj : collection) {
                floatArray[i] = (float) obj;
                i += 1;
            }
            return floatArray;
        }

        private FloatArrayCreator(ModelUnmarshaller<JsonParser> delegate) {
            super(delegate);
        }

    }

    private static final class DoubleArrayCreator extends ArrayInstanceCreator {

        @Override
        protected Object resolveArrayInstance(Collection<Object> collection) {
            double[] doubleArray = new double[collection.size()];
            int i = 0;
            for (Object obj : collection) {
                doubleArray[i] = (double) obj;
                i += 1;
            }
            return doubleArray;
        }

        private DoubleArrayCreator(ModelUnmarshaller<JsonParser> delegate) {
            super(delegate);
        }

    }

    private static final class BooleanArrayCreator extends ArrayInstanceCreator {

        @Override
        protected Object resolveArrayInstance(Collection<Object> collection) {
            boolean[] booleanArray = new boolean[collection.size()];
            int i = 0;
            for (Object obj : collection) {
                booleanArray[i] = (boolean) obj;
                i += 1;
            }
            return booleanArray;
        }

        private BooleanArrayCreator(ModelUnmarshaller<JsonParser> delegate) {
            super(delegate);
        }

    }

    private static final class CharArrayCreator extends ArrayInstanceCreator {

        @Override
        protected Object resolveArrayInstance(Collection<Object> collection) {
            char[] charArray = new char[collection.size()];
            int i = 0;
            for (Object obj : collection) {
                charArray[i] = (char) obj;
                i += 1;
            }
            return charArray;
        }

        private CharArrayCreator(ModelUnmarshaller<JsonParser> delegate) {
            super(delegate);
        }

    }

    private static final class ObjectArrayCreator extends ArrayInstanceCreator {

        private final Class<?> componentClass;

        @Override
        protected Object resolveArrayInstance(Collection<Object> collection) {
            Object[] objectArray = (Object[]) Array.newInstance(componentClass, collection.size());
            int i = 0;
            for (Object obj : collection) {
                objectArray[i] = obj;
                i += 1;
            }
            return objectArray;
        }

        private ObjectArrayCreator(ModelUnmarshaller<JsonParser> delegate, Class<?> componentClass) {
            super(delegate);
            this.componentClass = componentClass;
        }

    }

    private static final class Base64ByteArray implements ModelUnmarshaller<JsonParser> {

        private final Base64.Decoder decoder;

        private final ModelUnmarshaller<JsonParser> delegate;

        @Override
        public Object unmarshal(JsonParser value, DeserializationContextImplementation context) {
            return decoder.decode((String) delegate.unmarshal(value, context));
        }

        public Base64.Decoder getDecoder(String strategy) {
            switch(strategy) {
                case BinaryDataStrategy.BASE_64:
                    return Base64.getDecoder();
                case BinaryDataStrategy.BASE_64_URL:
                    return Base64.getUrlDecoder();
                default:
                    throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.INTERNAL_ERROR, "Invalid strategy: " + strategy));
            }
        }

        private Base64ByteArray(String strategy, ModelUnmarshaller<JsonParser> delegate) {
            this.decoder = getDecoder(strategy);
            this.delegate = delegate;
        }

    }

    static ModelUnmarshaller<JsonParser> createBase64Deserializer(String strategy, ModelUnmarshaller<JsonParser> delegate) {
        return new Base64ByteArray(strategy, delegate);
    }

    protected abstract Object resolveArrayInstance(Collection<Object> collection);

    static ArrayInstanceCreator create(Class<?> arrayType, Class<?> componentClass, ModelUnmarshaller<JsonParser> delegate) {
        if (CACHE.containsKey(arrayType)) {
            return CACHE.get(arrayType).apply(delegate);
        }
        return new ObjectArrayCreator(delegate, componentClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object unmarshal(JsonParser value, DeserializationContextImplementation context) {
        Collection<Object> collection = (Collection<Object>) delegate.unmarshal(value, context);
        return resolveArrayInstance(collection);
    }

    private ArrayInstanceCreator(ModelUnmarshaller<JsonParser> delegate) {
        this.delegate = delegate;
    }

}

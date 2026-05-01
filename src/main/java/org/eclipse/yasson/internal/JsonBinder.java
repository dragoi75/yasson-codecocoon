/*******************************************************************************
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 * Roman Grigoriadi
 ******************************************************************************/
package org.eclipse.yasson.internal;

import org.eclipse.yasson.internal.properties.MessageKeys;
import org.eclipse.yasson.internal.properties.Messages;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbConfig;
import javax.json.bind.JsonbException;
import javax.json.spi.JsonProvider;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of Jsonb interface.
 *
 * @author Dmitry Kornilov
 */
public class JsonBinder implements Jsonb {

    private final JsonbContext bindingState;

    JsonBinder(JsonBindingBuilder bindingConfig) {
        this.bindingState = new JsonbContext(bindingConfig.getConfig(), bindingConfig.getProvider().orElseGet(JsonProvider::provider));
    }

    private <T> T deserializeJson(final Type targetKind, final JsonParser jsonReader, final JsonbUnmarshaller dataMapper) {
        return dataMapper.deserialize(targetKind, jsonReader);
    }

    @Override
    public <T> T fromJson(String jsonText, Class<T> targetKind) throws JsonbException {
        final JsonParser jsonReader = new JsonbRiParser(bindingState.getJsonProvider().createParser(new StringReader(jsonText)));
        final JsonbUnmarshaller dataMapper = new JsonbUnmarshaller(bindingState);
        return deserializeJson(targetKind, jsonReader, dataMapper);
    }

    @Override
    public <T> T fromJson(String jsonText, Type targetKind) throws JsonbException {
        JsonParser jsonReader = new JsonbRiParser(bindingState.getJsonProvider().createParser(new StringReader(jsonText)));
        JsonbUnmarshaller dataMapper = new JsonbUnmarshaller(bindingState);
        return deserializeJson(targetKind, jsonReader, dataMapper);
    }

    @Override
    public <T> T fromJson(Reader charSource, Class<T> targetKind) throws JsonbException {
        JsonParser jsonReader = new JsonbRiParser(bindingState.getJsonProvider().createParser(charSource));
        JsonbUnmarshaller dataMapper = new JsonbUnmarshaller(bindingState);
        return deserializeJson(targetKind, jsonReader, dataMapper);
    }

    @Override
    public <T> T fromJson(Reader charSource, Type targetKind) throws JsonbException {
        JsonParser jsonReader = new JsonbRiParser(bindingState.getJsonProvider().createParser(charSource));
        JsonbUnmarshaller dataMapper = new JsonbUnmarshaller(bindingState);
        return deserializeJson(targetKind, jsonReader, dataMapper);
    }

    @Override
    public <T> T fromJson(InputStream byteSource, Class<T> targetClass) throws JsonbException {
        JsonbUnmarshaller dataMapper = new JsonbUnmarshaller(bindingState);
        return deserializeJson(targetClass, createInputStreamParser(byteSource), dataMapper);
    }

    @Override
    public <T> T fromJson(InputStream byteSource, Type targetKind) throws JsonbException {
        JsonbUnmarshaller dataMapper = new JsonbUnmarshaller(bindingState);
        return deserializeJson(targetKind, createInputStreamParser(byteSource), dataMapper);
    }

    private JsonParser createInputStreamParser(InputStream byteSource) {
        return new JsonbRiParser(bindingState.getJsonProvider().createParserFactory(buildJsonpProperties(bindingState.getConfig()))
                .createParser(byteSource,
                        Charset.forName((String) bindingState.getConfig().getProperty(JsonbConfig.ENCODING).orElse("UTF-8"))));
    }

    @Override
    public String toJson(Object value) throws JsonbException {
        StringWriter stringBuffer = new StringWriter();
        final JsonGenerator jsonEmitter = createGeneratorForWriter(stringBuffer);
        new Serializer(bindingState).marshal(value, jsonEmitter);
        return stringBuffer.toString();
    }

    @Override
    public String toJson(Object value, Type targetKind) throws JsonbException {
        StringWriter stringBuffer = new StringWriter();
        final JsonGenerator jsonEmitter = createGeneratorForWriter(stringBuffer);
        new Serializer(bindingState, targetKind).marshal(value, jsonEmitter);
        return stringBuffer.toString();
    }

    @Override
    public void toJson(Object value, Writer stringBuffer) throws JsonbException {
        final Serializer serializerImpl = new Serializer(bindingState);
        serializerImpl.marshal(value, createGeneratorForWriter(stringBuffer));
    }

    @Override
    public void toJson(Object value, Type targetKind, Writer stringBuffer) throws JsonbException {
        final Serializer serializerImpl = new Serializer(bindingState, targetKind);
        serializerImpl.marshal(value, createGeneratorForWriter(stringBuffer));
    }

    private JsonGenerator createGeneratorForWriter(Writer stringBuffer) {
        Map<String, ?> factoryProps = buildJsonpProperties(bindingState.getConfig());
        if (factoryProps.isEmpty()) {
            return bindingState.getJsonProvider().createGenerator(stringBuffer);
        }
        return bindingState.getJsonProvider().createGeneratorFactory(factoryProps).createGenerator(stringBuffer);
    }

    @Override
    public void toJson(Object value, OutputStream byteSource) throws JsonbException {
        final Serializer serializerImpl = new Serializer(bindingState);
        serializerImpl.marshal(value, createStreamGenerator(byteSource));
    }

    @Override
    public void toJson(Object value, Type targetKind, OutputStream byteSource) throws JsonbException {
        final Serializer serializerImpl = new Serializer(bindingState, targetKind);
        serializerImpl.marshal(value, createStreamGenerator(byteSource));
    }

    private JsonGenerator createStreamGenerator(OutputStream byteSource) {
        Map<String, ?> factoryProps = buildJsonpProperties(bindingState.getConfig());
        final String charset = (String) bindingState.getConfig().getProperty(JsonbConfig.ENCODING).orElse("UTF-8");
        return bindingState.getJsonProvider().createGeneratorFactory(factoryProps).createGenerator(byteSource, Charset.forName(charset));
    }

    @Override
    public void close() throws Exception {
        bindingState.getComponentInstanceCreator().close();
    }

    /**
     * Propagates properties from JsonbConfig to JSONP generator / parser factories.
     *
     * @param bindingConfig jsonb config
     * @return properties for JSONP generator / parser
     */
    protected Map<String, ?> buildJsonpProperties(JsonbConfig bindingConfig) {
        //JSONP 1.0 actually ignores the value, just checks the key is present. Only set if JsonbConfig.FORMATTING is true.
        final Optional<Object> optionalProp = bindingConfig.getProperty(JsonbConfig.FORMATTING);
        final Map<String, Object> factoryProps = new HashMap<>();
        if (optionalProp.isPresent()) {
            final Object item = optionalProp.get();
            if (!(item instanceof Boolean)) {
                throw new JsonbException(Messages.getMessage(MessageKeys.JSONB_CONFIG_FORMATTING_ILLEGAL_VALUE));
            }
            if ((Boolean) item) {
                factoryProps.put(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
            }
            return factoryProps;
        }
        return factoryProps;
    }
}

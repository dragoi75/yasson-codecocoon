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

import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

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
public class JsonConverter implements Jsonb {

    private final JsonbConfigurationContext bindingConfig;

    private JsonGenerator streamGenerator(OutputStream inputSource) {
        Map<String, ?> configMap = createJsonpProperties(bindingConfig.getConfig());
        final String charset = (String) bindingConfig.getConfig().getProperty(JsonbConfig.ENCODING).orElse("UTF-8");
        return bindingConfig.getJsonProvider().createGeneratorFactory(configMap).createGenerator(inputSource, Charset.forName(charset));
    }

    @Override
    public void toJson(Object value, Writer stringBuffer) throws JsonbException {
        final ObjectMarshaller objectSerializer = new ObjectMarshaller(bindingConfig);
        objectSerializer.marshal(value, writerGenerator(stringBuffer));
    }

    @Override
    public <T> T fromJson(InputStream inputSource, Class<T> targetClass) throws JsonbException {
        JsonUnmarshaller jsonDeserializer = new JsonUnmarshaller(bindingConfig);
        return deserialize(targetClass, inputStreamParser(inputSource), jsonDeserializer);
    }

    private JsonGenerator writerGenerator(Writer stringBuffer) {
        Map<String, ?> configMap = createJsonpProperties(bindingConfig.getConfig());
        if (configMap.isEmpty()) {
            return bindingConfig.getJsonProvider().createGenerator(stringBuffer);
        }
        return bindingConfig.getJsonProvider().createGeneratorFactory(configMap).createGenerator(stringBuffer);
    }

    @Override
    public String toJson(Object value, Type targetDescriptor) throws JsonbException {
        StringWriter stringBuffer = new StringWriter();
        final JsonGenerator jsonEmitter = writerGenerator(stringBuffer);
        new ObjectMarshaller(bindingConfig, targetDescriptor).marshal(value, jsonEmitter);
        return stringBuffer.toString();
    }

    @Override
    public <T> T fromJson(String jsonText, Class<T> targetDescriptor) throws JsonbException {
        final JsonParser tokenReader = new JsonbRiStreamParser(bindingConfig.getJsonProvider().createParser(new StringReader(jsonText)));
        final JsonUnmarshaller jsonDeserializer = new JsonUnmarshaller(bindingConfig);
        return deserialize(targetDescriptor, tokenReader, jsonDeserializer);
    }

    @Override
    public void toJson(Object value, OutputStream inputSource) throws JsonbException {
        final ObjectMarshaller objectSerializer = new ObjectMarshaller(bindingConfig);
        objectSerializer.marshal(value, streamGenerator(inputSource));
    }

    @Override
    public void toJson(Object value, Type targetDescriptor, Writer stringBuffer) throws JsonbException {
        final ObjectMarshaller objectSerializer = new ObjectMarshaller(bindingConfig, targetDescriptor);
        objectSerializer.marshal(value, writerGenerator(stringBuffer));
    }

    @Override
    public String toJson(Object value) throws JsonbException {
        StringWriter stringBuffer = new StringWriter();
        final JsonGenerator jsonEmitter = writerGenerator(stringBuffer);
        new ObjectMarshaller(bindingConfig).marshal(value, jsonEmitter);
        return stringBuffer.toString();
    }

    @Override
    public <T> T fromJson(Reader charSource, Type targetDescriptor) throws JsonbException {
        JsonParser tokenReader = new JsonbRiStreamParser(bindingConfig.getJsonProvider().createParser(charSource));
        JsonUnmarshaller jsonDeserializer = new JsonUnmarshaller(bindingConfig);
        return deserialize(targetDescriptor, tokenReader, jsonDeserializer);
    }

    @Override
    public void close() throws Exception {
        bindingConfig.getComponentInstanceCreator().close();
    }

    @Override
    public <T> T fromJson(InputStream inputSource, Type targetDescriptor) throws JsonbException {
        JsonUnmarshaller jsonDeserializer = new JsonUnmarshaller(bindingConfig);
        return deserialize(targetDescriptor, inputStreamParser(inputSource), jsonDeserializer);
    }

    private JsonParser inputStreamParser(InputStream inputSource) {
        return new JsonbRiStreamParser(bindingConfig.getJsonProvider().createParserFactory(createJsonpProperties(bindingConfig.getConfig()))
                .createParser(inputSource,
                        Charset.forName((String) bindingConfig.getConfig().getProperty(JsonbConfig.ENCODING).orElse("UTF-8"))));
    }

    @Override
    public void toJson(Object value, Type targetDescriptor, OutputStream inputSource) throws JsonbException {
        final ObjectMarshaller objectSerializer = new ObjectMarshaller(bindingConfig, targetDescriptor);
        objectSerializer.marshal(value, streamGenerator(inputSource));
    }

    JsonConverter(JsonBindingConfigurator configurator) {
        this.bindingConfig = new JsonbConfigurationContext(configurator.getConfig(), configurator.getProvider().orElseGet(JsonProvider::provider));
    }

    @Override
    public <T> T fromJson(String jsonText, Type targetDescriptor) throws JsonbException {
        JsonParser tokenReader = new JsonbRiStreamParser(bindingConfig.getJsonProvider().createParser(new StringReader(jsonText)));
        JsonUnmarshaller jsonDeserializer = new JsonUnmarshaller(bindingConfig);
        return deserialize(targetDescriptor, tokenReader, jsonDeserializer);
    }

    @Override
    public <T> T fromJson(Reader charSource, Class<T> targetDescriptor) throws JsonbException {
        JsonParser tokenReader = new JsonbRiStreamParser(bindingConfig.getJsonProvider().createParser(charSource));
        JsonUnmarshaller jsonDeserializer = new JsonUnmarshaller(bindingConfig);
        return deserialize(targetDescriptor, tokenReader, jsonDeserializer);
    }

    /**
     * Propagates properties from JsonbConfig to JSONP generator / parser factories.
     *
     * @param config jsonb config
     * @return properties for JSONP generator / parser
     */
    protected Map<String, ?> createJsonpProperties(JsonbConfig config) {
        //JSONP 1.0 actually ignores the value, just checks the key is present. Only set if JsonbConfig.FORMATTING is true.
        final Optional<Object> optionalValue = config.getProperty(JsonbConfig.FORMATTING);
        final Map<String, Object> configMap = new HashMap<>();
        if (optionalValue.isPresent()) {
            final Object entry = optionalValue.get();
            if (!(entry instanceof Boolean)) {
                throw new JsonbException(LocalizedMessages.getMessage(MessageKeyConstants.JSONB_CONFIG_FORMATTING_ILLEGAL_VALUE));
            }
            if ((Boolean) entry) {
                configMap.put(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
            }
            return configMap;
        }
        return configMap;
    }

    private <T> T deserialize(final Type targetDescriptor, final JsonParser tokenReader, final JsonUnmarshaller jsonDeserializer) {
        return jsonDeserializer.deserialize(targetDescriptor, tokenReader);
    }

}

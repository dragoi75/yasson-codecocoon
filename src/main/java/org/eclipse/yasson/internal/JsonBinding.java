/*
 * Copyright (c) 2016, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */

package org.eclipse.yasson.internal;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.json.JsonStructure;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

import org.eclipse.yasson.YassonJsonb;
import org.eclipse.yasson.internal.jsonstructure.JsonGeneratorToStructureAdapter;
import org.eclipse.yasson.internal.jsonstructure.JsonStructureToParserAdapter;
import org.eclipse.yasson.internal.properties.MessageBundle;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;

/**
 * Implementation of Jsonb interface.
 */
public class JsonBinding implements YassonJsonb {

    private final JsonbRuntimeContext jsonbContext;

    @Override
    public void toJson(Object object, OutputStream stream) throws JsonbException {
        final JsonbMarshaller marshaller = new JsonbMarshaller(jsonbContext);
        marshaller.writeJson(object, streamGenerator(stream));
    }

    JsonBinding(JsonBindingBuilder builder) {
        this.jsonbContext = new JsonbRuntimeContext(builder.getConfig(), builder.getProvider().orElseGet(JsonProvider::provider));
        Set<Class<?>> eagerInitClasses = this.jsonbContext.getConfigProperties().getEagerInitClasses();
        for (Class<?> eagerInitClass : eagerInitClasses) {
            // Eagerly initialize requested ClassModels and Serializers
            jsonbContext.getMappingContext().getOrCreateClassModel(eagerInitClass);
            new JsonbMarshaller(jsonbContext).getRootSerializer(eagerInitClass);
        }
    }

    /**
     * Propagates properties from JsonbConfig to JSONP generator / parser factories.
     *
     * @param jsonbConfig jsonb config
     * @return properties for JSONP generator / parser
     */
    protected Map<String, ?> createJsonpProperties(JsonbConfig jsonbConfig) {
        //JSONP 1.0 actually ignores the value, just checks the key is present. Only set if JsonbConfig.FORMATTING is true.
        final Optional<Object> property = jsonbConfig.getProperty(JsonbConfig.FORMATTING);
        final Map<String, Object> factoryProperties = new HashMap<>();
        if (property.isPresent()) {
            final Object value = property.get();
            if (!(value instanceof Boolean)) {
                throw new JsonbException(MessageBundle.getMessage(MessageKeyConstants.JSONB_CONFIG_FORMATTING_ILLEGAL_VALUE));
            }
            if ((Boolean) value) {
                factoryProperties.put(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
            }
            return factoryProperties;
        }
        return factoryProperties;
    }

    @Override
    public <T> T fromJsonStructure(JsonStructure jsonStructure, Type runtimeType) throws JsonbException {
        JsonParser parser = new JsonbStreamingParser(new JsonStructureToParserAdapter(jsonStructure));
        return deserialize(runtimeType, parser, new JsonbUnmarshaller(jsonbContext));
    }

    @Override
    public <T> T fromJson(String str, Class<T> type) throws JsonbException {
        final JsonParser parser = new JsonbStreamingParser(jsonbContext.getJsonProvider().createParser(new StringReader(str)));
        final JsonbUnmarshaller unmarshaller = new JsonbUnmarshaller(jsonbContext);
        return deserialize(type, parser, unmarshaller);
    }

    @Override
    public void toJson(Object object, Type type, OutputStream stream) throws JsonbException {
        final JsonbMarshaller marshaller = new JsonbMarshaller(jsonbContext, type);
        marshaller.writeJson(object, streamGenerator(stream));
    }

    @Override
    public JsonStructure toJsonStructure(Object object, Type runtimeType) throws JsonbException {
        JsonGeneratorToStructureAdapter structureGenerator = new JsonGeneratorToStructureAdapter(jsonbContext.getJsonProvider());
        final JsonbMarshaller marshaller = new JsonbMarshaller(jsonbContext, runtimeType);
        marshaller.writeJson(object, structureGenerator);
        return structureGenerator.getRootStructure();
    }

    private JsonGenerator streamGenerator(OutputStream stream) {
        Map<String, ?> factoryProperties = createJsonpProperties(jsonbContext.getConfig());
        final String encoding = (String) jsonbContext.getConfig().getProperty(JsonbConfig.ENCODING).orElse("UTF-8");
        return jsonbContext.getJsonProvider().createGeneratorFactory(factoryProperties)
                .createGenerator(stream, Charset.forName(encoding));
    }

    @Override
    public <T> T fromJson(JsonParser jsonParser, Class<T> type) throws JsonbException {
        JsonbUnmarshaller unmarshaller = new JsonbUnmarshaller(jsonbContext);
        return unmarshaller.deserialize(type, new JsonbStreamingParser(jsonParser));
    }

    @Override
    public <T> T fromJson(InputStream stream, Class<T> clazz) throws JsonbException {
        JsonbUnmarshaller unmarshaller = new JsonbUnmarshaller(jsonbContext);
        return deserialize(clazz, inputStreamParser(stream), unmarshaller);
    }

    @Override
    public <T> T fromJsonStructure(JsonStructure jsonStructure, Class<T> type) throws JsonbException {
        JsonParser parser = new JsonbStreamingParser(new JsonStructureToParserAdapter(jsonStructure));
        return deserialize(type, parser, new JsonbUnmarshaller(jsonbContext));
    }

    @Override
    public void toJson(Object object, Type type, Writer writer) throws JsonbException {
        final JsonbMarshaller marshaller = new JsonbMarshaller(jsonbContext, type);
        marshaller.marshallNoClose(object, writerGenerator(writer));
    }

    @Override
    public void close() throws Exception {
        jsonbContext.getComponentInstanceCreator().close();
    }

    @Override
    public <T> T fromJson(JsonParser jsonParser, Type runtimeType) throws JsonbException {
        JsonbUnmarshaller unmarshaller = new JsonbUnmarshaller(jsonbContext);
        return unmarshaller.deserialize(runtimeType, new JsonbStreamingParser(jsonParser));
    }

    @Override
    public void toJson(Object object, JsonGenerator jsonGenerator) throws JsonbException {
        final JsonbMarshaller marshaller = new JsonbMarshaller(jsonbContext);
        marshaller.marshallNoClose(object, jsonGenerator);
    }

    @Override
    public String toJson(Object object) throws JsonbException {
        StringWriter writer = new StringWriter();
        final JsonGenerator generator = writerGenerator(writer);
        new JsonbMarshaller(jsonbContext).writeJson(object, generator);
        return writer.toString();
    }

    @Override
    public JsonStructure toJsonStructure(Object object) throws JsonbException {
        JsonGeneratorToStructureAdapter structureGenerator = new JsonGeneratorToStructureAdapter(jsonbContext.getJsonProvider());
        final JsonbMarshaller marshaller = new JsonbMarshaller(jsonbContext);
        marshaller.writeJson(object, structureGenerator);
        return structureGenerator.getRootStructure();
    }

    @Override
    public void toJson(Object object, Type runtimeType, JsonGenerator jsonGenerator) throws JsonbException {
        final JsonbMarshaller marshaller = new JsonbMarshaller(jsonbContext, runtimeType);
        marshaller.marshallNoClose(object, jsonGenerator);
    }

    @Override
    public <T> T fromJson(Reader reader, Class<T> type) throws JsonbException {
        JsonParser parser = new JsonbStreamingParser(jsonbContext.getJsonProvider().createParser(reader));
        JsonbUnmarshaller unmarshaller = new JsonbUnmarshaller(jsonbContext);
        return deserialize(type, parser, unmarshaller);
    }

    @Override
    public void toJson(Object object, Writer writer) throws JsonbException {
        final JsonbMarshaller marshaller = new JsonbMarshaller(jsonbContext);
        marshaller.marshallNoClose(object, writerGenerator(writer));
    }

    @Override
    public String toJson(Object object, Type type) throws JsonbException {
        StringWriter writer = new StringWriter();
        final JsonGenerator generator = writerGenerator(writer);
        new JsonbMarshaller(jsonbContext, type).writeJson(object, generator);
        return writer.toString();
    }

    private <T> T deserialize(final Type type, final JsonParser parser, final JsonbUnmarshaller unmarshaller) {
        return unmarshaller.deserialize(type, parser);
    }

    @Override
    public <T> T fromJson(InputStream stream, Type type) throws JsonbException {
        JsonbUnmarshaller unmarshaller = new JsonbUnmarshaller(jsonbContext);
        return deserialize(type, inputStreamParser(stream), unmarshaller);
    }

    @Override
    public <T> T fromJson(String str, Type type) throws JsonbException {
        JsonParser parser = new JsonbStreamingParser(jsonbContext.getJsonProvider().createParser(new StringReader(str)));
        JsonbUnmarshaller unmarshaller = new JsonbUnmarshaller(jsonbContext);
        return deserialize(type, parser, unmarshaller);
    }

    @Override
    public <T> T fromJson(Reader reader, Type type) throws JsonbException {
        JsonParser parser = new JsonbStreamingParser(jsonbContext.getJsonProvider().createParser(reader));
        JsonbUnmarshaller unmarshaller = new JsonbUnmarshaller(jsonbContext);
        return deserialize(type, parser, unmarshaller);
    }

    private JsonParser inputStreamParser(InputStream stream) {
        return new JsonbStreamingParser(jsonbContext.getJsonProvider()
                                         .createParserFactory(createJsonpProperties(jsonbContext.getConfig()))
                                         .createParser(stream,
                                                       Charset.forName((String) jsonbContext.getConfig()
                                                               .getProperty(JsonbConfig.ENCODING).orElse("UTF-8"))));
    }

    private JsonGenerator writerGenerator(Writer writer) {
        Map<String, ?> factoryProperties = createJsonpProperties(jsonbContext.getConfig());
        if (factoryProperties.isEmpty()) {
            return jsonbContext.getJsonProvider().createGenerator(writer);
        }
        return jsonbContext.getJsonProvider().createGeneratorFactory(factoryProperties).createGenerator(writer);
    }

}

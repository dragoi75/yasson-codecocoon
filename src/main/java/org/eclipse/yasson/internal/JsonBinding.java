/*******************************************************************************
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.yasson.YassonJsonb;
import org.eclipse.yasson.internal.jsonstructure.JsonGeneratorToStructureAdapter;
import org.eclipse.yasson.internal.jsonstructure.JsonStructureToParserAdapter;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.Messages;

import javax.json.JsonStructure;
import javax.json.bind.JsonbConfig;
import javax.json.bind.JsonbException;
import javax.json.spi.JsonProvider;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParser;
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

/**
 * Implementation of Jsonb interface.
 *
 * @author Dmitry Kornilov
 */
public class JsonBinding implements YassonJsonb {

    private final JsonbContext jsonbContext;

    private JsonGenerator streamGenerator(OutputStream stream) {
        Map<String, ?> factoryProperties = createJsonpProperties(jsonbContext.getConfig());
        final String encoding = (String) jsonbContext.getConfig().getProperty(JsonbConfig.ENCODING).orElse("UTF-8");
        return jsonbContext.getJsonProvider().createGeneratorFactory(factoryProperties).createGenerator(stream, Charset.forName(encoding));
    }

    @Override
    public JsonStructure toJsonStructure(Object object) throws JsonbException {
        JsonGeneratorToStructureAdapter structureGenerator = new JsonGeneratorToStructureAdapter(jsonbContext.getJsonProvider());
        final ObjectMarshaller marshaller = new ObjectMarshaller(jsonbContext);
        marshaller.marshal(object, structureGenerator);
        return structureGenerator.getRootStructure();
    }

    @Override
    public <T> T fromJson(JsonParser jsonParser, Class<T> type) throws JsonbException {
        Unmarshaller unmarshaller = new Unmarshaller(jsonbContext);
        return unmarshaller.deserialize(type, new JsonbRiParser(jsonParser));
    }

    @Override
    public void toJson(Object object, JsonGenerator jsonGenerator) throws JsonbException {
        final ObjectMarshaller marshaller = new ObjectMarshaller(jsonbContext);
        marshaller.marshalWithoutClose(object, jsonGenerator);
    }

    @Override
    public String toJson(Object object) throws JsonbException {
        StringWriter writer = new StringWriter();
        final JsonGenerator generator = writerGenerator(writer);
        new ObjectMarshaller(jsonbContext).marshal(object, generator);
        return writer.toString();
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
                throw new JsonbException(Messages.getMessage(MessageKeyConstants.JSONB_CONFIG_FORMATTING_ILLEGAL_VALUE));
            }
            if ((Boolean) value) {
                factoryProperties.put(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
            }
            return factoryProperties;
        }
        return factoryProperties;
    }

    @Override
    public <T> T fromJson(Reader reader, Type type) throws JsonbException {
        JsonParser parser = new JsonbRiParser(jsonbContext.getJsonProvider().createParser(reader));
        Unmarshaller unmarshaller = new Unmarshaller(jsonbContext);
        return deserialize(type, parser, unmarshaller);
    }

    @Override
    public <T> T fromJsonStructure(JsonStructure jsonStructure, Type runtimeType) throws JsonbException {
        JsonParser parser = new JsonbRiParser(new JsonStructureToParserAdapter(jsonStructure));
        return deserialize(runtimeType, parser, new Unmarshaller(jsonbContext));
    }

    @Override
    public <T> T fromJson(String str, Type type) throws JsonbException {
        JsonParser parser = new JsonbRiParser(jsonbContext.getJsonProvider().createParser(new StringReader(str)));
        Unmarshaller unmarshaller = new Unmarshaller(jsonbContext);
        return deserialize(type, parser, unmarshaller);
    }

    @Override
    public <T> T fromJson(InputStream stream, Type type) throws JsonbException {
        Unmarshaller unmarshaller = new Unmarshaller(jsonbContext);
        return deserialize(type, inputStreamParser(stream), unmarshaller);
    }

    @Override
    public void close() throws Exception {
        jsonbContext.getComponentInstanceCreator().close();
    }

    @Override
    public <T> T fromJson(Reader reader, Class<T> type) throws JsonbException {
        JsonParser parser = new JsonbRiParser(jsonbContext.getJsonProvider().createParser(reader));
        Unmarshaller unmarshaller = new Unmarshaller(jsonbContext);
        return deserialize(type, parser, unmarshaller);
    }

    @Override
    public <T> T fromJson(JsonParser jsonParser, Type runtimeType) throws JsonbException {
        Unmarshaller unmarshaller = new Unmarshaller(jsonbContext);
        return unmarshaller.deserialize(runtimeType, new JsonbRiParser(jsonParser));
    }

    @Override
    public <T> T fromJsonStructure(JsonStructure jsonStructure, Class<T> type) throws JsonbException {
        JsonParser parser = new JsonbRiParser(new JsonStructureToParserAdapter(jsonStructure));
        return deserialize(type, parser, new Unmarshaller(jsonbContext));
    }

    @Override
    public void toJson(Object object, Type type, OutputStream stream) throws JsonbException {
        final ObjectMarshaller marshaller = new ObjectMarshaller(jsonbContext, type);
        marshaller.marshal(object, streamGenerator(stream));
    }

    @Override
    public void toJson(Object object, OutputStream stream) throws JsonbException {
        final ObjectMarshaller marshaller = new ObjectMarshaller(jsonbContext);
        marshaller.marshal(object, streamGenerator(stream));
    }

    @Override
    public JsonStructure toJsonStructure(Object object, Type runtimeType) throws JsonbException {
        JsonGeneratorToStructureAdapter structureGenerator = new JsonGeneratorToStructureAdapter(jsonbContext.getJsonProvider());
        final ObjectMarshaller marshaller = new ObjectMarshaller(jsonbContext, runtimeType);
        marshaller.marshal(object, structureGenerator);
        return structureGenerator.getRootStructure();
    }

    @Override
    public String toJson(Object object, Type type) throws JsonbException {
        StringWriter writer = new StringWriter();
        final JsonGenerator generator = writerGenerator(writer);
        new ObjectMarshaller(jsonbContext, type).marshal(object, generator);
        return writer.toString();
    }

    @Override
    public void toJson(Object object, Writer writer) throws JsonbException {
        final ObjectMarshaller marshaller = new ObjectMarshaller(jsonbContext);
        marshaller.marshal(object, writerGenerator(writer));
    }

    @Override
    public <T> T fromJson(String str, Class<T> type) throws JsonbException {
        final JsonParser parser = new JsonbRiParser(jsonbContext.getJsonProvider().createParser(new StringReader(str)));
        final Unmarshaller unmarshaller = new Unmarshaller(jsonbContext);
        return deserialize(type, parser, unmarshaller);
    }

    private <T> T deserialize(final Type type, final JsonParser parser, final Unmarshaller unmarshaller) {
        return unmarshaller.deserialize(type, parser);
    }

    JsonBinding(JsonBindingBuilder builder) {
        this.jsonbContext = new JsonbContext(builder.getConfig(), builder.getProvider().orElseGet(JsonProvider::provider));
    }

    private JsonGenerator writerGenerator(Writer writer) {
        Map<String, ?> factoryProperties = createJsonpProperties(jsonbContext.getConfig());
        if (factoryProperties.isEmpty()) {
            return jsonbContext.getJsonProvider().createGenerator(writer);
        }
        return jsonbContext.getJsonProvider().createGeneratorFactory(factoryProperties).createGenerator(writer);
    }

    @Override
    public void toJson(Object object, Type type, Writer writer) throws JsonbException {
        final ObjectMarshaller marshaller = new ObjectMarshaller(jsonbContext, type);
        marshaller.marshal(object, writerGenerator(writer));
    }

    private JsonParser inputStreamParser(InputStream stream) {
        return new JsonbRiParser(jsonbContext.getJsonProvider().createParserFactory(createJsonpProperties(jsonbContext.getConfig()))
                .createParser(stream,
                        Charset.forName((String) jsonbContext.getConfig().getProperty(JsonbConfig.ENCODING).orElse("UTF-8"))));
    }

    @Override
    public <T> T fromJson(InputStream stream, Class<T> clazz) throws JsonbException {
        Unmarshaller unmarshaller = new Unmarshaller(jsonbContext);
        return deserialize(clazz, inputStreamParser(stream), unmarshaller);
    }

    @Override
    public void toJson(Object object, Type runtimeType, JsonGenerator jsonGenerator) throws JsonbException {
        final ObjectMarshaller marshaller = new ObjectMarshaller(jsonbContext, runtimeType);
        marshaller.marshalWithoutClose(object, jsonGenerator);
    }

}

package org.eclipse.yasson.defaultmapping.basic;

import org.eclipse.yasson.internal.JsonBindingConfigurator;
import org.eclipse.yasson.internal.properties.MessageKeyConstants;
import org.eclipse.yasson.internal.properties.LocalizedMessages;
import org.junit.Assert;
import org.junit.Test;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbConfig;
import javax.json.bind.JsonbException;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

/**
 * @author David Kral
 */
public class SingleValueTest {

    @Test
    public void testMarshallPrimitives() {
        final Jsonb jsonb = (new JsonBindingConfigurator()).build();

        // String
        assertEquals("\"some_string\"", jsonb.toJson("some_string"));

        // Character
        assertEquals("\"\uFFFF\"", jsonb.toJson('\uFFFF'));

        // Byte
        assertEquals("1", jsonb.toJson((byte)1));

        // Short
        assertEquals("1", jsonb.toJson((short)1));

        // Integer
        assertEquals("1", jsonb.toJson(1));

        // Long
        assertEquals("5", jsonb.toJson(5L));

        // Float
        assertEquals("1.2", jsonb.toJson(1.2f));

        // Double
        assertEquals("1.2", jsonb.toJson(1.2));

        // BigInteger
        assertEquals("1", jsonb.toJson(new BigInteger("1")));

        // BigDecimal
        assertEquals("1.2", jsonb.toJson(new BigDecimal("1.2")));

        // Number
        assertEquals("1.2", jsonb.toJson(1.2));

        // Boolean true
        assertEquals("true", jsonb.toJson(true));

        // Boolean false
        assertEquals("false", jsonb.toJson(false));

        assertEquals("1", jsonb.toJson(1));

        // null
        //assertEquals("null", jsonb.toJson(null));
    }

    @Test
    public void testSingleValue() {
        Jsonb jsonb = (new JsonBindingConfigurator()).build();
        assertEquals("5", jsonb.toJson(5));

        jsonb = (new JsonBindingConfigurator().withConfig(new JsonbConfig().withStrictIJSON(true))).build();
        try {
            jsonb.toJson(5);
            Assert.fail();
        } catch (JsonbException exception){
            Assert.assertEquals(LocalizedMessages.getMessage(MessageKeyConstants.IJSON_ENABLED_SINGLE_VALUE), exception.getMessage());
        }
    }

}

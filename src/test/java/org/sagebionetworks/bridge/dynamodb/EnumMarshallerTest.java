package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class EnumMarshallerTest {
    private static final EnumMarshaller MARSHALLER = new EnumMarshaller(TestEnum.class);

    @Test
    public void testMarshall() {
        assertEquals(MARSHALLER.convert(TestEnum.FOO), "FOO");
        assertEquals(MARSHALLER.convert(TestEnum.BAR), "BAR");
    }

    @Test
    public void testUnmarshall() {
        assertEquals(MARSHALLER.unconvert("FOO"), TestEnum.FOO);
        assertEquals(MARSHALLER.unconvert("BAR"), TestEnum.BAR);
    }

    private static enum TestEnum {
        FOO,
        BAR,
    }
}

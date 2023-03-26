package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class JsonMarshallerTest {
    // Test class that we marshal to JSON.
    private static class TestClass {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    // Test marshaller for the test.
    private static class TestMarshaller extends JsonMarshaller<TestClass> {
        @Override
        public Class<TestClass> getConvertedClass() {
            return TestClass.class;
        }
    }

    private static final TestMarshaller MARSHALLER = new TestMarshaller();
    private static final String TEST_VALUE = "test value";

    @Test
    public void normalCase() throws Exception {
        // Create test object.
        TestClass testObject = new TestClass();
        testObject.setValue(TEST_VALUE);

        // Convert to JSON.
        String json = MARSHALLER.convert(testObject);

        // Validate JSON using Jackson.
        JsonNode jsonNode = BridgeObjectMapper.get().readTree(json);
        assertEquals(jsonNode.get("value").textValue(), TEST_VALUE);

        // Convert back to object and validate.
        TestClass unmarshalledObject = MARSHALLER.unconvert(json);
        assertEquals(unmarshalledObject.getValue(), TEST_VALUE);
    }

    @Test
    public void convertNull() {
        // Convert null to JSON.
        String json = MARSHALLER.convert(null);
        assertNull(json);
    }

    @Test
    public void unconvertNull() {
        // Convert JSON back to object.
        TestClass unmarshalledObject = MARSHALLER.unconvert(null);
        assertNull(unmarshalledObject);
    }

    @Test(expectedExceptions = DynamoDBMappingException.class)
    public void unconvertInvalidJson() {
        // Convert invalid JSON back to object.
        MARSHALLER.unconvert("invalid json");
    }
}

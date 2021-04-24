package org.sagebionetworks.bridge.models.upload;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class UploadRequestTest {
    @Test
    public void defaultValues() {
        UploadRequest uploadRequest = new UploadRequest.Builder().build();
        assertNull(uploadRequest.getName());
        assertEquals(uploadRequest.getContentLength(), 0);
        assertNull(uploadRequest.getContentMd5());
        assertNull(uploadRequest.getContentType());
        assertTrue(uploadRequest.isEncrypted());
        assertNull(uploadRequest.getMetadata());
        assertTrue(uploadRequest.isZipped());
    }

    @Test
    public void withValues() throws Exception {
        JsonNode metadata = BridgeObjectMapper.get().readTree("{\"key\":\"value\"}");

        UploadRequest uploadRequest = new UploadRequest.Builder()
                .withName("test name")
                .withContentLength(42L)
                .withContentMd5("dummy md5")
                .withContentType("text/plain")
                .withEncrypted(false)
                .withMetadata(metadata)
                .withZipped(false)
                .build();
        assertEquals(uploadRequest.getName(), "test name");
        assertEquals(uploadRequest.getContentLength(), 42);
        assertEquals(uploadRequest.getContentMd5(), "dummy md5");
        assertEquals(uploadRequest.getContentType(), "text/plain");
        assertFalse(uploadRequest.isEncrypted());
        assertEquals(uploadRequest.getMetadata(), metadata);
        assertFalse(uploadRequest.isZipped());
    }

    @Test
    public void deserialization() throws Exception {
        String json = "{" +
                "   \"name\":\"test name\"," +
                "   \"contentLength\":42," +
                "   \"contentMd5\":\"dummy md5\"," +
                "   \"contentType\":\"text/plain\"," +
                "   \"encrypted\":false," +
                "   \"metadata\":{\"key\":\"value\"}," +
                "   \"zipped\":false" +
                "}";

        UploadRequest uploadRequest = BridgeObjectMapper.get().readValue(json, UploadRequest.class);
        assertEquals(uploadRequest.getName(), "test name");
        assertEquals(uploadRequest.getContentLength(), 42);
        assertEquals(uploadRequest.getContentMd5(), "dummy md5");
        assertEquals(uploadRequest.getContentType(), "text/plain");
        assertFalse(uploadRequest.isEncrypted());
        assertFalse(uploadRequest.isZipped());

        JsonNode metadata = uploadRequest.getMetadata();
        assertEquals(metadata.size(), 1);
        assertEquals(metadata.get("key").textValue(), "value");
    }
}

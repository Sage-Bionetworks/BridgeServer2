package org.sagebionetworks.bridge.models.upload;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

public class UploadRequestTest {
    @Test
    public void defaultValues() {
        UploadRequest uploadRequest = new UploadRequest.Builder().build();
        assertNull(uploadRequest.getName());
        assertEquals(uploadRequest.getContentLength(), 0);
        assertNull(uploadRequest.getContentMd5());
        assertNull(uploadRequest.getContentType());
        assertTrue(uploadRequest.isEncrypted());
        assertTrue(uploadRequest.isZipped());
    }

    @Test
    public void withValues() {
        UploadRequest uploadRequest = new UploadRequest.Builder()
                .withName("test name")
                .withContentLength(42L)
                .withContentMd5("dummy md5")
                .withContentType("text/plain")
                .withEncrypted(false)
                .withZipped(false)
                .build();
        assertEquals(uploadRequest.getName(), "test name");
        assertEquals(uploadRequest.getContentLength(), 42);
        assertEquals(uploadRequest.getContentMd5(), "dummy md5");
        assertEquals(uploadRequest.getContentType(), "text/plain");
        assertFalse(uploadRequest.isEncrypted());
        assertFalse(uploadRequest.isZipped());
    }
}

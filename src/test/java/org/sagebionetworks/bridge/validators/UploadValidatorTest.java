package org.sagebionetworks.bridge.validators;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.springframework.validation.Validator;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;

public class UploadValidatorTest {
    private static final String UPLOAD_CONTENT = "testValidateRequest";

    // The other tests in this class don't go through the controller; we want to do that
    // because we want to verify that we get the right type back. This could easily
    // be broken since there's no external test and we could switch the object internally.
    @Test
    public void uploadRequestHasCorrectType() {
        BridgeObjectMapper mapper = new BridgeObjectMapper();
        
        JsonNode node = mapper.valueToTree(new UploadRequest.Builder().build());
        assertEquals("UploadRequest", node.get("type").asText(), "Type is UploadRequest");
    }

    @Test
    public void testValidateRequest() {
        Validator validator = new UploadValidator();
        
        // A valid case
        {
            UploadRequest uploadRequest = makeValidUploadRequestBuilder().build();
            Validate.entityThrowingException(validator, uploadRequest);
        }

        try {
            UploadRequest uploadRequest = makeValidUploadRequestBuilder().withName(null).build();
            Validate.entityThrowingException(validator, uploadRequest);
        } catch (BridgeServiceException e) {
            assertEquals(e.getStatusCode(), HttpStatus.SC_BAD_REQUEST, "Name missing");
        }

        try {
            UploadRequest uploadRequest = makeValidUploadRequestBuilder().withContentType(null).build();
            Validate.entityThrowingException(validator, uploadRequest);
        } catch (BridgeServiceException e) {
            assertEquals(e.getStatusCode(), HttpStatus.SC_BAD_REQUEST, "Content type missing");
        }

        try {
            UploadRequest uploadRequest = makeValidUploadRequestBuilder().withContentLength(null).build();
            Validate.entityThrowingException(validator, uploadRequest);
        } catch (BridgeServiceException e) {
            assertEquals(e.getStatusCode(), HttpStatus.SC_BAD_REQUEST, "Content length missing");
        }

        try {
            UploadRequest uploadRequest = makeValidUploadRequestBuilder().withContentLength(51000000L).build();
            Validate.entityThrowingException(validator, uploadRequest);
        } catch (BridgeServiceException e) {
            assertEquals(e.getStatusCode(), HttpStatus.SC_BAD_REQUEST, "Content length > 10 MB");
        }

        try {
            UploadRequest uploadRequest = makeValidUploadRequestBuilder().withContentMd5("not-md5").build();
            Validate.entityThrowingException(validator, uploadRequest);
        } catch (BridgeServiceException e) {
            assertEquals(e.getStatusCode(), HttpStatus.SC_BAD_REQUEST, "MD5 not base64 encoded");
        }
    }

    private static UploadRequest.Builder makeValidUploadRequestBuilder() {
        return new UploadRequest.Builder().withName("dummy-upload-name").withContentType("text/plain")
                .withContentLength((long) UPLOAD_CONTENT.getBytes().length)
                .withContentMd5(Base64.encodeBase64String(DigestUtils.md5(UPLOAD_CONTENT)));
    }
}

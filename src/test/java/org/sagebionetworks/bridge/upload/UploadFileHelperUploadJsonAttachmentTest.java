package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Charsets;
import org.apache.commons.codec.digest.DigestUtils;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.s3.S3Helper;

public class UploadFileHelperUploadJsonAttachmentTest {
    private static final String FIELD_NAME = "field-name";
    private static final String UPLOAD_ID = "upload-id";
    private static final String EXPECTED_ATTACHMENT_NAME = UPLOAD_ID + '-' + FIELD_NAME;

    private S3Helper mockS3Helper;
    private UploadFileHelper uploadFileHelper;

    @BeforeMethod
    public void before() throws Exception {
        DigestUtils mockMd5DigestUtils = mock(DigestUtils.class);
        when(mockMd5DigestUtils.digest(any(File.class))).thenReturn(TestConstants.MOCK_MD5);
        when(mockMd5DigestUtils.digest(any(byte[].class))).thenReturn(TestConstants.MOCK_MD5);

        mockS3Helper = mock(S3Helper.class);

        uploadFileHelper = new UploadFileHelper();
        uploadFileHelper.setMd5DigestUtils(mockMd5DigestUtils);
        uploadFileHelper.setS3Helper(mockS3Helper);
    }

    @Test
    public void successCase() throws Exception {
        // Normally this will be an array or an object, but for the purpose of this test, use a TextNode.
        JsonNode result = uploadFileHelper.uploadJsonNodeAsAttachment(TextNode.valueOf("dummy content"), UPLOAD_ID,
                FIELD_NAME);
        assertEquals(result.textValue(), EXPECTED_ATTACHMENT_NAME);

        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        // Validate uploaded content. The text is quoted because when we serialize a JSON string, it quotes the JSON
        // string.
        verify(mockS3Helper).writeBytesToS3(eq(UploadFileHelper.ATTACHMENT_BUCKET), eq(EXPECTED_ATTACHMENT_NAME),
                eq("\"dummy content\"".getBytes(Charsets.UTF_8)), metadataCaptor.capture());
        
        assertEquals(metadataCaptor.getValue().getSSEAlgorithm(), ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
    }

    @Test(expectedExceptions = UploadValidationException.class)
    public void errorCase() throws Exception {
        doThrow(IOException.class).when(mockS3Helper).writeBytesToS3(any(), any(), any(byte[].class), any());
        uploadFileHelper.uploadJsonNodeAsAttachment(TextNode.valueOf("dummy content"), UPLOAD_ID, FIELD_NAME);
    }
}

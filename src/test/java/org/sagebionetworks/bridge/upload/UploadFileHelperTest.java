package org.sagebionetworks.bridge.upload;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.io.File;

import com.amazonaws.services.s3.model.ObjectMetadata;
import org.apache.commons.codec.digest.DigestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.s3.S3Helper;

public class UploadFileHelperTest {
    private static final byte[] CONTENT = "Hello world!".getBytes();
    private static final String FILENAME = "file.txt";

    @Mock
    DigestUtils mockMd5DigestUtils;

    @Mock
    S3Helper mockS3Helper;

    @InjectMocks
    UploadFileHelper helper;

    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockMd5DigestUtils.digest(any(File.class))).thenReturn(TestConstants.MOCK_MD5);
        when(mockMd5DigestUtils.digest(any(byte[].class))).thenReturn(TestConstants.MOCK_MD5);
    }

    @Test
    public void uploadBytesAsAttachment() throws Exception {
        // Execute.
        helper.uploadBytesToS3(FILENAME, CONTENT);

        // Verify.
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(mockS3Helper).writeBytesToS3(eq(UploadFileHelper.ATTACHMENT_BUCKET), eq(FILENAME), eq(CONTENT),
                metadataCaptor.capture());
        ObjectMetadata metadata = metadataCaptor.getValue();
        assertEquals(metadata.getUserMetaDataOf(UploadFileHelper.KEY_CUSTOM_CONTENT_MD5),
                TestConstants.MOCK_MD5_HEX_ENCODED);
        assertEquals(metadata.getSSEAlgorithm(), ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
    }

    @Test
    public void uploadFileAsAttachment() throws Exception {
        // Execute.
        File mockFile = mock(File.class);
        helper.uploadFileToS3(FILENAME, mockFile);

        // Verify.
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(mockS3Helper).writeFileToS3(eq(UploadFileHelper.ATTACHMENT_BUCKET), eq(FILENAME), same(mockFile),
                metadataCaptor.capture());
        ObjectMetadata metadata = metadataCaptor.getValue();
        assertEquals(metadata.getUserMetaDataOf(UploadFileHelper.KEY_CUSTOM_CONTENT_MD5),
                TestConstants.MOCK_MD5_HEX_ENCODED);
        assertEquals(metadata.getSSEAlgorithm(), ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
    }
}

package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;

import org.apache.commons.io.IOUtils;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.s3.S3Helper;

public class UploadCertificateServiceMockTest {
    // For safety, don't use api app. Otherwise, if we botch the test, we risk stomping over the api PEM keys again.
    private static final String PEM_FILENAME = UploadCertificateService.getPemFilename(TEST_APP_ID);

    private AmazonS3 mockS3client;
    private UploadCertificateService svc;

    @BeforeMethod
    public void before() {
        // Mock S3 client. We'll fill in the details during the test.
        mockS3client = mock(AmazonS3.class);

        // Spy UploadCertificateService. This allows us to mock s3Put without doing a bunch of complex logic.
        svc = spy(new UploadCertificateService());
        svc.setS3CmsClient(mockS3client);
    }

    @Test
    public void createKeyPair() {
        when(mockS3client.doesObjectExist(UploadCertificateService.CERT_BUCKET, PEM_FILENAME)).thenReturn(false);
        when(mockS3client.doesObjectExist(UploadCertificateService.PRIVATE_KEY_BUCKET, PEM_FILENAME)).thenReturn(
                false);
        testCreateKeyPair();
    }

    @Test
    public void certExists() {
        when(mockS3client.doesObjectExist(UploadCertificateService.CERT_BUCKET, PEM_FILENAME)).thenReturn(true);
        when(mockS3client.doesObjectExist(UploadCertificateService.PRIVATE_KEY_BUCKET, PEM_FILENAME)).thenReturn(
                false);
        testCreateKeyPair();
    }

    @Test
    public void privKeyExists() {
        when(mockS3client.doesObjectExist(UploadCertificateService.CERT_BUCKET, PEM_FILENAME)).thenReturn(false);
        when(mockS3client.doesObjectExist(UploadCertificateService.PRIVATE_KEY_BUCKET, PEM_FILENAME)).thenReturn(
                true);
        testCreateKeyPair();
    }

    private void testCreateKeyPair() {
        // execute
        svc.createCmsKeyPair(TEST_APP_ID);

        // verify key pair were created
        ArgumentCaptor<String> certCaptor = ArgumentCaptor.forClass(String.class);
        verify(svc).s3Put(eq(UploadCertificateService.CERT_BUCKET), eq(PEM_FILENAME), certCaptor.capture());
        String cert = certCaptor.getValue();
        assertTrue(cert.contains("-----BEGIN CERTIFICATE-----"));
        assertTrue(cert.contains("-----END CERTIFICATE-----"));

        ArgumentCaptor<String> privKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(svc).s3Put(eq(UploadCertificateService.PRIVATE_KEY_BUCKET), eq(PEM_FILENAME), privKeyCaptor.capture());
        String privKey = privKeyCaptor.getValue();
        assertTrue(privKey.contains("-----BEGIN RSA PRIVATE KEY-----"));
        assertTrue(privKey.contains("-----END RSA PRIVATE KEY-----"));
    }

    @Test
    public void bothKeysAlreadyExist() {
        // Mock S3 client. Both keys exist.
        when(mockS3client.doesObjectExist(UploadCertificateService.CERT_BUCKET, PEM_FILENAME)).thenReturn(true);
        when(mockS3client.doesObjectExist(UploadCertificateService.PRIVATE_KEY_BUCKET, PEM_FILENAME)).thenReturn(
                true);

        // execute
        svc.createCmsKeyPair(TEST_APP_ID);

        // We never upload to S3.
        verify(svc, never()).s3Put(any(), any(), any());
    }
    
    @Test
    public void getPublicKeyAsPem() throws Exception {
        S3Helper mockS3CmsHelper = mock(S3Helper.class);
        svc.setS3CmsHelper(mockS3CmsHelper);
        
        svc.getPublicKeyAsPem(TEST_APP_ID);
        
        verify(mockS3CmsHelper).readS3FileAsString(
                BridgeConfigFactory.getConfig().getProperty("upload.cms.cert.bucket"),
                TEST_APP_ID + ".pem");
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void getPublicKeyAsPemThrowsException() throws Exception {
        S3Helper mockS3CmsHelper = mock(S3Helper.class);
        svc.setS3CmsHelper(mockS3CmsHelper);
        
        when(mockS3CmsHelper.readS3FileAsString(any(), any())).thenThrow(new IOException());
        svc.getPublicKeyAsPem(TEST_APP_ID);
    }
    
    @Test
    public void s3Put() throws Exception {
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        ArgumentCaptor<InputStream> isCaptor = ArgumentCaptor.forClass(InputStream.class);
        
        svc.s3Put("bucket", "name", "pem-file.pem");
        
        verify(mockS3client).putObject(eq("bucket"), eq("name"), isCaptor.capture(), metadataCaptor.capture());
        assertEquals(IOUtils.toString(isCaptor.getValue()), "pem-file.pem");
        ObjectMetadata metadata = metadataCaptor.getValue();
        assertEquals(metadata.getSSEAlgorithm(), ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
    }
}

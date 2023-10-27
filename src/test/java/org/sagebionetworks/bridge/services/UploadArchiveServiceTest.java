package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.cache.LoadingCache;

import org.sagebionetworks.bridge.crypto.BcCmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.PemUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("unchecked")
public class UploadArchiveServiceTest {
    private static final byte[] PLAIN_TEXT_DATA = "This is my raw data".getBytes(Charsets.UTF_8);

    private static UploadArchiveService archiveService;
    private static byte[] encryptedData;

    @BeforeClass
    public static void before() throws Exception {
        // encryptor
        File certFile = new ClassPathResource("/cms/rsacert.pem").getFile();
        byte[] certBytes = Files.readAllBytes(certFile.toPath());
        X509Certificate cert = PemUtils.loadCertificateFromPem(new String(certBytes));
        File privateKeyFile = new ClassPathResource("/cms/rsaprivkey.pem").getFile();
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
        PrivateKey privateKey = PemUtils.loadPrivateKeyFromPem(new String(privateKeyBytes));
        CmsEncryptor encryptor = new BcCmsEncryptor(cert, privateKey);

        // mock encryptor cache
        LoadingCache<String, CmsEncryptor> mockEncryptorCache = mock(LoadingCache.class);
        when(mockEncryptorCache.get(notNull())).thenReturn(encryptor);

        // archive service
        archiveService = new UploadArchiveService();
        archiveService.setCmsEncryptorCache(mockEncryptorCache);
        archiveService.setMaxNumZipEntries(1000000);
        archiveService.setMaxZipEntrySize(1000000);

        // Encrypt some data, so our tests have something to work with.
        encryptedData = archiveService.encrypt(TEST_APP_ID, PLAIN_TEXT_DATA);
    }

    @Test
    public void encryptSuccess() {
        assertNotNull(encryptedData);
        assertTrue(encryptedData.length > 0);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void encryptNullAppId() {
        archiveService.encrypt(null, PLAIN_TEXT_DATA);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void encryptEmptyAppId() {
        archiveService.encrypt("", PLAIN_TEXT_DATA);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void encryptBlankAppId() {
        archiveService.encrypt("   ", PLAIN_TEXT_DATA);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void encryptNullBytes() {
        archiveService.encrypt(TEST_APP_ID, null);
    }

    @Test
    public void decryptSuccess() {
        byte[] decryptedData = archiveService.decrypt(TEST_APP_ID, encryptedData);
        assertEquals(decryptedData, PLAIN_TEXT_DATA);
    }

    @Test(expectedExceptions = BridgeServiceException.class)
    public void decryptGarbageData() {
        String garbageStr = "This is not encrypted data.";
        byte[] garbageData = garbageStr.getBytes(Charsets.UTF_8);
        archiveService.decrypt(TEST_APP_ID, garbageData);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void decryptBytesNullAppId() {
        archiveService.decrypt(null, encryptedData);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void decryptBytesEmptyAppId() {
        archiveService.decrypt("", encryptedData);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void decryptBytesBlankAppId() {
        archiveService.decrypt("   ", encryptedData);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void decryptBytesNullBytes() {
        archiveService.decrypt(TEST_APP_ID, (byte[]) null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void decryptStreamNullAppId() throws Exception {
        try (InputStream encryptedInputStream = new ByteArrayInputStream(encryptedData)) {
            archiveService.decrypt(null, encryptedInputStream);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void decryptStreamEmptyAppId() throws Exception {
        try (InputStream encryptedInputStream = new ByteArrayInputStream(encryptedData)) {
            archiveService.decrypt("", encryptedInputStream);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void decryptStreamBlankAppId() throws Exception {
        try (InputStream encryptedInputStream = new ByteArrayInputStream(encryptedData)) {
            archiveService.decrypt("   ", encryptedInputStream);
        }
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void decryptStreamNullBytes() throws Exception {
        archiveService.decrypt(TEST_APP_ID, (InputStream) null);
    }

    @Test
    public void decryptAndUnzipRealFile() throws Exception {
        // get archive file, which is stored in git
        File archiveFile = new ClassPathResource("/cms/data/archive").getFile();
        byte[] encryptedBytes = Files.readAllBytes(archiveFile.toPath());

        // decrypt
        byte[] decryptedData = archiveService.decrypt(TEST_APP_ID, encryptedBytes);
        assertNotNull(decryptedData);
        assertTrue(decryptedData.length > 0);

        // unzip
        Map<String, byte[]> unzippedData = archiveService.unzip(decryptedData);
        assertEquals(unzippedData.size(), 3);
        for (byte[] oneData : unzippedData.values()) {
            assertNotNull(oneData);
            assertTrue(oneData.length > 0);
        }
    }
}

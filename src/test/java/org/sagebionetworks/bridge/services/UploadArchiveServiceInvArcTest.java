package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import com.google.common.cache.LoadingCache;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.crypto.BcCmsEncryptor;
import org.sagebionetworks.bridge.crypto.CmsEncryptor;
import org.sagebionetworks.bridge.crypto.PemUtils;

// Unit test for DIAN-749, Android inv-arc app had wrong public key for encryption.
@SuppressWarnings("unchecked")
public class UploadArchiveServiceInvArcTest {
    private static final String APP_ID_ARC = "arc";
    private static final String APP_ID_INV_ARC = "inv-arc";
    private static final byte[] PLAIN_TEXT_DATA = "This is my raw data".getBytes();

    private static UploadArchiveService archiveService;

    @BeforeClass
    public static void before() throws Exception {
        // Load encryptors. rsacert.pem and rsaprivkey.pem represent the arc app. rsacert2.pem and rsaprivkey2.pem
        // represent the inv-arc app.
        CmsEncryptor arcEncryptor = loadEncryptor("/cms/rsacert.pem", "/cms/rsaprivkey.pem");
        CmsEncryptor invArcEncryptor = loadEncryptor("/cms/rsacert2.pem", "/cms/rsaprivkey2.pem");

        // Mock encryptor cache.
        LoadingCache<String, CmsEncryptor> mockEncryptorCache = mock(LoadingCache.class);
        when(mockEncryptorCache.get(APP_ID_ARC)).thenReturn(arcEncryptor);
        when(mockEncryptorCache.get(APP_ID_INV_ARC)).thenReturn(invArcEncryptor);

        // Create archive service.
        archiveService = new UploadArchiveService();
        archiveService.setCmsEncryptorCache(mockEncryptorCache);
        archiveService.setMaxNumZipEntries(1000000);
        archiveService.setMaxZipEntrySize(1000000);
    }

    private static CmsEncryptor loadEncryptor(String publicKeyPath, String privateKeyPath)
            throws CertificateEncodingException, IOException {
        File certFile = new ClassPathResource(publicKeyPath).getFile();
        byte[] certBytes = Files.readAllBytes(certFile.toPath());
        X509Certificate cert = PemUtils.loadCertificateFromPem(new String(certBytes));

        File privateKeyFile = new ClassPathResource(privateKeyPath).getFile();
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
        PrivateKey privateKey = PemUtils.loadPrivateKeyFromPem(new String(privateKeyBytes));

        return new BcCmsEncryptor(cert, privateKey);
    }

    @Test
    public void testInvArc() throws Exception {
        // Encrypt some data with the arc app and write it to a file.
        byte[] arcEncryptedData = archiveService.encrypt(APP_ID_ARC, PLAIN_TEXT_DATA);
        File arcEncryptedFile = File.createTempFile("arc", ".encrypted");
        FileUtils.writeByteArrayToFile(arcEncryptedFile, arcEncryptedData);

        // Attempt to decrypt it with the inv-arc app.
        File arcDecryptedFile = File.createTempFile("arc", ".decrypted");
        archiveService.decrypt(APP_ID_INV_ARC, arcEncryptedFile, arcDecryptedFile);
        byte[] result = FileUtils.readFileToByteArray(arcDecryptedFile);
        assertEquals(result, PLAIN_TEXT_DATA);
    }
}

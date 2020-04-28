package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.model.CannedAccessControlList.PublicRead;
import static org.sagebionetworks.bridge.services.StudyConsentService.SIGNATURE_BLOCK;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.io.IOException;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.common.collect.ImmutableList;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.io.ByteArrayResource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.MimeType;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.validators.StudyConsentValidator;

public class StudyConsentServiceMockTest extends Mockito {
    private static final String DOCUMENT = "<p>Document</p>";
    private static final StudyConsentForm FORM = new StudyConsentForm(DOCUMENT);
    private static final long CREATED_ON = DateTime.now().getMillis();
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("my-subpop");
    private static final String STORAGE_PATH = "my-subpop." + CREATED_ON;
    private static final String CONSENT_BUCKET = "aConsentBucket";
    private static final String PUBLICATION_BUCKET = "aPublicationBucket";
    
    @Mock
    StudyConsentDao mockDao;
    
    @Mock
    AmazonS3Client mockS3Client;
    
    @Mock
    SubpopulationService mockSubpopService;
    
    @Mock
    S3Helper mockS3Helper;
    
    @Captor
    ArgumentCaptor<PutObjectRequest> requestCaptor;
    
    @Captor
    ArgumentCaptor<Subpopulation> subpopCaptor;
    
    @InjectMocks
    @Spy
    StudyConsentService service;

    @BeforeMethod
    public void before() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(CREATED_ON);
        MockitoAnnotations.initMocks(this);
        
        BridgeConfig config = mock(BridgeConfig.class);
        when(config.getConsentsBucket()).thenReturn(CONSENT_BUCKET);
        when(config.getHostnameWithPostfix("docs")).thenReturn(PUBLICATION_BUCKET);
        service.setBridgeConfig(config);
        
        StudyConsentValidator validator = new StudyConsentValidator();
        validator.setConsentBodyTemplate(new ByteArrayResource("<p>This is the template</p>".getBytes()));
        service.setValidator(validator);
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void addConsent() throws Exception {
        StudyConsent consent = StudyConsent.create();
        consent.setCreatedOn(CREATED_ON);
        consent.setSubpopulationGuid(SUBPOP_GUID.getGuid());
        when(mockDao.addConsent(SUBPOP_GUID, STORAGE_PATH, CREATED_ON)).thenReturn(consent);
        
        StudyConsentView result = service.addConsent(SUBPOP_GUID, FORM);
        assertEquals(result.getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertEquals(result.getCreatedOn(), CREATED_ON);
        assertEquals(result.getDocumentContent(), DOCUMENT + SIGNATURE_BLOCK);
        
        verify(mockS3Helper).writeBytesToS3(CONSENT_BUCKET, STORAGE_PATH, (DOCUMENT + SIGNATURE_BLOCK).getBytes());
        verify(mockDao).addConsent(SUBPOP_GUID, STORAGE_PATH, CREATED_ON);
    }

    @Test
    public void deleteAllConsentsPermanently() {
        StudyConsent consent1 = StudyConsent.create();
        consent1.setStoragePath(SUBPOP_GUID.getGuid() + "." + CREATED_ON);
        
        StudyConsent consent2 = StudyConsent.create();
        consent2.setStoragePath(SUBPOP_GUID.getGuid() + "." + (CREATED_ON - 2000L));
        
        List<StudyConsent> consents = ImmutableList.of(consent1, consent2);
        when(mockDao.getConsents(SUBPOP_GUID)).thenReturn(consents);
        
        service.deleteAllConsentsPermanently(SUBPOP_GUID);
        
        verify(mockDao).deleteConsentPermanently(consent1);
        verify(mockS3Client).deleteObject(CONSENT_BUCKET, consent1.getStoragePath());
        verify(mockDao).deleteConsentPermanently(consent2);
        verify(mockS3Client).deleteObject(CONSENT_BUCKET, consent2.getStoragePath());
        verify(mockS3Client).deleteObject(PUBLICATION_BUCKET, SUBPOP_GUID.getGuid() + "/consent.html");
        verify(mockS3Client).deleteObject(PUBLICATION_BUCKET, SUBPOP_GUID.getGuid() + "/consent.pdf");
    }

    @Test
    public void getActiveConsent() throws Exception {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SUBPOP_GUID);
        subpop.setPublishedConsentCreatedOn(CREATED_ON);
        
        StudyConsent consent = StudyConsent.create();
        consent.setStoragePath(STORAGE_PATH);
        consent.setCreatedOn(CREATED_ON);
        consent.setSubpopulationGuid(SUBPOP_GUID.getGuid());
        when(mockDao.getConsent(SUBPOP_GUID, CREATED_ON)).thenReturn(consent);
        when(mockS3Helper.readS3FileAsString(CONSENT_BUCKET, consent.getStoragePath())).thenReturn(DOCUMENT);
        
        StudyConsentView result = service.getActiveConsent(subpop);
        assertEquals(result.getDocumentContent(), DOCUMENT + SIGNATURE_BLOCK);
        assertEquals(result.getCreatedOn(), CREATED_ON);
        assertEquals(result.getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertEquals(result.getStudyConsent(), consent);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getActiveConsentNotFound() throws Exception {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SUBPOP_GUID);
        subpop.setPublishedConsentCreatedOn(CREATED_ON);
        
        service.getActiveConsent(subpop);
    }
    
    @Test
    public void getMostRecentConsent() throws Exception {
        StudyConsent consent = StudyConsent.create();
        consent.setStoragePath(STORAGE_PATH);
        consent.setCreatedOn(CREATED_ON);
        consent.setSubpopulationGuid(SUBPOP_GUID.getGuid());
        when(mockDao.getMostRecentConsent(SUBPOP_GUID)).thenReturn(consent);
        when(mockS3Helper.readS3FileAsString(CONSENT_BUCKET, consent.getStoragePath())).thenReturn(DOCUMENT);
        
        StudyConsentView result = service.getMostRecentConsent(SUBPOP_GUID);
        assertEquals(result.getDocumentContent(), DOCUMENT + SIGNATURE_BLOCK);
        assertEquals(result.getCreatedOn(), CREATED_ON);
        assertEquals(result.getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertEquals(result.getStudyConsent(), consent);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getMostRecentConsentNotFound() {
        service.getMostRecentConsent(SUBPOP_GUID);
    }
    
    @Test
    public void getAllConsents() {
        List<StudyConsent> consents = ImmutableList.of(StudyConsent.create(), StudyConsent.create());
        when(mockDao.getConsents(SUBPOP_GUID)).thenReturn(consents);
        
        List<StudyConsent> results = service.getAllConsents(SUBPOP_GUID);
        assertSame(results, consents);
    }

    @Test
    public void getConsent() throws Exception {
        StudyConsent consent = StudyConsent.create();
        consent.setCreatedOn(CREATED_ON);
        consent.setSubpopulationGuid(SUBPOP_GUID.getGuid());
        when(mockDao.getConsent(SUBPOP_GUID, CREATED_ON)).thenReturn(consent);
        when(mockS3Helper.readS3FileAsString(CONSENT_BUCKET, consent.getStoragePath())).thenReturn(DOCUMENT);

        StudyConsentView result = service.getConsent(SUBPOP_GUID, CREATED_ON);
        assertEquals(result.getDocumentContent(), DOCUMENT + SIGNATURE_BLOCK);
        assertEquals(result.getCreatedOn(), CREATED_ON);
        assertEquals(result.getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertEquals(result.getStudyConsent(), consent);
    }

    @Test(expectedExceptions = EntityNotFoundException.class)
    public void getConsentNotFound() {
        service.getConsent(SUBPOP_GUID, CREATED_ON);
    }
    
    @Test
    public void publishConsent() throws Exception {
        // This is the document with the footer, where all the template variables have been removed
        String transformedDoc = "<doc><p>Document</p><table class=\"bridge-sig-block\"><tbody><tr>"+
                "<td><div class=\"label\">Name of Adult Participant</div></td><td><img brimg=\"\" "+
                "alt=\"\" onerror=\"this.style.display='none'\" src=\"cid:consentSignature\" /><div "+
                "class=\"label\">Signature of Adult Participant</div></td><td><div class=\"label\">"+
                "Date</div></td></tr><tr><td><div class=\"label\">Email, Phone, or ID</div></td><td>"+
                "<div class=\"label\">Sharing Option</div></td></tr></tbody></table></doc>";
        
        StudyConsent consent = StudyConsent.create();
        consent.setCreatedOn(CREATED_ON);
        consent.setSubpopulationGuid(SUBPOP_GUID.getGuid());
        when(mockDao.getConsent(SUBPOP_GUID, CREATED_ON)).thenReturn(consent);
        when(mockS3Helper.readS3FileAsString(CONSENT_BUCKET, consent.getStoragePath())).thenReturn(DOCUMENT);
        
        App app = App.create();
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SUBPOP_GUID);
        
        service.setConsentTemplate(new ByteArrayResource("<doc>${consent.body}</doc>".getBytes()));
        
        StudyConsentView result = service.publishConsent(app, subpop, CREATED_ON);
        assertEquals(result.getDocumentContent(), DOCUMENT + SIGNATURE_BLOCK);
        assertEquals(result.getCreatedOn(), CREATED_ON);
        assertEquals(result.getSubpopulationGuid(), SUBPOP_GUID.getGuid());
        assertEquals(result.getStudyConsent(), consent);
        
        verify(mockSubpopService).updateSubpopulation(eq(app), subpopCaptor.capture());
        assertEquals(subpopCaptor.getValue().getPublishedConsentCreatedOn(), CREATED_ON);

        verify(mockS3Client, times(2)).putObject(requestCaptor.capture());
        PutObjectRequest request = requestCaptor.getAllValues().get(0);
        assertEquals(request.getBucketName(), PUBLICATION_BUCKET);
        assertEquals(request.getCannedAcl(), PublicRead);
        assertEquals(IOUtils.toString(request.getInputStream()), transformedDoc);
        ObjectMetadata metadata = request.getMetadata();
        assertEquals(metadata.getContentType(), MimeType.HTML.toString());
        
        request = requestCaptor.getAllValues().get(1);
        assertEquals(request.getBucketName(), PUBLICATION_BUCKET);
        assertEquals(request.getCannedAcl(), PublicRead);
        // The PDF output isn't easily verified...
        metadata = request.getMetadata();
        assertEquals(metadata.getContentType(), MimeType.PDF.toString());
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class)
    public void publishConsentNotFound() throws Exception {
        App app = App.create();
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SUBPOP_GUID);
        
        service.publishConsent(app, subpop, CREATED_ON);
    }

    @Test
    public void deleteAll() {
        // Mock dao. We only care about the storage path.
        StudyConsent consent1 = StudyConsent.create();
        consent1.setStoragePath("storagePath1");

        StudyConsent consent2 = StudyConsent.create();
        consent2.setStoragePath("storagePath2");

        when(mockDao.getConsents(SUBPOP_GUID)).thenReturn(ImmutableList.of(consent1, consent2));

        // Execute and validate.
        service.deleteAllConsentsPermanently(SUBPOP_GUID);

        verify(mockDao).deleteConsentPermanently(consent1);
        verify(mockDao).deleteConsentPermanently(consent2);

        verify(mockS3Client).deleteObject(CONSENT_BUCKET, "storagePath1");
        verify(mockS3Client).deleteObject(CONSENT_BUCKET, "storagePath2");

        verify(mockS3Client).deleteObject(PUBLICATION_BUCKET, "my-subpop/consent.html");
        verify(mockS3Client).deleteObject(PUBLICATION_BUCKET, "my-subpop/consent.pdf");
    }
    
    @Test(expectedExceptions = BridgeServiceException.class)
    public void addConsentThrowsException() {
        StudyConsent consent = StudyConsent.create();
        consent.setCreatedOn(CREATED_ON);
        consent.setSubpopulationGuid(SUBPOP_GUID.getGuid());
        when(mockDao.addConsent(SUBPOP_GUID, STORAGE_PATH, CREATED_ON)).thenThrow(new IllegalArgumentException());
        
        service.addConsent(SUBPOP_GUID, FORM);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class, expectedExceptionsMessageRegExp = "Test message")
    public void publishConsentThrowsException() throws Exception {
        StudyConsent consent = StudyConsent.create();
        consent.setCreatedOn(CREATED_ON);
        consent.setSubpopulationGuid(SUBPOP_GUID.getGuid());
        when(mockDao.getConsent(SUBPOP_GUID, CREATED_ON)).thenReturn(consent);
        when(mockS3Helper.readS3FileAsString(CONSENT_BUCKET, consent.getStoragePath())).thenReturn(DOCUMENT);
        doThrow(new IOException("Test message")).when(service).writeBytesToPublicS3(any(), any(), any(), any());
        
        App app = App.create();
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SUBPOP_GUID);
        
        service.publishConsent(app, subpop, CREATED_ON);
    }
    
    @Test(expectedExceptions = BridgeServiceException.class, expectedExceptionsMessageRegExp = "java.io.IOException")
    public void loadDocumentContentThrowsException() throws Exception {
        StudyConsent consent = StudyConsent.create();
        consent.setCreatedOn(CREATED_ON);
        consent.setSubpopulationGuid(SUBPOP_GUID.getGuid());
        when(mockDao.getConsent(SUBPOP_GUID, CREATED_ON)).thenReturn(consent);
        when(mockS3Helper.readS3FileAsString(CONSENT_BUCKET, consent.getStoragePath())).thenThrow(new IOException());
        App app = App.create();
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuid(SUBPOP_GUID);
        
        service.publishConsent(app, subpop, CREATED_ON);
    }
    
    @Test
    public void invalidMarkupIsFixed() {
        StudyConsent consent = StudyConsent.create();
        when(mockDao.addConsent(SUBPOP_GUID, STORAGE_PATH, CREATED_ON)).thenReturn(consent);
        
        StudyConsentForm form = new StudyConsentForm("<cml><p>This is not valid XML.</cml>");
        StudyConsentView view = service.addConsent(SUBPOP_GUID, form);
        assertEquals(view.getDocumentContent(), "<p>This is not valid XML.</p>" + SIGNATURE_BLOCK);
    }
    
    @Test
    public void fullDocumentsAreConvertedToFragments() {
        StudyConsent consent = StudyConsent.create();
        when(mockDao.addConsent(SUBPOP_GUID, STORAGE_PATH, CREATED_ON)).thenReturn(consent);
        
        String doc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title></title></head><body><p>This is all the content that should be kept.</p><br><p>And this makes it a fragment.</p></body></html>";
        
        StudyConsentForm form = new StudyConsentForm(doc);
        StudyConsentView view = service.addConsent(SUBPOP_GUID, form);
        assertEquals(view.getDocumentContent(), "<p>This is all the content that should be kept.</p><br /><p>And this makes it a fragment.</p>" + SIGNATURE_BLOCK);
    }
    
    @Test
    public void ckeditorMarkupIsPreserved() {
        StudyConsent consent = StudyConsent.create();
        when(mockDao.addConsent(SUBPOP_GUID, STORAGE_PATH, CREATED_ON)).thenReturn(consent);
        
        String doc = "<s>This is a test</s><p style=\"color:red\">of new attributes ${url}.</p><hr />";
        
        StudyConsentForm form = new StudyConsentForm(doc);
        StudyConsentView view = service.addConsent(SUBPOP_GUID, form);
        assertEquals(view.getDocumentContent(), doc + SIGNATURE_BLOCK);
    }
    
    @Test
    public void  evenVeryBrokenContentIsFixed() {
        StudyConsent consent = StudyConsent.create();
        when(mockDao.addConsent(SUBPOP_GUID, STORAGE_PATH, CREATED_ON)).thenReturn(consent);
        
        StudyConsentForm form = new StudyConsentForm("</script><div ankle='foo'>This just isn't a SGML-based document no matter how you slice it.</p><h4><img>");
        StudyConsentView view = service.addConsent(SUBPOP_GUID, form);
        assertEquals(view.getDocumentContent(), "<div>This just isn't a SGML-based document no matter how you slice it.<p></p><h4><img /></h4></div>"  + SIGNATURE_BLOCK);
    }
    
    @Test
    public void removedSignatureBlockIsRestored() { 
        StudyConsent consent = StudyConsent.create();
        when(mockDao.addConsent(SUBPOP_GUID, STORAGE_PATH, CREATED_ON)).thenReturn(consent);
        
        StudyConsentForm form = new StudyConsentForm(
                "<p>Test</p>" + SIGNATURE_BLOCK.replace("${participant.name}", ""));
        
        StudyConsentView view = service.addConsent(SUBPOP_GUID, form);
        assertEquals(view.getDocumentContent(), form.getDocumentContent()  + SIGNATURE_BLOCK);
    }
    
    @Test
    public void existingSignatureInformationIsNotDuplicated() {
        StudyConsent consent = StudyConsent.create();
        when(mockDao.addConsent(SUBPOP_GUID, STORAGE_PATH, CREATED_ON)).thenReturn(consent);
        
        StudyConsentForm form = new StudyConsentForm("<p>${participant.name} ${participant.signing.date}</p>");        
        StudyConsentView view = service.addConsent(SUBPOP_GUID, form);
        
        // The information in the document is enough that we do not insert SIGNATURE_BLOCK
        assertEquals(view.getDocumentContent(), form.getDocumentContent());
    }
}

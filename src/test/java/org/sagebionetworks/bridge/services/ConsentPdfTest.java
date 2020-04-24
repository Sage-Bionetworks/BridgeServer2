package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;
import static org.sagebionetworks.bridge.services.StudyConsentService.SIGNATURE_BLOCK;
import static org.testng.Assert.assertTrue;

import java.io.FileInputStream;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.springframework.core.io.ClassPathResource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

public class ConsentPdfTest {
    private static final long TIMESTAMP = DateTime.parse("2017-10-04").getMillis();
    private static final String DOCUMENT_FRAGMENT = "<p>This is a consent agreement body</p>" + SIGNATURE_BLOCK;
    // This is an actual 2x2 image
    private static final String DUMMY_IMAGE_DATA =
            "Qk1GAAAAAAAAADYAAAAoAAAAAgAAAAIAAAABABgAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAA////AAAAAAAAAAD///8AAA==";
    private static final StudyParticipant EMAIL_PARTICIPANT = new StudyParticipant.Builder()
            .withEmail(EMAIL).withEmailVerified(true).build();;
    
    private String consentBodyTemplate;
    private App app;
    
    @BeforeMethod
    public void before() throws Exception {
        DateTimeUtils.setCurrentMillisFixed(TIMESTAMP);
        consentBodyTemplate = IOUtils.toString(new FileInputStream(new ClassPathResource(
                "conf/app-defaults/consent-page.xhtml").getFile()));
        
        app = new DynamoApp();
        app.setName("Study Name");
        app.setSponsorName("Sponsor Name");
        app.setSupportEmail("sender@default.com");
        app.setConsentNotificationEmail("consent@consent.com");
        app.setConsentNotificationEmailVerified(true);
    }
    
    @AfterMethod
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
    }
    
    @Test
    public void createsBytes() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(app, EMAIL_PARTICIPANT, sig, NO_SHARING, DOCUMENT_FRAGMENT,
                consentBodyTemplate);
        
        assertTrue(consentPdf.getBytes().length > 0);
    }
    
    @Test
    public void docWithNullUserTimeZone() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(app, EMAIL_PARTICIPANT, sig, NO_SHARING, DOCUMENT_FRAGMENT,
                consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        String dateStr = ConsentPdf.FORMATTER.print(DateTime.now(DateTimeZone.UTC));
        assertTrue(output.contains(dateStr), "Signing date formatted with default zone");
    }
    
    @Test
    public void docWithoutSigImage() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(app, EMAIL_PARTICIPANT, sig, NO_SHARING,
                DOCUMENT_FRAGMENT, consentBodyTemplate);

        String output = consentPdf.getFormattedConsentDocument(); 
        validateDocBody(output);
    }

    @Test
    public void docWithSigImage() throws Exception {
        ConsentSignature sig = makeSignatureWithImage();
        
        ConsentPdf consentPdf = new ConsentPdf(app, EMAIL_PARTICIPANT, sig, NO_SHARING,
                DOCUMENT_FRAGMENT, consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        validateDocBody(output);
    }
    @Test
    public void legacyDocWithInvalidSig() throws Exception {
        ConsentSignature sig = makeInvalidSignature();
        
        ConsentPdf consentPdf = new ConsentPdf(app, EMAIL_PARTICIPANT, sig, NO_SHARING,
                DOCUMENT_FRAGMENT, consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        validateDocBody(output);
    }
    
    @Test
    public void legacyDocWithNoEmail() throws Exception {
        ConsentSignature sig = makeSignatureWithImage();
        
        StudyParticipant noEmailParticipant = new StudyParticipant.Builder().copyOf(EMAIL_PARTICIPANT)
                .withEmail(null).build();
        
        ConsentPdf consentPdf = new ConsentPdf(app, noEmailParticipant, sig, NO_SHARING, DOCUMENT_FRAGMENT,
                consentBodyTemplate);

        String output = consentPdf.getFormattedConsentDocument();
        assertTrue(!output.contains("@@email@@"));
    }

    @Test
    public void docWithInvalidSig() throws Exception {
        ConsentSignature sig = makeInvalidSignature();
        
        ConsentPdf consentPdf = new ConsentPdf(app, EMAIL_PARTICIPANT, sig, NO_SHARING,
                DOCUMENT_FRAGMENT, consentBodyTemplate);
        
        String output = consentPdf.getFormattedConsentDocument();
        validateDocBody(output);
    }
    
    @Test
    public void phoneSignature() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        StudyParticipant phoneParticipant = new StudyParticipant.Builder().withPhone(PHONE).withPhoneVerified(true).build();
        
        ConsentPdf consentPdf = new ConsentPdf(app, phoneParticipant, sig, NO_SHARING,
                DOCUMENT_FRAGMENT, consentBodyTemplate);
        String output = consentPdf.getFormattedConsentDocument();
        assertTrue(output.contains(">(971) 248-6796<"));
        assertTrue(output.contains(">Phone Number<"));
    }
    
    @Test
    public void externalIdSignature() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        StudyParticipant extIdParticipant = new StudyParticipant.Builder().withExternalId("anId").build();
        
        ConsentPdf consentPdf = new ConsentPdf(app, extIdParticipant, sig, NO_SHARING,
                DOCUMENT_FRAGMENT, consentBodyTemplate);
        String output = consentPdf.getFormattedConsentDocument();
        assertTrue(output.contains(">anId<"));
        assertTrue(output.contains(">ID<"));
    }
    
    @Test 
    public void dateFormattedCorrectly() throws Exception {
        ConsentSignature sig = makeSignatureWithoutImage();
        
        ConsentPdf consentPdf = new ConsentPdf(app, EMAIL_PARTICIPANT, sig, NO_SHARING,
                DOCUMENT_FRAGMENT, consentBodyTemplate);
        String output = consentPdf.getFormattedConsentDocument();

        assertTrue(output.contains("October 4, 2017 (GMT)"), "Contains formatted date");
    }
    
    private static ConsentSignature makeSignatureWithoutImage() {
        return new ConsentSignature.Builder().withName("Test Person").withBirthdate("1980-06-06").build();
    }

    private static ConsentSignature makeSignatureWithImage() {
        return new ConsentSignature.Builder().withName("Test Person").withBirthdate("1980-06-06")
                .withImageMimeType("image/bmp").withImageData(DUMMY_IMAGE_DATA).build();
    }

    private static ConsentSignature makeInvalidSignature() {
        return new ConsentSignature.Builder().withName("<a href=\"http://sagebase.org/\">Test Person</a>")
                .withBirthdate("1980-06-06").withImageMimeType("application/octet-stream")
                .withImageData("\" /><a href=\"http://sagebase.org/\">arbitrary link</a><br name=\"foo").build();
    }

    private static void validateDocBody(String bodyContent) throws Exception {
        String dateStr = ConsentPdf.FORMATTER.print(DateTime.now());
        assertTrue(bodyContent.contains(dateStr), "Signing date correct");
        assertTrue(bodyContent.contains("<title>Study Name Consent To Research</title>"), "Study name correct");
        assertTrue(bodyContent.contains(">Test Person<"), "Name correct");
        assertTrue(bodyContent.contains(">email@email.com<"), "User email correct");
        assertTrue(bodyContent.contains(">Not Sharing<"), "Sharing correct");
        assertTrue(bodyContent.contains(">Email Address<"), "Contact correctly labeled");
    }
}

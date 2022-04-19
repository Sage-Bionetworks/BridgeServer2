package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.Charset.defaultCharset;
import static org.sagebionetworks.bridge.BridgeUtils.sanitizeHTML;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.text.translate.CharSequenceTranslator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.MimeType;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.validators.StudyConsentValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.Validator;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.util.XRRuntimeException;

import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.lowagie.text.DocumentException;

@Component
public class StudyConsentService {
    private static final Logger logger = LoggerFactory.getLogger(StudyConsentService.class);
    
    /**
     * By including the signature block through the StudyConsentService, we are able to 
     * centralize the definition of the block in one place, and apply it to both old and 
     * new consent documents. We will test and add the block to any document that removes 
     * the participant's signature or signing date. 
     */
    public static final String SIGNATURE_BLOCK = "<table class=\"bridge-sig-block\">"+
            "<tbody><tr><td>${participant.name}<div class=\"label\">Name of Adult Participant</div></td>"+
            "<td><img brimg=\"\" alt=\"\" onerror=\"this.style.display='none'\" src=\"cid:consentSignature\" />"+
            "<div class=\"label\">Signature of Adult Participant</div></td>"+
            "<td>${participant.signing.date}<div class=\"label\">Date</div></td></tr>"+
            "<tr><td>${participant.contactInfo}<div class=\"label\">${participant.contactLabel}</div></td>"+
            "<td>${participant.sharing}<div class=\"label\">Sharing Option</div></td></tr></tbody></table>";
    
    /**
     * For the published version of the consent document, every template variable in the footer needs
     * to have a suitable value.
     */
    Map<String, String> SIGNATURE_BLOCK_VARS = new ImmutableMap.Builder<String, String>()
            .put("participant.name", "")
            .put("participant.signing.date", "")
            .put("participant.contactInfo", "")
            .put("participant.sharing", "")
            .put("participant.contactLabel", "Email, Phone, or ID").build();
    
    static final String CONSENT_HTML_SUFFIX = "/consent.html";
    static final String CONSENT_PDF_SUFFIX = "/consent.pdf";

    // Documented to be threat-safe
    private static final CharSequenceTranslator XML_ESCAPER = StringEscapeUtils.ESCAPE_XML11;
    
    private Validator validator;
    private StudyConsentDao studyConsentDao;
    private SubpopulationService subpopService;
    private AmazonS3 s3Client;
    private S3Helper s3Helper;
    private String consentsBucket = BridgeConfigFactory.getConfig().getConsentsBucket();
    private String publicationsBucket = BridgeConfigFactory.getConfig().getHostnameWithPostfix("docs");
    private String fullPageTemplate;
    
    @Value("classpath:conf/app-defaults/consent-page.xhtml")
    final void setConsentTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.fullPageTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Autowired
    final void setValidator(StudyConsentValidator validator) {
        this.validator = validator;
    }
    @Autowired
    final void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    @Autowired
    final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.consentsBucket = bridgeConfig.getConsentsBucket();
        this.publicationsBucket = bridgeConfig.getHostnameWithPostfix("docs");
    }
    /**
     * S3 client. We need to use the S3 client to call writeBytesToPublicS3(), which wasn't migrated to bridge-base
     * because it references BridgePF-specific classes.
     */
    @Resource(name = "s3Client")
    final void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Resource(name = "s3Helper")
    final void setS3Helper(S3Helper helper) {
        this.s3Helper = helper;
    }
    
    /**
     * Adds a new consent document to the study, and sets that consent document as active.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @param form
     *            form filled out by researcher including the path to the consent document and the minimum age required
     *            to consent.
     * @return the added consent document of type StudyConsent along with its document content
     */
    public StudyConsentView addConsent(SubpopulationGuid subpopGuid, StudyConsentForm form) {
        checkNotNull(subpopGuid);
        checkNotNull(form);
        
        String sanitizedContent = sanitizeHTML(form.getDocumentContent());
        Validate.entityThrowingException(validator, new StudyConsentForm(sanitizedContent));
        
        sanitizedContent = appendSignatureBlockIfNeeded(sanitizedContent);

        long createdOn = DateUtils.getCurrentMillisFromEpoch();
        String storagePath = subpopGuid.getGuid() + "." + createdOn;
        logger.info("Accessing bucket: " + consentsBucket + " with storagePath: " + storagePath);
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            s3Helper.writeBytesToS3(consentsBucket, storagePath, sanitizedContent.getBytes(defaultCharset()));
            logger.info("Finished writing consent to bucket " + consentsBucket + " storagePath " + storagePath +
                    " (" + sanitizedContent.length() + " chars) in " + stopwatch.elapsed(TimeUnit.MILLISECONDS) +
                    " ms");

            StudyConsent consent = studyConsentDao.addConsent(subpopGuid, storagePath, createdOn);
            return new StudyConsentView(consent, sanitizedContent);
        } catch(Throwable t) {
            throw new BridgeServiceException(t);
        }
    }

    /** Physically delete all the consents for a subpopulation. */
    public void deleteAllConsentsPermanently(SubpopulationGuid subpopulationGuid) {
        checkNotNull(subpopulationGuid);

        // We need to load all consents, so we know their storage paths and can delete their S3 contents.
        List<StudyConsent> consentList = getAllConsents(subpopulationGuid);
        for (StudyConsent consent : consentList) {
            studyConsentDao.deleteConsentPermanently(consent);
            s3Client.deleteObject(consentsBucket, consent.getStoragePath());
        }

        // We need to delete from the publications bucket.
        s3Client.deleteObject(publicationsBucket, subpopulationGuid.getGuid() + CONSENT_HTML_SUFFIX);
        s3Client.deleteObject(publicationsBucket, subpopulationGuid.getGuid() + CONSENT_PDF_SUFFIX);
    }

    /**
     * Gets the currently active consent document for this subpopulation.
     *
     * @param subpop
     *          the subpopulation associated with this consent
     * @return the currently active StudyConsent along with its document content
     */
    public StudyConsentView getActiveConsent(Subpopulation subpop) {
        checkNotNull(subpop);
        
        return getConsent(subpop.getGuid(), subpop.getPublishedConsentCreatedOn());
    }
    
    /**
     * Gets the most recently created consent document for the study.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @return the most recent StudyConsent along with its document content
     */
    public StudyConsentView getMostRecentConsent(SubpopulationGuid subpopGuid) {
        checkNotNull(subpopGuid);
        
        StudyConsent consent = studyConsentDao.getMostRecentConsent(subpopGuid);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        String documentContent = loadDocumentContent(consent);
        return new StudyConsentView(consent, documentContent);
    }

    /**
     * Get all added consent documents for the study.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @return list of all consent documents associated with study along with its document content
     */
    public List<StudyConsent> getAllConsents(SubpopulationGuid subpopGuid) {
        checkNotNull(subpopGuid);
        
        return studyConsentDao.getConsents(subpopGuid);
    }

    /**
     * Gets the consent document associated with the study created at the
     * specified timestamp.
     *
     * @param subpopGuid
     *            the subpopulation associated with this consent
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the specified consent document along with its document content
     */
    public StudyConsentView getConsent(SubpopulationGuid subpopGuid, long timestamp) {
        checkNotNull(subpopGuid);
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(subpopGuid, timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        String documentContent = loadDocumentContent(consent);
        return new StudyConsentView(consent, documentContent);
    }

    /**
     * Set the specified consent document as active, setting all other consent documents 
     * as inactive.
     *
     * @param app
     *            app for this consent
     * @param subpop
     *            the subpopulation associated with this consent
     * @param timestamp
     *            time the consent document was added to the database.
     * @return the activated consent document along with its document content
     */
    public StudyConsentView publishConsent(App app, Subpopulation subpop, long timestamp) {
        checkNotNull(app);
        checkNotNull(subpop);
        checkArgument(timestamp > 0, "Timestamp is 0");
        
        StudyConsent consent = studyConsentDao.getConsent(subpop.getGuid(), timestamp);
        if (consent == null) {
            throw new EntityNotFoundException(StudyConsent.class);
        }
        // Only if we can publish the document, do we mark it as published in the database.
        String documentContent = loadDocumentContent(consent);
        try {
            publishFormatsToS3(app, subpop.getGuid(), documentContent);
            
            subpop.setPublishedConsentCreatedOn(timestamp);
            subpopService.updateSubpopulation(app, subpop);

        } catch(IOException | DocumentException | XRRuntimeException e) {
            throw new BridgeServiceException(e.getMessage());
        }
        return new StudyConsentView(consent, documentContent);
    }
    
    private String loadDocumentContent(StudyConsent consent) {
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();
            String content = s3Helper.readS3FileAsString(consentsBucket, consent.getStoragePath());
            logger.info("Finished reading consent from bucket " + consentsBucket + " storagePath " +
                    consent.getStoragePath() + " (" + content.length() + " chars) in " +
                    stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");
            // Add a signature block if this document does not contain one.
            return appendSignatureBlockIfNeeded(content);
        } catch(IOException ioe) {
            logger.error("Failure loading storagePath: " + consent.getStoragePath());
            throw new BridgeServiceException(ioe);
        }
    }
    
    private String appendSignatureBlockIfNeeded(String content) {
        // The user can change the signature block, they can remove parts of the signature block,
        // but if the person's name or the signing date are gone, we're assuming that they've 
        // removed too much, and we re-append the signature block.
        if (!content.contains("${participant.name}") || !content.contains("${participant.signing.date}")) {
            content = content + SIGNATURE_BLOCK;
        }
        return content;
    }
    
    private void publishFormatsToS3(App app, SubpopulationGuid subpopGuid, String bodyTemplate) throws DocumentException, IOException {
        Map<String,String> map = BridgeUtils.appTemplateVariables(app, (value) -> XML_ESCAPER.translate(value));
        map.putAll(SIGNATURE_BLOCK_VARS);
        String resolvedHTML = BridgeUtils.resolveTemplate(bodyTemplate, map);

        map.put("consent.body", resolvedHTML);
        resolvedHTML = BridgeUtils.resolveTemplate(fullPageTemplate, map);

        String key = subpopGuid.getGuid() + CONSENT_HTML_SUFFIX;
        byte[] bytes = resolvedHTML.getBytes(Charset.forName(("UTF-8")));
        writeBytesToPublicS3(publicationsBucket, key, bytes, MimeType.HTML);
        
        // Now create and post a PDF version !
        try (ByteArrayBuilder buffer = new ByteArrayBuilder()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(resolvedHTML);
            renderer.layout();
            renderer.createPDF(buffer);
            buffer.flush();

            key = subpopGuid.getGuid() + CONSENT_PDF_SUFFIX;
            writeBytesToPublicS3(publicationsBucket, key, buffer.toByteArray(), MimeType.PDF);
        }
    }

    /**
     * Write the byte array to a bucket at S3. The bucket will be given world read privileges, and the request
     * will be returned with the appropriate content type header for the document's MimeType.
     */
    void writeBytesToPublicS3(@Nonnull String bucket, @Nonnull String key, @Nonnull byte[] data,
            @Nonnull MimeType type) throws IOException {
        try (InputStream dataInputStream = new ByteArrayInputStream(data)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(type.toString());
            PutObjectRequest request = new PutObjectRequest(bucket, key, dataInputStream, metadata)
                    .withCannedAcl(CannedAccessControlList.PublicRead);

            Stopwatch stopwatch = Stopwatch.createStarted();
            s3Client.putObject(request);
            logger.info("Finished writing to bucket " + bucket + " key " + key + " (" + data.length + " bytes) in " +
                    stopwatch.elapsed(TimeUnit.MILLISECONDS) + " ms");
        }
    }
}
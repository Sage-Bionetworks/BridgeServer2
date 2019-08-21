package org.sagebionetworks.bridge.models.subpopulations;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Combines the DynamoDB study consent record with the contents of the consent document, 
 * stored on S3. As long as you retrieve a study consent through the StudyConsentService, 
 * it should function as if the document content was persisted with the consent record.
 */
@BridgeTypeName("StudyConsent")
public class StudyConsentView {
    
    /**
     * By including the signature block through the StudyConsentView, we are able to 
     * centralize the definition of the block in one place, apply it to both old and new 
     * consent documents, and make it available as part of the document so it can be 
     * localized or changed. 
     */
    private static final String SIGNATURE_BLOCK = "<table class=\"bridge-sig-block\">"+
            "<tr><td>${participant.name}<div class=\"label\">Name of Adult Participant</div></td>"+
            "<td><img alt=\"\" onerror=\"this.style.display='none'\" src=\"cid:consentSignature\"/>"+
            "<div class=\"label\">Signature of Adult Participant</div></td>"+
            "<td>${participant.signing.date}<div class=\"label\">Date</div></td></tr>"+
            "<tr><td>${participant.contactInfo}<div class=\"label\">${participant.contactLabel}</div></td>"+
            "<td>${participant.sharing}<div class=\"label\">Sharing Option</div></td></tr></table>";
    
    private final StudyConsent consent;
    private final String documentContent;
    
    public StudyConsentView(StudyConsent consent, String documentContent) {
        checkNotNull(consent);
        checkNotNull(documentContent);
        this.consent = consent;
        this.documentContent = documentContent;
    }

    public String getSubpopulationGuid() {
        return consent.getSubpopulationGuid();
    }
    
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getCreatedOn() {
        return consent.getCreatedOn();
    }

    @JsonIgnore
    public StudyConsent getStudyConsent() {
        return consent;
    }
    
    public String getDocumentContent() {
        if (documentContent.indexOf("<table class=\"bridge-sig-block\">") == -1) {
            return documentContent + SIGNATURE_BLOCK;
        }
        return documentContent;
    }
}

package org.sagebionetworks.bridge.models.templates;

import static org.sagebionetworks.bridge.models.studies.MimeType.TEXT;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.HibernateTemplateRevision;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;

@BridgeTypeName("TemplateRevision")
@JsonDeserialize(as=HibernateTemplateRevision.class)
public interface TemplateRevision extends BridgeEntity {
    
    public static TemplateRevision create() { 
        return new HibernateTemplateRevision();
    }
    
    // This is a temporary bridging method while migrating. Eventually we will directly
    // retrieve a template revision object for templates.
    public static TemplateRevision create(EmailTemplate template) {
        TemplateRevision revision = new HibernateTemplateRevision();
        revision.setSubject(template.getSubject());
        revision.setDocumentContent(template.getBody());
        revision.setMimeType(template.getMimeType());
        return revision;
    }
    
    // This is a temporary bridging method while migrating. Eventually we will directly
    // retrieve a template revision object for templates.
    public static TemplateRevision create(SmsTemplate template) {
        TemplateRevision revision = new HibernateTemplateRevision();
        revision.setDocumentContent(template.getMessage());
        revision.setMimeType(TEXT);
        return revision;
    }
    
    /**
     * This revision is a version of the document for the template with this guid. This
     * is a foreign key to the Template table and one part of the revision's compound 
     * primary key (templateGuid + createdOn). This field is suppressed in JSON output 
     * through the API. 
     */
    String getTemplateGuid();
    void setTemplateGuid(String templateGuid);
    
    /**
     * The creation date of this revision. Every revision for a given template must have 
     * a unique creation date, this is the second part of the revision's compound primary 
     * key (templateGuid + createdOn).
     */
    DateTime getCreatedOn();
    void setCreatedOn(DateTime createdOn);
    
    /**
     * The Account ID of the user who created this immutable revision of the template.
     */
    String getCreatedBy();
    void setCreatedBy(String createdBy);
    
    /**
     * The body content of the revision is stored on S3.
     * @return
     */
    String getStoragePath();
    void setStoragePath(String storagePath);
    
    String getDocumentContent();
    void setDocumentContent(String documentContent);
    
    MimeType getMimeType();
    void setMimeType(MimeType mimeType);
    
    String getSubject();
    void setSubject(String subject);
}
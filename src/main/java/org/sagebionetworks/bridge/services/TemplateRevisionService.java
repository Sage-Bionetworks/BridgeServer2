package org.sagebionetworks.bridge.services;

import static com.amazonaws.services.s3.model.ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.validators.TemplateRevisionValidator.INSTANCE;

import com.amazonaws.services.s3.model.ObjectMetadata;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.TemplateDao;
import org.sagebionetworks.bridge.dao.TemplateRevisionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CreatedOnHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class TemplateRevisionService {

    private TemplateDao templateDao;
    
    private TemplateRevisionDao templateRevisionDao;
    
    private S3Helper s3Helper;
    
    private String publicationsBucket;
    
    @Autowired
    final void setTemplateDao(TemplateDao templateDao) {
        this.templateDao = templateDao;
    }
    
    @Autowired
    final void setTemplateRevisionDao(TemplateRevisionDao templateRevisionDao) {
        this.templateRevisionDao = templateRevisionDao;
    }
    
    @Autowired
    final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }
    
    @Autowired
    final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.publicationsBucket = bridgeConfig.getHostnameWithPostfix("docs");
    }
    
    
    public PagedResourceList<? extends TemplateRevision> getTemplateRevisions(StudyIdentifier studyId,
            String templateGuid, Integer offset, Integer pageSize) {
        checkNotNull(studyId);
        checkNotNull(templateGuid);
        
        if (offset == null) {
            offset = 0;
        }
        if (pageSize == null) {
            pageSize = API_DEFAULT_PAGE_SIZE;
        }
        if (offset < 0) {
            throw new BadRequestException("Invalid negative offset value");
        } else if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException("pageSize must be in range 1-" + API_MAXIMUM_PAGE_SIZE);
        }
        
        // verify the template GUID is in the user's study.
        templateDao.getTemplate(studyId, templateGuid)
                .orElseThrow(() -> new EntityNotFoundException(Template.class));
        
        return templateRevisionDao.getTemplateRevisions(templateGuid, offset, pageSize);
    }
    
    public CreatedOnHolder createTemplateRevision(StudyIdentifier studyId, String templateGuid, TemplateRevision revision)
            throws Exception {
        checkNotNull(studyId);
        checkNotNull(templateGuid);
        checkNotNull(revision);
        
        DateTime createdOn = getDateTime();
        String storagePath = templateGuid + "." + createdOn.getMillis();

        // verify the template GUID is in the user's study.
        templateDao.getTemplate(studyId, templateGuid)
                .orElseThrow(() -> new EntityNotFoundException(Template.class));
        
        revision.setCreatedOn(createdOn);
        revision.setTemplateGuid(templateGuid);
        revision.setCreatedBy(getUserId());
        revision.setStoragePath(storagePath);
        
        Validate.entityThrowingException(INSTANCE, revision);
        
        // save document content
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setSSEAlgorithm(AES_256_SERVER_SIDE_ENCRYPTION);
        s3Helper.writeBytesToS3(publicationsBucket, storagePath, revision.getDocumentContent().getBytes(), metadata);
        
        templateRevisionDao.createTemplateRevision(revision);
        
        return new CreatedOnHolder(createdOn);
    }
    
    public TemplateRevision getTemplateRevision(StudyIdentifier studyId, String templateGuid, DateTime createdOn)
            throws Exception {
        checkNotNull(studyId);
        checkNotNull(templateGuid);
        
        // verify the template GUID is in the user's study.
        templateDao.getTemplate(studyId, templateGuid)
                .orElseThrow(() -> new EntityNotFoundException(Template.class));
        
        TemplateRevision revision = templateRevisionDao.getTemplateRevision(templateGuid, createdOn)
                .orElseThrow(() -> new EntityNotFoundException(TemplateRevision.class));
        
        // Load document content
        String storagePath = templateGuid + "." + createdOn.getMillis();
        String documentContent = s3Helper.readS3FileAsString(publicationsBucket, storagePath);
        revision.setDocumentContent(documentContent);
        
        return revision;
    }
    
    public void publishTemplateRevision(StudyIdentifier studyId, String templateGuid, DateTime createdOn) {
        checkNotNull(studyId);
        checkNotNull(templateGuid);
        checkNotNull(createdOn);
        
        Template template = templateDao.getTemplate(studyId, templateGuid)
                .orElseThrow(() -> new EntityNotFoundException(Template.class));
        
        templateRevisionDao.getTemplateRevision(templateGuid, createdOn)
                .orElseThrow(() -> new EntityNotFoundException(TemplateRevision.class));
        
        template.setPublishedCreatedOn(createdOn);
        templateDao.updateTemplate(template);
    }
    
    protected String getUserId() {
        return BridgeUtils.getRequestContext().getId();
    }
    
    protected DateTime getDateTime() {
        return DateTime.now();
    }
}

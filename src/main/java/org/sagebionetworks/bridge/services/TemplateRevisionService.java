package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.TemplateDao;
import org.sagebionetworks.bridge.dao.TemplateRevisionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CreatedOnHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.validators.TemplateRevisionValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class TemplateRevisionService {

    private TemplateDao templateDao;
    
    private TemplateRevisionDao templateRevisionDao;
    
    @Autowired
    final void setTemplateDao(TemplateDao templateDao) {
        this.templateDao = templateDao;
    }
    
    @Autowired
    final void setTemplateRevisionDao(TemplateRevisionDao templateRevisionDao) {
        this.templateRevisionDao = templateRevisionDao;
    }
    
    public PagedResourceList<? extends TemplateRevision> getTemplateRevisions(String studyId,
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
    
    public CreatedOnHolder createTemplateRevision(String studyId, String templateGuid, TemplateRevision revision)
            throws Exception {
        checkNotNull(studyId);
        checkNotNull(templateGuid);
        checkNotNull(revision);
        
        DateTime createdOn = getDateTime();
        String storagePath = templateGuid + "." + createdOn.getMillis();

        // verify the template GUID is in the user's study.
        Template template = templateDao.getTemplate(studyId, templateGuid)
                .orElseThrow(() -> new EntityNotFoundException(Template.class));
        
        revision.setCreatedOn(createdOn);
        revision.setTemplateGuid(templateGuid);
        revision.setCreatedBy(getUserId());
        revision.setStoragePath(storagePath);
        
        TemplateRevisionValidator validator = new TemplateRevisionValidator(template.getTemplateType());
        Validate.entityThrowingException(validator, revision);
        
        templateRevisionDao.createTemplateRevision(revision);
        
        return new CreatedOnHolder(createdOn);
    }
    
    public TemplateRevision getTemplateRevision(String studyId, String templateGuid, DateTime createdOn)
            throws Exception {
        checkNotNull(studyId);
        checkNotNull(templateGuid);
        
        // verify the template GUID is in the user's study.
        templateDao.getTemplate(studyId, templateGuid)
                .orElseThrow(() -> new EntityNotFoundException(Template.class));
        
        TemplateRevision revision = templateRevisionDao.getTemplateRevision(templateGuid, createdOn)
                .orElseThrow(() -> new EntityNotFoundException(TemplateRevision.class));
        
        return revision;
    }
    
    public void publishTemplateRevision(String studyId, String templateGuid, DateTime createdOn) {
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
        return BridgeUtils.getRequestContext().getCallerUserId();
    }
    
    protected DateTime getDateTime() {
        return DateTime.now();
    }
}

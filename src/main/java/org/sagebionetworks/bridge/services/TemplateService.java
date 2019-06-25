package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.dao.TemplateDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.Template;
import org.sagebionetworks.bridge.models.TemplateType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.TemplateValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class TemplateService {

    private TemplateDao templateDao;
    
    private CriteriaDao criteriaDao;
    
    private StudyService studyService;
    
    private SubstudyService substudyService;
    
    @Autowired
    final void setTemplateDao(TemplateDao templateDao) {
        this.templateDao = templateDao;
    }
    
    @Autowired
    final void setCriteriaDao(CriteriaDao criteriaDao) {
        this.criteriaDao = criteriaDao;
    }
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.substudyService = substudyService;
    }

    public Template getTemplateForUser(CriteriaContext context, TemplateType type) {
        checkNotNull(context);
        checkNotNull(type);

        ResourceList<? extends Template> results = templateDao.getTemplates(
                context.getStudyIdentifier(), type, null, null, false);
        for (Template template : results.getItems()) {
            loadCriteria(template);
        }

        List<Template> templates = results.getItems().stream().filter(template -> {
            return CriteriaUtils.matchCriteria(context, template.getCriteria());
        }).collect(toImmutableList());
        
        // The ideal case: one and only one template matches the user's context
        if (templates.size() == 1) {
            return templates.get(0);
        }
        // If not, fall back to the default specified for this study:
        Study study = studyService.getStudy(context.getStudyIdentifier());
        String defaultGuid = study.getDefaultTemplates().get(type.name().toLowerCase());
        if (defaultGuid != null) {
            return getTemplate(context.getStudyIdentifier(), defaultGuid);
        }
        throw new EntityNotFoundException(Template.class);
    }
    
    public PagedResourceList<? extends Template> getTemplatesForType(StudyIdentifier studyId, TemplateType type,
            Integer offset, Integer pageSize, boolean includeDeleted) {
        checkNotNull(studyId);
        checkNotNull(type);

        if (offset == null) {
            offset = 0;
        }
        if (pageSize == null) {
            pageSize = API_DEFAULT_PAGE_SIZE;
        }
        if (offset < 0) {
            throw new BadRequestException("Invalid negative offset value");
        } else if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException("pageSize must be in range " + API_MINIMUM_PAGE_SIZE + "-" + API_MAXIMUM_PAGE_SIZE);
        }
        
        PagedResourceList<? extends Template> templates = templateDao.getTemplates(studyId, type, offset, pageSize, includeDeleted);
        for (Template template : templates.getItems()) {
            loadCriteria(template);
        }
        return templates;
    }
    
    public Template getTemplate(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        
        if (StringUtils.isBlank(guid)) {
            throw new BadRequestException("Invalid template guid: " + guid);
        }
        
        Template template = templateDao.getTemplate(studyId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Template.class));
        
        loadCriteria(template);
        return template;
    }
    
    public GuidVersionHolder createTemplate(StudyIdentifier studyId, Template template) {
        checkNotNull(studyId);
        checkNotNull(template);
        
        template.setStudyId(studyId.getIdentifier());
        template.setDeleted(false);
        template.setVersion(0);
        template.setGuid(generateGuid());
        DateTime timestamp = getTimestamp();
        template.setCreatedOn(timestamp);
        template.setModifiedOn(timestamp);

        Study study = studyService.getStudy(studyId);
        Set<String> substudyIds = substudyService.getSubstudyIds(study.getStudyIdentifier());
        
        TemplateValidator validator = new TemplateValidator(study.getDataGroups(), substudyIds);
        Validate.entityThrowingException(validator, template);
        
        Criteria criteria = persistCriteria(template);
        template.setCriteria(criteria);
        
        templateDao.createTemplate(template);

        return new GuidVersionHolder(template.getGuid(), new Long(template.getVersion()));
    }
    
    public GuidVersionHolder updateTemplate(StudyIdentifier studyId, Template template) {
        checkNotNull(studyId);
        checkNotNull(template);
        
        // If the entity is deleted and staying deleted, don't allow this operation.
        Template existing = getTemplate(studyId, template.getGuid());
        if (existing.isDeleted() && template.isDeleted()) {
            throw new EntityNotFoundException(Template.class);
        }
        checkNotDeletingDefault(template, studyId);
        
        template.setStudyId(studyId.getIdentifier());
        template.setModifiedOn(getTimestamp());
        // no reason for these to be updated after creation
        template.setTemplateType(existing.getTemplateType());
        template.setCreatedOn(existing.getCreatedOn());
        
        Study study = studyService.getStudy(studyId);
        Set<String> substudyIds = substudyService.getSubstudyIds(study.getStudyIdentifier());
        
        TemplateValidator validator = new TemplateValidator(study.getDataGroups(), substudyIds);
        Validate.entityThrowingException(validator, template);
        
        persistCriteria(template);
        templateDao.updateTemplate(template);
        
        return new GuidVersionHolder(template.getGuid(), new Long(template.getVersion()));
    }
    
    public void deleteTemplate(StudyIdentifier studyId, String guid) {
        // This not only verifies the template exists, it verifies it is in the caller's study
        Template existing = getTemplate(studyId, guid);
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Template.class);
        }
        checkNotDeletingDefault(existing, studyId);
        
        existing.setDeleted(true);
        existing.setModifiedOn(getTimestamp());
        
        templateDao.updateTemplate(existing);
    }
    
    public void deleteTemplatePermanently(StudyIdentifier studyId, String guid) {
        // Deletes fail quietly, we don't throw 404s if they're unnecessary 
        Optional<Template> optional = templateDao.getTemplate(studyId, guid);
        if (!optional.isPresent()) {
            return;
        }
        // You cannot delete the default template (logical or physical).
        Template existing = optional.get();
        checkNotDeletingDefault(existing, studyId);
        templateDao.deleteTemplatePermanently(studyId, guid);
    }

    private void checkNotDeletingDefault(Template template, StudyIdentifier studyId) {
        Study study = studyService.getStudy(studyId);
        String defaultGuid = study.getDefaultTemplates().get(template.getTemplateType().name().toLowerCase());
        if (template.getGuid().equals(defaultGuid)) {
            throw new ConstraintViolationException.Builder().withMessage("The default template for a type cannot be deleted.")
                .withEntityKey("guid", template.getGuid()).build();
        }
    }
    
    private String getKey(Template template) {
        return "template:" + template.getGuid();
    }
    
    private Criteria persistCriteria(Template template) {
        Criteria criteria = template.getCriteria();
        if (criteria == null) {
            criteria = Criteria.create();
        }
        criteria.setKey(getKey(template));
        return criteriaDao.createOrUpdateCriteria(criteria);
    }

    private void loadCriteria(Template template) {
        Criteria criteria = criteriaDao.getCriteria(getKey(template));
        if (criteria == null) {
            criteria = Criteria.create();
        }
        criteria.setKey(getKey(template));
        template.setCriteria(criteria);
    }
    
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }
    
    DateTime getTimestamp() {
        return DateTime.now(UTC);
    }
    
}

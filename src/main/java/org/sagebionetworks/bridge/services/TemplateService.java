package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOG = LoggerFactory.getLogger(TemplateService.class);

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

        List<Template> templateMatches = results.getItems().stream().filter(template -> {
            return CriteriaUtils.matchCriteria(context, template.getCriteria());
        }).collect(toImmutableList());
        
        // The ideal case: one and only one template matches the user's context
        if (templateMatches.size() == 1) {
            return templateMatches.get(0);
        }
        // If not, fall back to the default specified for this study, if it exists. 
        Study study = studyService.getStudy(context.getStudyIdentifier());
        String defaultGuid = study.getDefaultTemplates().get(type.name().toLowerCase());
        if (defaultGuid != null) {
            // Specified default may not exist, log as integrity violation, but continue
            Optional<Template> optional = templateDao.getTemplate(context.getStudyIdentifier(), defaultGuid);
            if (optional.isPresent()) {
                return optional.get();
            }
            LOG.warn("Default template " + defaultGuid + " no longer exists for template type" + type.name());
        }
        // NOTE: We will eventually validate that the study object has a default template specified
        // for every type of template, making the following scenarios effectively impossible.
        
        // Return a matching template
        if (templateMatches.size() > 1) {
            LOG.warn("Template matching ambiguous without a default, returning first matched template");
            return templateMatches.get(0);
        }
        // Return any template
        if (results.getItems().size() > 0) {
            LOG.warn("Template matching failed with no default, returning first template found without matching");
            return results.getItems().get(0);
        }
        // There is nothing to return
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
        } else if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException("pageSize must be in range 1-" + API_MAXIMUM_PAGE_SIZE);
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

        return new GuidVersionHolder(template.getGuid(), Long.valueOf(template.getVersion()));
    }
    
    public GuidVersionHolder updateTemplate(StudyIdentifier studyId, Template template) {
        checkNotNull(studyId);
        checkNotNull(template);
        
        // If the entity is deleted and staying deleted, don't allow this operation.
        Template existing = getTemplate(studyId, template.getGuid());
        if (existing.isDeleted() && template.isDeleted()) {
            throw new EntityNotFoundException(Template.class);
        }
        template.setStudyId(studyId.getIdentifier());
        template.setModifiedOn(getTimestamp());
        // no reason for these to be updated after creation
        template.setTemplateType(existing.getTemplateType());
        template.setCreatedOn(existing.getCreatedOn());
        
        Study study = studyService.getStudy(studyId);
        Set<String> substudyIds = substudyService.getSubstudyIds(study.getStudyIdentifier());
        
        TemplateValidator validator = new TemplateValidator(study.getDataGroups(), substudyIds);
        Validate.entityThrowingException(validator, template);

        if (template.isDeleted() && isDefaultTemplate(template, studyId)) {
            throw new ConstraintViolationException.Builder().withMessage("The default template for a type cannot be deleted.")
                .withEntityKey("guid", template.getGuid()).build();
        }
        
        persistCriteria(template);
        templateDao.updateTemplate(template);
        
        return new GuidVersionHolder(template.getGuid(), Long.valueOf(template.getVersion()));
    }
    
    public void deleteTemplate(StudyIdentifier studyId, String guid) {
        // This not only verifies the template exists, it verifies it is in the caller's study
        Template existing = getTemplate(studyId, guid);
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Template.class);
        }
        if (isDefaultTemplate(existing, studyId)) {
            throw new ConstraintViolationException.Builder().withMessage("The default template for a type cannot be deleted.")
                .withEntityKey("guid", guid).build();
        }
        
        existing.setDeleted(true);
        existing.setModifiedOn(getTimestamp());
        
        templateDao.updateTemplate(existing);
    }
    
    public void deleteTemplatePermanently(StudyIdentifier studyId, String guid) {
        // Throws exception if template doesn't exist
        Template template = getTemplate(studyId, guid);
        
        // You cannot delete the default template (logical or physical).
        if (isDefaultTemplate(template, studyId)) {
            throw new ConstraintViolationException.Builder().withMessage("The default template for a type cannot be deleted.")
                .withEntityKey("guid", guid).build();
        }
        templateDao.deleteTemplatePermanently(studyId, guid);
        criteriaDao.deleteCriteria(getKey(template));
    }

    private boolean isDefaultTemplate(Template template, StudyIdentifier studyId) {
        Study study = studyService.getStudy(studyId);
        String defaultGuid = study.getDefaultTemplates().get(template.getTemplateType().name().toLowerCase());
        
        return (template.getGuid().equals(defaultGuid));
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
        template.setCriteria(criteria);
    }
    
    String generateGuid() {
        return BridgeUtils.generateGuid();
    }
    
    DateTime getTimestamp() {
        return DateTime.now(UTC);
    }
    
}

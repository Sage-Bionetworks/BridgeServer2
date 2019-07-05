package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.models.studies.MimeType.HTML;
import static org.sagebionetworks.bridge.models.studies.MimeType.TEXT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_ACCOUNT_EXISTS;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_APP_INSTALL_LINK;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_RESET_PASSWORD;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_SIGNED_CONSENT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_SIGN_IN;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_VERIFY_EMAIL;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_ACCOUNT_EXISTS;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_APP_INSTALL_LINK;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_PHONE_SIGN_IN;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_RESET_PASSWORD;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_SIGNED_CONSENT;
import static org.sagebionetworks.bridge.models.templates.TemplateType.SMS_VERIFY_PHONE;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.dao.TemplateDao;
import org.sagebionetworks.bridge.dao.TemplateRevisionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateType;
import org.sagebionetworks.bridge.validators.TemplateValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class TemplateService {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateService.class);

    private Map<TemplateType,Triple<String,String,MimeType>> defaultTemplatesMap = new HashMap<>();
    
    private TemplateDao templateDao;
    private TemplateRevisionDao templateRevisionDao;
    private CriteriaDao criteriaDao;
    private StudyService studyService;
    private SubstudyService substudyService;
    
    private String defaultEmailVerificationTemplate;
    private String defaultEmailVerificationTemplateSubject;
    private String defaultResetPasswordTemplate;
    private String defaultResetPasswordTemplateSubject;
    private String defaultEmailSignInTemplate;
    private String defaultEmailSignInTemplateSubject;
    private String defaultAccountExistsTemplate;
    private String defaultAccountExistsTemplateSubject;
    private String defaultSignedConsentTemplate;
    private String defaultSignedConsentTemplateSubject;
    private String defaultAppInstallLinkTemplate;
    private String defaultAppInstallLinkTemplateSubject;
    private String defaultResetPasswordSmsTemplate;
    private String defaultPhoneSignInSmsTemplate;
    private String defaultAppInstallLinkSmsTemplate;
    private String defaultVerifyPhoneSmsTemplate;
    private String defaultAccountExistsSmsTemplate;
    private String defaultSignedConsentSmsTemplate;
    
    @Autowired
    final void setTemplateDao(TemplateDao templateDao) {
        this.templateDao = templateDao;
    }
    @Autowired
    final void setTemplateRevisionDao(TemplateRevisionDao templateRevisionDao) {
        this.templateRevisionDao = templateRevisionDao;
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

    @Value("classpath:conf/study-defaults/email-verification.txt")
    final void setDefaultEmailVerificationTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailVerificationTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/email-verification-subject.txt")
    final void setDefaultEmailVerificationTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailVerificationTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/reset-password.txt")
    final void setDefaultPasswordTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultResetPasswordTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/reset-password-subject.txt")
    final void setDefaultPasswordTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultResetPasswordTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/email-sign-in.txt")
    final void setDefaultEmailSignInTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailSignInTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/email-sign-in-subject.txt")
    final void setDefaultEmailSignInTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailSignInTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/account-exists.txt")
    final void setDefaultAccountExistsTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultAccountExistsTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/account-exists-subject.txt")
    final void setDefaultAccountExistsTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultAccountExistsTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/signed-consent.txt")
    final void setSignedConsentTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultSignedConsentTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/signed-consent-subject.txt")
    final void setSignedConsentTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultSignedConsentTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/app-install-link.txt")
    final void setAppInstallLinkTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultAppInstallLinkTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/study-defaults/app-install-link-subject.txt")
    final void setAppInstallLinkTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultAppInstallLinkTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("${sms.reset.password}")
    final void setResetPasswordSmsTemplate(String template) {
        this.defaultResetPasswordSmsTemplate = template;
    }
    @Value("${sms.phone.signin}")
    final void setPhoneSignInSmsTemplate(String template) {
        this.defaultPhoneSignInSmsTemplate = template;
    }
    @Value("${sms.app.install.link}")
    final void setAppInstallLinkSmsTemplate(String template) {
        this.defaultAppInstallLinkSmsTemplate = template;
    }
    @Value("${sms.verify.phone}")
    final void setVerifyPhoneSmsTemplate(String template) {
        this.defaultVerifyPhoneSmsTemplate = template;
    }
    @Value("${sms.account.exists}")
    final void setAccountExistsSmsTemplate(String template) {
        this.defaultAccountExistsSmsTemplate = template;
    }
    @Value("${sms.signed.consent}")
    final void setSignedConsentSmsTemplate(String template) {
        this.defaultSignedConsentSmsTemplate = template;
    }
    
    @PostConstruct
    public void makeDefaultTemplateMap() {
        defaultTemplatesMap.put(EMAIL_ACCOUNT_EXISTS, Triple.of(defaultAccountExistsTemplateSubject,
                defaultAccountExistsTemplate, HTML));
        defaultTemplatesMap.put(EMAIL_APP_INSTALL_LINK, Triple.of(defaultAppInstallLinkTemplateSubject,
                defaultAppInstallLinkTemplate, HTML));
        defaultTemplatesMap.put(EMAIL_RESET_PASSWORD, Triple.of(defaultResetPasswordTemplateSubject,
                defaultResetPasswordTemplate, HTML));
        defaultTemplatesMap.put(EMAIL_SIGN_IN, Triple.of(defaultEmailSignInTemplateSubject,
                defaultEmailSignInTemplate, HTML));
        defaultTemplatesMap.put(EMAIL_SIGNED_CONSENT, Triple.of(defaultSignedConsentTemplateSubject,
                defaultSignedConsentTemplate, HTML));
        defaultTemplatesMap.put(EMAIL_VERIFY_EMAIL, Triple.of(defaultEmailVerificationTemplateSubject,
                defaultEmailVerificationTemplate, HTML));
        defaultTemplatesMap.put(SMS_ACCOUNT_EXISTS, Triple.of(null, defaultAccountExistsSmsTemplate, TEXT));
        defaultTemplatesMap.put(SMS_APP_INSTALL_LINK, Triple.of(null, defaultAppInstallLinkSmsTemplate, TEXT));
        defaultTemplatesMap.put(SMS_PHONE_SIGN_IN, Triple.of(null, defaultPhoneSignInSmsTemplate, TEXT));
        defaultTemplatesMap.put(SMS_RESET_PASSWORD, Triple.of(null, defaultResetPasswordSmsTemplate, TEXT));
        defaultTemplatesMap.put(SMS_SIGNED_CONSENT, Triple.of(null, defaultSignedConsentSmsTemplate, TEXT));
        defaultTemplatesMap.put(SMS_VERIFY_PHONE, Triple.of(null, defaultVerifyPhoneSmsTemplate, TEXT));
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
        TemplateRevision revision = TemplateRevision.create();
        if (template.getTemplateType() != null) {
            Triple<String,String,MimeType> triple = defaultTemplatesMap.get(template.getTemplateType());
            revision.setSubject(triple.getLeft());
            revision.setDocumentContent(triple.getMiddle());
            revision.setMimeType(triple.getRight());
        }
        return migrateTemplate(studyId, template, revision);
    }
    
    public GuidVersionHolder migrateTemplate(StudyIdentifier studyId, Template template, TemplateRevision revision) {
        checkNotNull(studyId);
        checkNotNull(template);
        checkNotNull(revision);
        
        Study study = studyService.getStudy(studyId);
        Set<String> substudyIds = substudyService.getSubstudyIds(study.getStudyIdentifier());
        
        TemplateValidator validator = new TemplateValidator(study.getDataGroups(), substudyIds);
        Validate.entityThrowingException(validator, template);

        String templateGuid = generateGuid();
        DateTime timestamp = getTimestamp();
        String storagePath = templateGuid + "." + timestamp.getMillis();
        
        revision.setCreatedBy(getUserId());
        revision.setCreatedOn(timestamp);
        revision.setTemplateGuid(templateGuid);
        revision.setCreatedBy(getUserId());
        revision.setStoragePath(storagePath);
        
        templateRevisionDao.createTemplateRevision(revision);
        
        template.setStudyId(studyId.getIdentifier());
        template.setDeleted(false);
        template.setVersion(0);
        template.setGuid(templateGuid);
        template.setCreatedOn(timestamp);
        template.setModifiedOn(timestamp);
        template.setPublishedCreatedOn(revision.getCreatedOn());
        
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
    
    String getUserId() {
        return BridgeUtils.getRequestContext().getCallerUserId();
    }
}

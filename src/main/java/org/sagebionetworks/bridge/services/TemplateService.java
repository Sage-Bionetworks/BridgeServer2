    package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.models.apps.MimeType.HTML;
import static org.sagebionetworks.bridge.models.apps.MimeType.TEXT;
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
import org.sagebionetworks.bridge.RequestContext;
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
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.MimeType;
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
    private AppService appService;
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
    final void setAppService(AppService appService) {
        this.appService = appService;
    }
    @Autowired
    final void setSubstudyService(SubstudyService substudyService) {
        this.substudyService = substudyService;
    }

    @Value("classpath:conf/app-defaults/email-verification.txt")
    final void setDefaultEmailVerificationTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailVerificationTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/email-verification-subject.txt")
    final void setDefaultEmailVerificationTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailVerificationTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/reset-password.txt")
    final void setDefaultPasswordTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultResetPasswordTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/reset-password-subject.txt")
    final void setDefaultPasswordTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultResetPasswordTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/email-sign-in.txt")
    final void setDefaultEmailSignInTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailSignInTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/email-sign-in-subject.txt")
    final void setDefaultEmailSignInTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultEmailSignInTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/account-exists.txt")
    final void setDefaultAccountExistsTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultAccountExistsTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/account-exists-subject.txt")
    final void setDefaultAccountExistsTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultAccountExistsTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/signed-consent.txt")
    final void setSignedConsentTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultSignedConsentTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/signed-consent-subject.txt")
    final void setSignedConsentTemplateSubject(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultSignedConsentTemplateSubject = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/app-install-link.txt")
    final void setAppInstallLinkTemplate(org.springframework.core.io.Resource resource) throws IOException {
        this.defaultAppInstallLinkTemplate = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
    @Value("classpath:conf/app-defaults/app-install-link-subject.txt")
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
    
    public TemplateRevision getRevisionForUser(App app, TemplateType type) {
        RequestContext reqContext = BridgeUtils.getRequestContext();
        CriteriaContext context = new CriteriaContext.Builder()
            .withClientInfo(reqContext.getCallerClientInfo())
            .withLanguages(reqContext.getCallerLanguages())
            .withAppId(app.getIdentifier())
            .build();

        Template template = getTemplateForUser(app, context, type)
                .orElseThrow(() -> new EntityNotFoundException(Template.class));
        return templateRevisionDao.getTemplateRevision(template.getGuid(), template.getPublishedCreatedOn())
                .orElseThrow(() -> new EntityNotFoundException(TemplateRevision.class));
    }
    
    @SuppressWarnings("unchecked")
    Optional<Template> getTemplateForUser(App app, CriteriaContext context, TemplateType type) {
        checkNotNull(context);
        checkNotNull(type);

        ResourceList<Template> results = (ResourceList<Template>)templateDao.getTemplates(
                context.getAppId(), type, null, null, false);
        for (Template template : results.getItems()) {
            loadCriteria(template);
        }

        List<Template> templateMatches = CriteriaUtils.filterByCriteria(context, results.getItems(), null);
        
        // The ideal case: one and only one template matches the user's context
        if (templateMatches.size() == 1) {
            return Optional.of(templateMatches.get(0));
        }
        // If not, fall back to the default specified for this app, if it exists. 
        String defaultGuid = app.getDefaultTemplates().get(type.name().toLowerCase());
        if (defaultGuid != null) {
            // Specified default may not exist, log as integrity violation, but continue
            Optional<Template> optional = templateDao.getTemplate(context.getAppId(), defaultGuid);
            if (optional.isPresent()) {
                return optional;
            }
            LOG.warn("Default template " + defaultGuid + " no longer exists for template type" + type.name());
        }
        // Return a matching template
        if (templateMatches.size() > 1) {
            LOG.warn("Template matching ambiguous without a default, returning first matched template");
            return Optional.of(templateMatches.get(0));
        }
        // Return any template
        if (results.getItems().size() > 0) {
            LOG.warn("Template matching failed with no default, returning first template found without matching");
            return Optional.of(results.getItems().get(0));
        }
        // There is nothing to return
        return Optional.empty();
    }
    
    public PagedResourceList<? extends Template> getTemplatesForType(String appId, TemplateType type,
            Integer offset, Integer pageSize, boolean includeDeleted) {
        checkNotNull(appId);
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
        
        PagedResourceList<? extends Template> templates = templateDao.getTemplates(appId, type, offset, pageSize, includeDeleted);
        for (Template template : templates.getItems()) {
            loadCriteria(template);
        }
        return templates;
    }
    
    public Template getTemplate(String appId, String guid) {
        checkNotNull(appId);
        
        if (StringUtils.isBlank(guid)) {
            throw new BadRequestException("Invalid template guid: " + guid);
        }
        
        Template template = templateDao.getTemplate(appId, guid)
                .orElseThrow(() -> new EntityNotFoundException(Template.class));
        
        loadCriteria(template);
        return template;
    }

    public GuidVersionHolder createTemplate(App app, Template template) {
        TemplateRevision revision = TemplateRevision.create();
        if (template.getTemplateType() != null) {
            Triple<String,String,MimeType> triple = defaultTemplatesMap.get(template.getTemplateType());
            revision.setSubject(triple.getLeft());
            revision.setDocumentContent(triple.getMiddle());
            revision.setMimeType(triple.getRight());
        }
        
        Set<String> substudyIds = substudyService.getSubstudyIds(app.getIdentifier());
        
        TemplateValidator validator = new TemplateValidator(app.getDataGroups(), substudyIds);
        Validate.entityThrowingException(validator, template);

        String templateGuid = generateGuid();
        DateTime timestamp = getTimestamp();
        String storagePath = templateGuid + "." + timestamp.getMillis();

        template.setAppId(app.getIdentifier());
        template.setDeleted(false);
        template.setVersion(0);
        template.setGuid(templateGuid);
        template.setCreatedOn(timestamp);
        template.setModifiedOn(timestamp);
        template.setPublishedCreatedOn(timestamp);
        
        Criteria criteria = persistCriteria(template);
        template.setCriteria(criteria);
        
        revision.setCreatedBy(getUserId());
        revision.setCreatedOn(timestamp);
        revision.setTemplateGuid(templateGuid);
        revision.setStoragePath(storagePath);

        templateDao.createTemplate(template, null);
        templateRevisionDao.createTemplateRevision(revision);
        return new GuidVersionHolder(template.getGuid(), Long.valueOf(template.getVersion()));
    }
    
    public GuidVersionHolder updateTemplate(String appId, Template template) {
        checkNotNull(appId);
        checkNotNull(template);
        
        // If the entity is deleted and staying deleted, don't allow this operation.
        Template existing = getTemplate(appId, template.getGuid());
        if (existing.isDeleted() && template.isDeleted()) {
            throw new EntityNotFoundException(Template.class);
        }
        template.setAppId(appId);
        template.setModifiedOn(getTimestamp());
        // no reason for these to be updated after creation
        template.setTemplateType(existing.getTemplateType());
        template.setCreatedOn(existing.getCreatedOn());
        
        App app = appService.getApp(appId);
        Set<String> substudyIds = substudyService.getSubstudyIds(app.getIdentifier());
        
        TemplateValidator validator = new TemplateValidator(app.getDataGroups(), substudyIds);
        Validate.entityThrowingException(validator, template);

        if (template.isDeleted() && isDefaultTemplate(template, appId)) {
            throw new ConstraintViolationException.Builder().withMessage("The default template for a type cannot be deleted.")
                .withEntityKey("guid", template.getGuid()).build();
        }
        
        persistCriteria(template);
        templateDao.updateTemplate(template);
        
        return new GuidVersionHolder(template.getGuid(), Long.valueOf(template.getVersion()));
    }
    
    public void deleteTemplate(String appId, String guid) {
        // This not only verifies the template exists, it verifies it is in the caller's app
        Template existing = getTemplate(appId, guid);
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(Template.class);
        }
        if (isDefaultTemplate(existing, appId)) {
            throw new ConstraintViolationException.Builder()
                .withMessage("The default template for a type cannot be deleted.")
                .withEntityKey("guid", guid).build();
        }
        
        existing.setDeleted(true);
        existing.setModifiedOn(getTimestamp());
        
        templateDao.updateTemplate(existing);
    }
    
    public void deleteTemplatePermanently(String appId, String guid) {
        // Throws exception if template doesn't exist
        Template template = getTemplate(appId, guid);

        templateDao.deleteTemplatePermanently(appId, guid);
        criteriaDao.deleteCriteria(getKey(template));
    }
    
    public void deleteTemplatesForStudy(String appId) {
        templateDao.deleteTemplatesForApp(appId);
    }

    private boolean isDefaultTemplate(Template template, String appId) {
        App app = appService.getApp(appId);
        String defaultGuid = app.getDefaultTemplates().get(template.getTemplateType().name().toLowerCase());
        
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

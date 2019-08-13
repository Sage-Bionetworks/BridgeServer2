package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeUtils.templateTypeToLabel;
import static org.sagebionetworks.bridge.models.studies.MimeType.TEXT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateType;

/**
 * A temporary component that works with TemplateService to migrate templates out of the study 
 * object and into the enhanced Template tables.
 */
@Component
public class TemplateMigrationService {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateMigrationService.class);
    
    private TemplateService templateService;
    
    @Autowired
    final void setTemplateService(TemplateService templateService) {
        this.templateService = templateService;
    }
    
    /**
     * Updates a study to use a set of enhanced templates with a default revision. Does not change the study mapping
     * if it already exists, but if it doesn't, it will create a template and revision from the study object's existing 
     * template information. If the study is new and doesn't have existing template information, it is created from 
     * the system defaults. This call does not persist changes in the study. If the study fails to be updated, the 
     * templates should be picked up on a subsequent run of this method. Eventually the study should have a complete 
     * set of enhanced templates associated with it.
     * @param study
     * @return true if the Study's defaultTemplates mapping has been updated, false otherwise.
     */
    public boolean migrateTemplates(Study study) {
        StudyIdentifier studyId = study.getStudyIdentifier();
        Map<String,String> studyDefaults = study.getDefaultTemplates();
        // Shortcut studies that have been entirely  migrated.
        if (TemplateType.values().length == studyDefaults.size()) {
            LOG.debug("Study " + study.getIdentifier() + " templates have been migrated");
            return false;
        }
        
        Map<String,String> defaultTemplates = new HashMap<>();
        
        // #1 Copy over existing, functional default references already set in the study
        for (Map.Entry<String, String> entry : studyDefaults.entrySet()) {
            String typeName = entry.getKey();
            String templateGuid = entry.getValue();
            // Try and load the template and revision to ensure they are consistent
            if (hasValidTemplate(studyId, templateGuid)) {
                defaultTemplates.put(typeName, templateGuid);
            }
        }
        
        // Now fill the remaining templates in any of a few ways
        for (TemplateType type : TemplateType.values()) {
            String typeName = type.name().toLowerCase();
            // #2 Look for a valid template that can be set as the study's default (data integrity or picking up
            // external saves to system)
            if (defaultTemplates.get(typeName) == null) {
                String templateGuid = findValidTemplateGuid(studyId, type);
                if (templateGuid != null) {
                    defaultTemplates.put(typeName, templateGuid);
                }
            }
            // #3 Migrate any study object template, since this is no persisted template that would take precedence (migration case)
            if (defaultTemplates.get(typeName) == null) {
                migrateExistingTemplate(study, defaultTemplates, type);
            }
            // #4 If still missing, create a template from the services's defaults (new study case)
            if (defaultTemplates.get(typeName) == null) {
                createNewTemplate(study, defaultTemplates, type);
            }
        }
        
        // If there are no changes, return false and don't set the mapping
        MapDifference<String,String> diff = Maps.difference(defaultTemplates, study.getDefaultTemplates());
        if (diff.areEqual()) {
            return false;
        }
        // Otherwise set the mapping and return true
        study.setDefaultTemplates(defaultTemplates);
        return true;
    }
    
    String findValidTemplateGuid(StudyIdentifier studyId, TemplateType type) {
        List<? extends Template> templates = templateService.getTemplatesForType(studyId, type, 0, 50, false).getItems();
        for (Template oneTemplate : templates) {
            if (oneTemplate.getPublishedCreatedOn() != null) {
                return oneTemplate.getGuid();
            }
        }
        return null;
    }
    
    boolean hasValidTemplate(StudyIdentifier studyId, String templateGuid) {
        if (templateGuid != null) {
            try {
                Template template = templateService.getTemplate(studyId, templateGuid);
                return (template.getPublishedCreatedOn() != null);
            } catch(Exception e) {
                LOG.error("Error verifying template " + templateGuid, e);
                return false;
            }
        }
        return false;
    }
    
    void migrateExistingTemplate(Study study, Map<String, String> defaultTemplates, TemplateType type) {
        TemplateRevision revision = getRevisionFromStudy(study, type);
        if (revision != null) {
            Template template = Template.create();
            template.setName(templateTypeToLabel(type));
            template.setTemplateType(type);
            GuidVersionHolder keys = templateService.migrateTemplate(study, template, revision);
            defaultTemplates.put(type.name().toLowerCase(), keys.getGuid());        
        }
    }
    
    void createNewTemplate(Study study, Map<String, String> defaultTemplates, TemplateType type) {
        Template template = Template.create();
        template.setName(templateTypeToLabel(type));
        template.setTemplateType(type);
        GuidVersionHolder keys = templateService.createTemplate(study, template);
        defaultTemplates.put(type.name().toLowerCase(), keys.getGuid());        
    }
    
    public static TemplateRevision getRevisionFromStudy(Study study, TemplateType type) {
        checkNotNull(study);
        checkNotNull(type);
        switch(type) {
        case EMAIL_ACCOUNT_EXISTS:
            return emailTemplateToRevision(study.getAccountExistsTemplate());
        case EMAIL_APP_INSTALL_LINK:
            return emailTemplateToRevision(study.getAppInstallLinkTemplate());
        case EMAIL_RESET_PASSWORD:
            return emailTemplateToRevision(study.getResetPasswordTemplate());
        case EMAIL_SIGN_IN:
            return emailTemplateToRevision(study.getEmailSignInTemplate());
        case EMAIL_SIGNED_CONSENT:
            return emailTemplateToRevision(study.getSignedConsentTemplate());
        case EMAIL_VERIFY_EMAIL:
            return emailTemplateToRevision(study.getVerifyEmailTemplate());
        case SMS_ACCOUNT_EXISTS:
            return smsTemplateToRevision(study.getAccountExistsSmsTemplate());
        case SMS_APP_INSTALL_LINK:
            return smsTemplateToRevision(study.getAppInstallLinkSmsTemplate());
        case SMS_PHONE_SIGN_IN:
            return smsTemplateToRevision(study.getPhoneSignInSmsTemplate());
        case SMS_RESET_PASSWORD:
            return smsTemplateToRevision(study.getResetPasswordSmsTemplate());
        case SMS_SIGNED_CONSENT:
            return smsTemplateToRevision(study.getSignedConsentSmsTemplate());
        case SMS_VERIFY_PHONE:            
            return smsTemplateToRevision(study.getVerifyPhoneSmsTemplate());
        }
        throw new BridgeServiceException("Template type does not match template: " + type);
    }
    
    static TemplateRevision emailTemplateToRevision(EmailTemplate template) {
        if (template == null) {
            return null;
        }
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject(template.getSubject());
        revision.setDocumentContent(template.getBody());
        revision.setMimeType(template.getMimeType());
        return revision;
    }
    
    static TemplateRevision smsTemplateToRevision(SmsTemplate template) {
        if (template == null) {
            return null;
        }
        TemplateRevision revision = TemplateRevision.create();
        revision.setDocumentContent(template.getMessage());
        revision.setMimeType(TEXT);
        return revision;
    }
}
package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;
import static org.sagebionetworks.bridge.services.SmsService.SMS_CHARACTER_LIMIT;

import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.templates.TemplateRevision;
import org.sagebionetworks.bridge.models.templates.TemplateType;

public class TemplateRevisionValidator implements Validator {
    private final TemplateType type;
    
    public TemplateRevisionValidator(TemplateType type) {
        this.type = type;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return TemplateRevision.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        TemplateRevision revision = (TemplateRevision)object;
        
        if (isBlank(revision.getTemplateGuid())) {
            errors.rejectValue("templateGuid", "cannot be blank");
        }
        if (revision.getCreatedOn() == null) {
            errors.rejectValue("createdOn", "cannot be null");
        }
        if (isBlank(revision.getCreatedBy())) {
            errors.rejectValue("createdBy", "cannot be blank");
        }
        if (isBlank(revision.getStoragePath())) {
            errors.rejectValue("storagePath", "cannot be blank");
        }
        if (revision.getMimeType() == null) {
            errors.rejectValue("mimeType", "cannot be null");
        }
        if (TemplateType.EMAIL_TYPES.contains(type)) {
            validateEmailTemplate(errors, revision, type.getRequiredVariables());
        } else {
            validateSmsTemplate(errors, revision, type.getRequiredVariables());
        }        
        
        if (revision.getSubject() != null && revision.getSubject().length() > 250) {
            errors.rejectValue("subject", "must be 250 characters of less");
        }
    }
    
    private void validateEmailTemplate(Errors errors, TemplateRevision revision, Set<String> templateVariables) {
        if (isBlank(revision.getSubject())) {
            errors.rejectValue("subject", "cannot be blank");
        } else if (revision.getSubject().length() > 250) {
            errors.rejectValue("subject", "cannot be more than 250 characters");
        }
        if (isBlank(revision.getDocumentContent())) {
            errors.rejectValue("documentContent", "cannot be blank");
        } else if (!templateVariables.isEmpty()) {
            validateVarExists(errors, revision, templateVariables);
        }
    }
    
    private void validateSmsTemplate(Errors errors, TemplateRevision revision, Set<String> templateVariables) {
        // This is not necessarily going to prevent the message from be split because the template variables haven't
        // been substituted. We do calculate this more accurately in the app manager right now.
        if (isBlank(revision.getDocumentContent())) {
            errors.rejectValue("documentContent", "cannot be blank");
        } else if (revision.getDocumentContent().length() > SMS_CHARACTER_LIMIT) {
            errors.rejectValue("documentContent", "cannot be more than " + SMS_CHARACTER_LIMIT + " characters");
        } else if (!templateVariables.isEmpty()){
            validateVarExists(errors, revision, templateVariables);
        }
    }

    private void validateVarExists(Errors errors, TemplateRevision revision, Set<String> templateVariables) {
        boolean missingTemplateVariable = true;
        for (String templateVar : templateVariables) {
            if (revision.getDocumentContent().contains(templateVar)) {
                missingTemplateVariable = false;
                break;
            }
        }
        if (missingTemplateVariable) {
            errors.rejectValue("documentContent", "must contain one of these template variables: "
                + COMMA_SPACE_JOINER.join(templateVariables));
        }
    }    
}

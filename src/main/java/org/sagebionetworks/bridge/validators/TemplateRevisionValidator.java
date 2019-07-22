package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.templates.TemplateRevision;

public class TemplateRevisionValidator implements Validator {

    public static final TemplateRevisionValidator INSTANCE = new TemplateRevisionValidator();
    
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
        if (revision.getSubject() != null && revision.getSubject().length() > 250) {
            errors.rejectValue("subject", "must be 250 characters of less");
        }
    }

}

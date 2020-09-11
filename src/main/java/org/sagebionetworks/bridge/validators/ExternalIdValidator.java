package org.sagebionetworks.bridge.validators;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.services.StudyService;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ExternalIdValidator implements Validator {

    private static final String IDENTIFIER_PATTERN = "^[a-zA-Z0-9_-]+$";
    
    private StudyService studyService;
    private boolean isV3;
    
    public ExternalIdValidator(StudyService studyService, boolean isV3) {
        this.studyService = studyService;
        this.isV3 = isV3;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return ExternalIdentifier.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        ExternalIdentifier extId = (ExternalIdentifier)object;

        String callerAppId = RequestContext.get().getCallerAppId();
        Set<String> callerStudies = RequestContext.get().getCallerStudies();
        
        if (StringUtils.isBlank(extId.getIdentifier())) {
            errors.rejectValue("identifier", "cannot be null or blank");
        } else if (!extId.getIdentifier().matches(IDENTIFIER_PATTERN)) {
            String msg = String.format("'%s' must contain only digits, letters, underscores and dashes", extId.getIdentifier());
            errors.rejectValue("identifier", msg);
        }
        if (!isV3) {
            String appId = StringUtils.isBlank(extId.getAppId()) ? null : extId.getAppId();
            if (StringUtils.isBlank(extId.getStudyId())) {
                errors.rejectValue("studyId", "cannot be null or blank");
            } else if (studyService.getStudy(appId, extId.getStudyId(), false) == null) {
                errors.rejectValue("studyId", "is not a valid study");
            } else if (!callerStudies.isEmpty() && !callerStudies.contains(extId.getStudyId())) {
                errors.rejectValue("studyId", "is not a valid study");
            }
        }
        if (StringUtils.isBlank(extId.getAppId())) {
            errors.rejectValue("appId", "cannot be null or blank");
        } else if (!extId.getAppId().equals(callerAppId)) {
            errors.rejectValue("appId", "is not a valid app");
        }
    }
}

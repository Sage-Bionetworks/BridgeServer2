package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_EMPTY;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

public class SubpopulationValidator implements Validator {

    private Set<String> dataGroups;
    private Set<String> studyIds;
    
    public SubpopulationValidator(Set<String> dataGroups, Set<String> studyIds) {
        this.dataGroups = dataGroups;
        this.studyIds = studyIds;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Subpopulation.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Subpopulation subpop = (Subpopulation)object;
        
        if (subpop.getAppId() == null) {
            errors.rejectValue("appId", CANNOT_BE_NULL);
        }
        if (isBlank(subpop.getName())) {
            errors.rejectValue("name", CANNOT_BE_BLANK);
        }
        if (isBlank(subpop.getGuidString())) {
            errors.rejectValue("guid", CANNOT_BE_BLANK);
        }
        for (String dataGroup : subpop.getDataGroupsAssignedWhileConsented()) {
            if (!dataGroups.contains(dataGroup)) {
                String listStr = (dataGroups.isEmpty()) ? "<empty>" : COMMA_SPACE_JOINER.join(dataGroups);
                String message = String.format("'%s' is not in enumeration: %s", dataGroup, listStr);
                errors.rejectValue("dataGroupsAssignedWhileConsented", message);
            }
        }
        if (subpop.getStudyIdsAssignedOnConsent().isEmpty()) {
            errors.rejectValue("studyIdsAssignedOnConsent", CANNOT_BE_EMPTY);
        } else {
            for (String studyId : subpop.getStudyIdsAssignedOnConsent()) {
                if (!studyIds.contains(studyId)) {
                    String listStr = COMMA_SPACE_JOINER.join(studyIds);
                    String message = String.format("'%s' is not in enumeration: %s", studyId, listStr);
                    errors.rejectValue("studyIdsAssignedOnConsent", message);
                }
            }
        }
        errors.pushNestedPath("criteria");
        CriteriaUtils.validate(subpop.getCriteria(), dataGroups, studyIds, errors);
        errors.popNestedPath();
    }
}

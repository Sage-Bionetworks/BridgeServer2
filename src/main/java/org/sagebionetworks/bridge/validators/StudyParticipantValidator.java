package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthUtils.IS_ORG_MEMBER;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.Iterables;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.OrganizationService;
import org.sagebionetworks.bridge.services.StudyService;

public class StudyParticipantValidator implements Validator {

    // see https://owasp.org/www-community/OWASP_Validation_Regex_Repository
    private static final String OWASP_REGEXP_VALID_EMAIL = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    private final StudyService studyService;
    private final OrganizationService organizationService;
    private final App app;
    private final boolean isNew;
    
    public StudyParticipantValidator(StudyService studyService, OrganizationService organizationService, App app,
            boolean isNew) {
        this.studyService = studyService;
        this.organizationService = organizationService;
        this.app = app;
        this.isNew = isNew;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return StudyParticipant.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        StudyParticipant participant = (StudyParticipant)object;
        
        if (isNew) {
            Phone phone = participant.getPhone();
            String email = participant.getEmail();
            String anyExternalId = participant.getExternalIds().isEmpty() ? null : 
                Iterables.getFirst(participant.getExternalIds().entrySet(), null).getValue();
            String synapseUserId = participant.getSynapseUserId();
            if (email == null && isBlank(anyExternalId) && phone == null && isBlank(synapseUserId)) {
                errors.reject("email, phone, synapseUserId or externalId is required");
            }
            // If provided, phone must be valid
            if (phone != null && !Phone.isValid(phone)) {
                errors.rejectValue("phone", "does not appear to be a phone number");
            }
            // If provided, email must be valid. Commons email validator v1.7 causes our test to 
            // fail because the word "test" appears in the user name, for reasons I could not 
            // deduce from their code. So we have switched to using OWASP regular expression to 
            // match valid email addresses.
            if (email != null && !email.matches(OWASP_REGEXP_VALID_EMAIL)) {
                errors.rejectValue("email", "does not appear to be an email address");
            }
            // External ID is required for non-administrative accounts when it is required on sign-up.
            if (participant.getRoles().isEmpty() && app.isExternalIdRequiredOnSignup() && participant.getExternalIds().isEmpty()) {
                errors.rejectValue("externalId", "is required");
            }
            // Password is optional, but validation is applied if supplied, any time it is 
            // supplied (such as in the password reset workflow).
            String password = participant.getPassword();
            if (password != null) {
                PasswordPolicy passwordPolicy = app.getPasswordPolicy();
                ValidatorUtils.validatePassword(errors, passwordPolicy, password);
            }
            
            // After account creation, organizational membership cannot be changed by updating an account
            // Instead, use the OrganizationService
            if (isNotBlank(participant.getOrgMembership())) {
                String orgId = participant.getOrgMembership();
                Optional<Organization> opt = organizationService.getOrganizationOpt(app.getIdentifier(), orgId);
                if (!opt.isPresent()) {
                    errors.rejectValue("orgMembership", "is not a valid organization");
                } else if (!IS_ORG_MEMBER.check(ORG_ID, orgId)) {
                    errors.rejectValue("orgMembership", "cannot be set by caller");
                }
            }
            // External IDs can be updated during creation or on update. If it's already assigned to another user, 
            // the database constraints will prevent this record's persistence.
            if (isNotBlank(participant.getExternalId()) && participant.getExternalIds().isEmpty()) {
                errors.rejectValue("externalId", "must now be supplied in the externalIds property that maps a study ID to the new external ID");
            }
            if (participant.getExternalIds() != null) {
                for (Map.Entry<String, String> entry : participant.getExternalIds().entrySet()) {
                    String studyId = entry.getKey();
                    String externalId = entry.getValue();
                    Study study = studyService.getStudy(app.getIdentifier(), studyId, false);
                    if (study == null) {
                        errors.rejectValue("externalIds["+studyId+"]", "is not a study");
                    }
                    if (isBlank(externalId)) {
                        errors.rejectValue("externalIds["+studyId+"].externalId", "cannot be blank");
                    }
                }
            }
        } else {
            if (isBlank(participant.getId())) {
                errors.rejectValue("id", "is required");
            }
        }

        if (participant.getSynapseUserId() != null && isBlank(participant.getSynapseUserId())) {
            errors.rejectValue("synapseUserId", "cannot be blank");
        }
                
        for (String dataGroup : participant.getDataGroups()) {
            if (!app.getDataGroups().contains(dataGroup)) {
                errors.rejectValue("dataGroups", messageForSet(app.getDataGroups(), dataGroup));
            }
        }
        for (String attributeName : participant.getAttributes().keySet()) {
            if (!app.getUserProfileAttributes().contains(attributeName)) {
                errors.rejectValue("attributes", messageForSet(app.getUserProfileAttributes(), attributeName));
            }
        }
    }

    private String messageForSet(Set<String> set, String fieldName) {
        return String.format("'%s' is not defined for app (use %s)", 
                fieldName, BridgeUtils.COMMA_SPACE_JOINER.join(set));
    }
}

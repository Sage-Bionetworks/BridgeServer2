package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.AuthEvaluatorField.ORG_ID;
import static org.sagebionetworks.bridge.AuthUtils.CAN_EDIT_MEMBERS;
import static org.sagebionetworks.bridge.BridgeConstants.OWASP_REGEXP_VALID_EMAIL;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EMAIL_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_PHONE_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.TIME_ZONE_ERROR;

import java.time.DateTimeException;
import java.time.ZoneId;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.OrganizationService;
import org.sagebionetworks.bridge.services.StudyService;

public class StudyParticipantValidator implements Validator {

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
            // if the call is anonymous, then the account must include email or phone
            if (RequestContext.get().getCallerUserId() == null && phone == null && email == null) {
                errors.reject("email or phone number is required");   
            } else if (!ValidatorUtils.participantHasValidIdentifier(participant)) {
                errors.reject("email, phone, synapseUserId or externalId is required");
            }
            if (phone != null && !Phone.isValid(phone)) {
                errors.rejectValue("phone", INVALID_PHONE_ERROR);
            }
            // If provided, email must be valid. Commons email validator v1.7 causes our test to 
            // fail because the word "test" appears in the user name, so we have switched to using 
            // OWASP regular expression to match valid email addresses.
            if (email != null && !email.matches(OWASP_REGEXP_VALID_EMAIL)) {
                errors.rejectValue("email", INVALID_EMAIL_ERROR);
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
                } else if (!CAN_EDIT_MEMBERS.check(ORG_ID, orgId)) {
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
                        errors.rejectValue("externalIds["+studyId+"].externalId", CANNOT_BE_BLANK);
                    }
                }
            }
        } else {
            if (isBlank(participant.getId())) {
                errors.rejectValue("id", "is required");
            }
        }

        if (participant.getSynapseUserId() != null && isBlank(participant.getSynapseUserId())) {
            errors.rejectValue("synapseUserId", CANNOT_BE_BLANK);
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
        if (participant.getClientTimeZone() != null) {
            try {
                ZoneId.of(participant.getClientTimeZone());
            } catch (DateTimeException e) {
                errors.rejectValue("clientTimeZone", TIME_ZONE_ERROR);
            }
        }
    }

    private String messageForSet(Set<String> set, String fieldName) {
        return String.format("'%s' is not defined for app (use %s)", 
                fieldName, BridgeUtils.COMMA_SPACE_JOINER.join(set));
    }
}

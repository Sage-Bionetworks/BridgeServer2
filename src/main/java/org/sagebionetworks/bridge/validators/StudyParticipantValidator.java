package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.ExternalIdService;
import org.sagebionetworks.bridge.services.SubstudyService;

public class StudyParticipantValidator implements Validator {

    private static final EmailValidator EMAIL_VALIDATOR = EmailValidator.getInstance();
    private final ExternalIdService externalIdService;
    private final SubstudyService substudyService;
    private final Study study;
    private final boolean isNew;
    
    public StudyParticipantValidator(ExternalIdService externalIdService, SubstudyService substudyService, Study study,
            boolean isNew) {
        this.externalIdService = externalIdService;
        this.substudyService = substudyService;
        this.study = study;
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
            String externalId = participant.getExternalId();
            String synapseUserId = participant.getSynapseUserId();
            if (email == null && isBlank(externalId) && phone == null && isBlank(synapseUserId)) {
                errors.reject("email, phone, synapseUserId or externalId is required");
            }
            // If provided, phone must be valid
            if (phone != null && !Phone.isValid(phone)) {
                errors.rejectValue("phone", "does not appear to be a phone number");
            }
            // If provided, email must be valid
            if (email != null && !EMAIL_VALIDATOR.isValid(email)) {
                errors.rejectValue("email", "does not appear to be an email address");
            }
            // External ID is required for non-administrative accounts when it is required on sign-up. Whether you're 
            // a researcher or not, however, if you add an external ID and we're managing them, we're going to validate
            // that yours is correct.
            if (participant.getRoles().isEmpty() && study.isExternalIdRequiredOnSignup() && isBlank(participant.getExternalId())) {
                errors.rejectValue("externalId", "is required");
            }
            // Password is optional, but validation is applied if supplied, any time it is 
            // supplied (such as in the password reset workflow).
            String password = participant.getPassword();
            if (password != null) {
                PasswordPolicy passwordPolicy = study.getPasswordPolicy();
                ValidatorUtils.validatePassword(errors, passwordPolicy, password);
            }
        } else {
            if (isBlank(participant.getId())) {
                errors.rejectValue("id", "is required");
            }
        }

        for (String substudyId : participant.getSubstudyIds()) {
            Substudy substudy = substudyService.getSubstudy(study.getStudyIdentifier(), substudyId, false);
            if (substudy == null) {
                errors.rejectValue("substudyIds["+substudyId+"]", "is not a substudy");
            }
        }
        
        // External ID can be updated during creation or on update. If it's already assigned to another user, 
        // the database constraints will prevent this record's persistence.
        if (isNotBlank(participant.getExternalId())) {
            Optional<ExternalIdentifier> optionalId = externalIdService.getExternalId(study.getStudyIdentifier(),
                    participant.getExternalId());
            if (!optionalId.isPresent()) {
                errors.rejectValue("externalId", "is not a valid external ID");
            }
        }
        // Never okay to have a blank external ID. It can produce errors later when querying for ext IDs
        if (participant.getExternalId() != null && isBlank(participant.getExternalId())) {
            errors.rejectValue("externalId", "cannot be blank");
        }
        
        if (participant.getSynapseUserId() != null && isBlank(participant.getSynapseUserId())) {
            errors.rejectValue("synapseUserId", "cannot be blank");
        }
                
        for (String dataGroup : participant.getDataGroups()) {
            if (!study.getDataGroups().contains(dataGroup)) {
                errors.rejectValue("dataGroups", messageForSet(study.getDataGroups(), dataGroup));
            }
        }
        for (String attributeName : participant.getAttributes().keySet()) {
            if (!study.getUserProfileAttributes().contains(attributeName)) {
                errors.rejectValue("attributes", messageForSet(study.getUserProfileAttributes(), attributeName));
            }
        }
    }
    
    private String messageForSet(Set<String> set, String fieldName) {
        return String.format("'%s' is not defined for study (use %s)", 
                fieldName, BridgeUtils.COMMA_SPACE_JOINER.join(set));
    }
}

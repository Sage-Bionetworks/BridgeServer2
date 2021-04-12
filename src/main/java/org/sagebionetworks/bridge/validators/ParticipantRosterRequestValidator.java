package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.models.ParticipantRosterRequest;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Validator for {@link org.sagebionetworks.bridge.models.ParticipantRosterRequest}. */
public class ParticipantRosterRequestValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return ParticipantRosterRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        if (target == null) {
            errors.rejectValue("ParticipantRosterRequest", Validate.CANNOT_BE_NULL);
        } else if (!(target instanceof ParticipantRosterRequest)) {
            errors.rejectValue("ParticipantRosterRequest", Validate.WRONG_TYPE);
        } else {
            ParticipantRosterRequest request = (ParticipantRosterRequest) target;

            // password
            if (request.getPassword() == null) {
                errors.rejectValue("password", Validate.CANNOT_BE_NULL);
            } else if (request.getPassword().isEmpty()) {
                errors.rejectValue("password", Validate.CANNOT_BE_BLANK);
            }

            // studyId
            if (request.getStudyId() == null) {
                errors.rejectValue("studyId", Validate.CANNOT_BE_NULL);
            }
        }
    }
}

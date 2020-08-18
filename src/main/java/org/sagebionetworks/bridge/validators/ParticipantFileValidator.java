package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ParticipantFileValidator implements Validator {
    // Singleton.
    public static final ParticipantFileValidator INSTANCE = new ParticipantFileValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return ParticipantFile.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        ParticipantFile file = (ParticipantFile) object;

        if (StringUtils.isBlank(file.getUserId())) {
            errors.rejectValue("userId", "is required");
        }
        if (StringUtils.isBlank(file.getFileId())) {
            errors.rejectValue("fileId", "is required");
        }
        if (StringUtils.isBlank(file.getMimeType())) {
            errors.rejectValue("mimeType", "is required");
        }
        if (StringUtils.isBlank(file.getAppId())) {
            errors.rejectValue("appId", "is required");
        }
    }
}

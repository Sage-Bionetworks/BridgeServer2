package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.files.FileRevision;

import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

public class FileRevisionValidator implements Validator {
    public static final FileRevisionValidator INSTANCE = new FileRevisionValidator();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return FileRevision.class.isAssignableFrom(clazz);
    }
    
    @Override
    public void validate(Object object, Errors errors) {
        FileRevision file = (FileRevision)object;
        
        if (StringUtils.isBlank(file.getFileGuid())) {
            errors.rejectValue("fileGuid", "is required");
        }
        if (StringUtils.isBlank(file.getName())) {
            errors.rejectValue("name", "is required");
        }
        validateStringLength(errors, 255, file.getName(), "name");
        if (StringUtils.isBlank(file.getMimeType())) {
            errors.rejectValue("mimeType", "is required");
        }
        validateStringLength(errors, 255, file.getMimeType(), "mimeType");
        validateStringLength(errors, TEXT_SIZE, file.getDescription(), "description");
        validateStringLength(errors, 1024, file.getUploadURL(), "uploadUrl");
    }
}

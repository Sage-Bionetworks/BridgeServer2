package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import org.sagebionetworks.bridge.models.files.FileMetadata;

public class FileMetadataValidator implements Validator {
    public static final FileMetadataValidator INSTANCE = new FileMetadataValidator();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return FileMetadata.class.isAssignableFrom(clazz);
    }
    
    @Override
    public void validate(Object object, Errors errors) {
        FileMetadata file = (FileMetadata)object;
        
        if (StringUtils.isBlank(file.getName())) {
            errors.rejectValue("name", "is required");
        }
        validateStringLength(errors, 255, file.getName(), "name");
        if (file.getDisposition() == null) {
            errors.rejectValue("disposition", CANNOT_BE_NULL);
        }
        validateStringLength(errors, TEXT_SIZE, file.getDescription(), "description");
    }

}

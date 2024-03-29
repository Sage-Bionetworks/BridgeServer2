package org.sagebionetworks.bridge.validators;

import static java.nio.charset.Charset.defaultCharset;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;

import org.apache.commons.codec.binary.Base64;
import org.sagebionetworks.bridge.models.upload.UploadRequest;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class UploadValidator implements Validator {
    public static final UploadValidator INSTANCE = new UploadValidator();

    private static final long MAX_UPLOAD_SIZE = 50L * 1000L * 1000L; // 50 MB
    private static final int MD5_BYTE_LENGTH = 16; // 16 bytes

    @Override
    public boolean supports(Class<?> clazz) {
        return UploadRequest.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        UploadRequest uploadRequest = (UploadRequest) object;

        final String name = uploadRequest.getName();
        if (name == null || name.isEmpty()) {
            errors.rejectValue("name", CANNOT_BE_BLANK);
        }
        final String contentType = uploadRequest.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            errors.rejectValue("contentType", CANNOT_BE_BLANK);
        }
        final long length = uploadRequest.getContentLength();
        if (length <= 0L) {
            errors.rejectValue("contentLength", "Invalid content length. Must be > 0.");
        }
        if (length > MAX_UPLOAD_SIZE) {
            errors.rejectValue("contentLength", "Content length is above the allowed maximum.");
        }
        final String base64md5 = uploadRequest.getContentMd5();
        if (base64md5 == null || base64md5.isEmpty()) {
            errors.rejectValue("contentMd5", "MD5 must not be empty.");
        } else {
            try {
                final byte[] md5Bytes = Base64.decodeBase64(base64md5.getBytes(defaultCharset()));
                if (md5Bytes.length != MD5_BYTE_LENGTH) {
                    errors.rejectValue("contentMd5", "MD5 hash must be 16 bytes.");
                }
            } catch (Exception e) {
                errors.rejectValue("contentMd5", "MD5 is not base64 encoded.");
            }
        }
    }
}

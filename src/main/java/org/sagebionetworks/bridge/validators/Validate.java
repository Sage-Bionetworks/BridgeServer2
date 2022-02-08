package org.sagebionetworks.bridge.validators;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.Validator;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Validate {
    
    // see https://owasp.org/www-community/OWASP_Validation_Regex_Repository
    static final String OWASP_REGEXP_VALID_EMAIL = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

    /** A common string constraint Synapse places on model identifiers. */
    static final String SYNAPSE_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_-]+$";
    
    /** The pattern used to validate activity event keys and automatic custom event keys. */
    static final String BRIDGE_EVENT_ID_PATTERN = "^[a-zA-Z0-9_-]+$";
    
    /** An identifier field that can contain spaces, some punctuation (but not colons) where it's infeasible 
     * to include a separate label and unnecessary to restrict the string for external systems like Synapse. 
     */
    static final String BRIDGE_RELAXED_ID_PATTERN = "^[^:]+$";

    /** A common string constraint we place on model identifiers. */
    static final String BRIDGE_IDENTIFIER_PATTERN = "^[a-z0-9-]+$";

    /** The pattern of a valid JavaScript variable/object property name. */
    static final String JS_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_][a-zA-Z0-9_-]*$";

    static final String BRIDGE_EVENT_ID_ERROR = "must contain only lower- or upper-case letters, numbers, dashes, and/or underscores";
    static final String BRIDGE_IDENTIFIER_ERROR = "must contain only lower-case letters and/or numbers with optional dashes";
    static final String BRIDGE_RELAXED_ID_ERROR = "cannot contain colons";
    
    static final String CANNOT_BE_BLANK = "%s cannot be null or blank";
    static final String CANNOT_BE_DUPLICATE = "cannot duplicate an earlier value";
    static final String CANNOT_BE_EMPTY = "cannot be empty";
    static final String CANNOT_BE_EMPTY_STRING = "cannot be an empty string";
    static final String CANNOT_BE_NEGATIVE = "cannot be negative";
    static final String CANNOT_BE_NULL = "%s cannot be null";
    static final String CANNOT_BE_NULL_OR_EMPTY = "cannot be null or empty";
    static final String CANNOT_BE_ZERO_OR_NEGATIVE = "cannot be zero or negative";
    static final String INVALID_EMAIL_ERROR = "does not appear to be an email address";
    static final String INVALID_EVENT_ID = "is not a valid custom event ID";
    static final String INVALID_PHONE_ERROR = "does not appear to be a phone number";
    static final String INVALID_TIME_ZONE = "is not a recognized IANA time zone name (eg. “America/Los_Angeles”)";
    static final String INVALID_TYPE = "is the wrong type";

    private static Errors getErrorsFor(Object object) {
        String entityName = BridgeUtils.getTypeName(object.getClass());
        return new MapBindingResult(Maps.newHashMap(), entityName);
    }
    
    /**
     * This method will validate an object (not an entity, that is, not an object that is 
     * defined in the API and usually represented by a JSON payload), throwing a 
     * BadRequestException if validation fails.
     * @param validator
     * @param object
     * @throws BadRequestException
     */
    public static void nonEntityThrowingException(Validator validator, Object object) throws BadRequestException {
        checkNotNull(validator);
        checkArgument(validator.supports(object.getClass()), "Invalid validator");
        checkNotNull(object);
        
        Errors errors = getErrorsFor(object);
        validator.validate(object, errors);
        if (errors.hasErrors()) {
            String message = convertErrorToMessage(errors);
            throw new BadRequestException(message);
        }
    }
    
    /**
     * This method validates an entity (an object that is defined in the API), and throws an 
     * InvalidEntityException when validation fails.
     * @param validator
     * @param object
     * @throws InvalidEntityException
     */
    public static void entityThrowingException(Validator validator, Object object) throws InvalidEntityException {
        checkNotNull(validator);
        checkArgument(object instanceof BridgeEntity);
        checkArgument(validator.supports(object.getClass()), "Invalid validator");
        checkNotNull(object);
        
        Errors errors = getErrorsFor(object);
        validator.validate(object, errors);
        throwException(errors, (BridgeEntity)object);
    }
    
    static void entity(Validator validator, Errors errors, Object object) {
        checkNotNull(validator);
        checkArgument(object instanceof BridgeEntity);
        checkArgument(validator.supports(object.getClass()), "Invalid validator");
        checkNotNull(errors);
        checkNotNull(object);
        
        validator.validate(object, errors);
    }
    static void throwException(Errors errors, BridgeEntity entity) {
        if (errors.hasErrors()) {
            String message = convertErrorToMessage(errors);
            Map<String,List<String>> map = convertErrorsToSimpleMap(errors);
            
            throw new InvalidEntityException(entity, message, map);
        }
    }
    static Map<String,List<String>> convertErrorsToSimpleMap(Errors errors) {
        Map<String,List<String>> map = Maps.newHashMap();
        
        if (errors.hasGlobalErrors()) {
            List<String> list = Lists.newArrayList();
            for (ObjectError error : errors.getGlobalErrors()) {
                list.add(errorToString(error.getObjectName(), error));
            }
            map.put(errors.getObjectName(), list);
        }
        if (errors.hasFieldErrors()) {
            for (FieldError error : errors.getFieldErrors()) {
                String fieldName = error.getField();
                if (map.get(fieldName) == null) {
                    map.put(fieldName, Lists.<String>newArrayList());
                }
                map.get(fieldName).add(errorToString(error.getField(), error));
            }
        }
        return map;
    }
    private static String convertErrorToMessage(Errors errors) {
        List<String> messages = Lists.newArrayListWithCapacity(errors.getErrorCount());
        for (ObjectError error : errors.getGlobalErrors()) {
            messages.add(errorToString(error.getObjectName(), error));    
        }
        for (FieldError error : errors.getFieldErrors()) {
            messages.add(errorToString(error.getField(), error));
        }
        return String.format("%s is invalid: %s", errors.getObjectName(), Joiner.on("; ").join(messages));
    }
    private static String errorToString(String name, ObjectError error) {
        String errorCode = error.getCode();
        String defMessage = error.getDefaultMessage();
        if (error.getArguments() != null) {
            String base = (error.getCode() != null) ? error.getCode() : error.getDefaultMessage();
            String message = (base == null) ? "" : String.format(base, error.getArguments());
            return formatIfNecessary(name, message);
        } else if (errorCode != null){
            return formatIfNecessary(name, errorCode);
        } else if (defMessage != null) {
            return formatIfNecessary(name, defMessage);
        }
        return "<ERROR>";
    }
    private static String formatIfNecessary(String name, String errorMessage) {
        if (errorMessage.contains("%s")) {
            return String.format(errorMessage, name).trim();
        } else if (errorMessage.startsWith(" ")) {
            return (name + errorMessage).trim();    
        }
        return (name + " " + errorMessage).trim();
    }
}

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

    public static final String EVENT_TIMESTAMP_FIELD = "eventTimestamp";
    public static final String STARTED_ON_FIELD = "startedOn";
    public static final String INSTANCE_GUID_FIELD = "instanceGuid";
    public static final String STUDY_ID_FIELD = "studyId";
    public static final String USER_ID_FIELD = "userId";
    public static final String CLIENT_TIME_ZONE_FIELD = "clientTimeZone";
    
    public static final String CANNOT_BE_BLANK = "%s cannot be null or blank";
    public static final String CANNOT_BE_DUPLICATE = "%s cannot duplicate an earlier value";
    public static final String CANNOT_BE_EMPTY = "%s cannot be empty";
    public static final String CANNOT_BE_EMPTY_STRING = "%s cannot be an empty string";
    public static final String CANNOT_BE_NEGATIVE = "%s cannot be negative";
    public static final String CANNOT_BE_NULL = "%s cannot be null";
    public static final String CANNOT_BE_NULL_OR_EMPTY = "%s cannot be null or empty";
    public static final String CANNOT_BE_ZERO_OR_NEGATIVE = "%s cannot be zero or negative";
    public static final String WRONG_TYPE = "%s is the wrong type";
    public static final String TIME_ZONE_ERROR = "is not a recognized IANA time zone name (eg. “America/Los_Angeles”)";
    public static final String INVALID_EVENT_ID = "is not a valid custom event ID";
    public static final String INVALID_EMAIL_ERROR = "does not appear to be an email address";
    public static final String INVALID_PHONE_ERROR = "does not appear to be a phone number";
    public static final String EXCEEDS_MAXIMUM_SIZE = "%s exceeds the maximum allowed size";
    
    public static Errors getErrorsFor(Object object) {
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
    
    public static void entity(Validator validator, Errors errors, Object object) {
        checkNotNull(validator);
        checkArgument(object instanceof BridgeEntity);
        checkArgument(validator.supports(object.getClass()), "Invalid validator");
        checkNotNull(errors);
        checkNotNull(object);
        
        validator.validate(object, errors);
    }
    public static void throwException(Errors errors, BridgeEntity entity) {
        if (errors.hasErrors()) {
            String message = convertErrorToMessage(errors);
            Map<String,List<String>> map = convertErrorsToSimpleMap(errors);
            
            throw new InvalidEntityException(entity, message, map);
        }
    }
    public static Map<String,List<String>> convertErrorsToSimpleMap(Errors errors) {
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

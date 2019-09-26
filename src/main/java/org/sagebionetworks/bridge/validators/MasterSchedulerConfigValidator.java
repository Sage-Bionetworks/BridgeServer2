package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.fasterxml.jackson.databind.JsonNode;

/** Validator for MasterSchedulerConfig. */
public class MasterSchedulerConfigValidator implements Validator {
    /** Singleton instance of this validator. */
    public static final MasterSchedulerConfigValidator INSTANCE = new MasterSchedulerConfigValidator();
    
    /** {@inheritDoc} */
    @Override
    public boolean supports(Class<?> clazz) {
        return MasterSchedulerConfig.class.isAssignableFrom(clazz);
    }
    
    @Override
    public void validate(Object object, Errors errors) {
        if (object == null) {
            errors.rejectValue("masterSchedulerConfig", "cannot be null");
        } else if (!(object instanceof MasterSchedulerConfig)) {
            errors.rejectValue("masterSchedulerConfig", "is the wrong type");
        } else {
            MasterSchedulerConfig schedulerConfig = (MasterSchedulerConfig) object;
            
            // scheduleId
            if (schedulerConfig.getScheduleId() == null) {
                errors.rejectValue("scheduleId", "is required");
            }
            // cronSchedule
            if (schedulerConfig.getCronSchedule() == null) {
                errors.rejectValue("cronSchedule", "is required");
            }
            
            // requestTemplate - Must be non-null and an ObjectNode.
            JsonNode requestTemplate = schedulerConfig.getRequestTemplate();
            if (requestTemplate == null || requestTemplate.isNull()) {
                errors.rejectValue("requestTemplate", "is required");
            } else if (!requestTemplate.isObject()) {
                errors.rejectValue("requestTemplate", "must be an object node");
            }
            
            // sqsQueueUrl
            if (schedulerConfig.getSqsQueueUrl() == null) {
                errors.rejectValue("sqsQueueUrl", "is required");
            }
        }
    }
}

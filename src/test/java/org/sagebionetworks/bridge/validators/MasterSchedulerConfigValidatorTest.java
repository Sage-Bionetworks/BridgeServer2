package org.sagebionetworks.bridge.validators;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.schedules.MasterSchedulerConfig;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.node.IntNode;

public class MasterSchedulerConfigValidatorTest {
    
    private MasterSchedulerConfigValidator validator = MasterSchedulerConfigValidator.INSTANCE;
    
    private MasterSchedulerConfig config;
    
    @BeforeMethod
    public void before() {
        config = TestUtils.getMasterSchedulerConfig();
    }
    
    @Test
    public void validWorks() {
        MasterSchedulerConfig config = TestUtils.getMasterSchedulerConfig();
        
        Validate.entityThrowingException(validator, config);
    }
    
    @Test
    public void scheduleIdisRequired() {
        config.setScheduleId(null);
        
        TestUtils.assertValidatorMessage(validator, config, "scheduleId", "is required");
    }
    
    @Test
    public void cronScheduleisRequired() {
        config.setCronSchedule(null);
        
        TestUtils.assertValidatorMessage(validator, config, "cronSchedule", "is required");
    }
    
    @Test
    public void requestTemplateisRequired() {
        config.setRequestTemplate(null);
        
        TestUtils.assertValidatorMessage(validator, config, "requestTemplate", "is required");
    }
    
    @Test
    public void requestTemplateMustBeObject() {
        config.setRequestTemplate(IntNode.valueOf(42));
        
        TestUtils.assertValidatorMessage(validator, config, "requestTemplate", "must be an object node");
    }
    
    @Test
    public void sQsQueueUrlisRequired() {
        config.setSqsQueueUrl(null);
        
        TestUtils.assertValidatorMessage(validator, config, "sqsQueueUrl", "is required");
    }
}

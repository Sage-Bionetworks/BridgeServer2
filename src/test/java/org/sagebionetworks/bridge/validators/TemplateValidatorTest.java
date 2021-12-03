package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.TestUtils.generateStringOfLength;
import static org.sagebionetworks.bridge.TestUtils.getInvalidStringLengthMessage;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_VERIFY_EMAIL;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.templates.Template;

public class TemplateValidatorTest extends Mockito {

    TemplateValidator validator;
    
    @BeforeMethod
    public void beforeMethod() {
        validator = new TemplateValidator(USER_DATA_GROUPS, USER_STUDY_IDS);
    }

    @Test
    public void valid() {
        Criteria criteria = Criteria.create();
        
        Template template = Template.create();
        template.setName("Test template");
        template.setTemplateType(EMAIL_VERIFY_EMAIL);
        template.setCriteria(criteria);
        Validate.entityThrowingException(validator, template);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp=".*name is required.*")
    public void noName() {
        Template template = Template.create();
        template.setTemplateType(EMAIL_VERIFY_EMAIL);
        Validate.entityThrowingException(validator, template);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp=".*templateType is required.*")
    public void noTemplateType() {
        Template template = Template.create();
        template.setName("Test template");
        Validate.entityThrowingException(validator, template);
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp=".*allOfGroups includes these excluded data groups.*")
    public void invalidCriteria() {
        Criteria criteria = Criteria.create();
        criteria.setAllOfGroups(USER_DATA_GROUPS);
        criteria.setNoneOfGroups(USER_DATA_GROUPS);
        
        Template template = Template.create();
        template.setName("Test template");
        template.setTemplateType(EMAIL_VERIFY_EMAIL);
        template.setCriteria(criteria);
        Validate.entityThrowingException(validator, template);        
    }
    
    @Test
    public void stringLengthValidation_name() {
        Criteria criteria = Criteria.create();
    
        Template template = Template.create();
        template.setName(generateStringOfLength(256));
        template.setTemplateType(EMAIL_VERIFY_EMAIL);
        template.setCriteria(criteria);
        assertValidatorMessage(validator, template, "name", getInvalidStringLengthMessage(255));
    }
    
    @Test
    public void stringLengthValidation_description() {
        Criteria criteria = Criteria.create();
        
        Template template = Template.create();
        template.setName("Test template");
        template.setTemplateType(EMAIL_VERIFY_EMAIL);
        template.setCriteria(criteria);
        template.setDescription(generateStringOfLength(TEXT_SIZE + 1));
        assertValidatorMessage(validator, template, "description", getInvalidStringLengthMessage(TEXT_SIZE));
    }
}

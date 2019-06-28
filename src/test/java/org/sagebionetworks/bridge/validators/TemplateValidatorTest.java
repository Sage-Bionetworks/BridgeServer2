package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_SUBSTUDY_IDS;
import static org.sagebionetworks.bridge.models.TemplateType.EMAIL_VERIFY_EMAIL;

import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.Template;

public class TemplateValidatorTest extends Mockito {

    TemplateValidator validator;
    
    @BeforeMethod
    public void beforeMethod() {
        validator = new TemplateValidator(USER_DATA_GROUPS, USER_SUBSTUDY_IDS);
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
}

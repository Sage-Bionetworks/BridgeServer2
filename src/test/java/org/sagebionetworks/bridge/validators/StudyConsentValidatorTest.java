package org.sagebionetworks.bridge.validators;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class StudyConsentValidatorTest {

    private Resource resource;
    
    @BeforeMethod
    public void before() {
        resource = new AbstractResource() {
            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(new ClassPathResource("conf/study-defaults/consent-page.xhtml").getFile());
            }
            @Override
            public String getDescription() {
                return null;
            }
        };
    }
    
    @AfterMethod
    public void after() throws IOException {
        resource.getInputStream().close();
    }
    
    @Test(expectedExceptions = InvalidEntityException.class)
    public void detectsInvalidXML() throws Exception {
        StudyConsentValidator validator = new StudyConsentValidator();
        validator.setConsentBodyTemplate(resource);
        
        StudyConsentForm form = new StudyConsentForm("<p>Definitely broken markup </xml>");
        
        Validate.entityThrowingException(validator, form);
    }

}

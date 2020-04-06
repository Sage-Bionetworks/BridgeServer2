package org.sagebionetworks.bridge.models.assessments.config;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.validators.AbstractValidator;

public class ConfigVisitorTest extends Mockito {

    @Mock
    Errors mockErrors;
    
    Map<String, Validator> validators;
    
    ConfigVisitor visitor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        validators = new HashMap<>();
        visitor = new ConfigVisitor(validators, mockErrors);
    }
    
    @Test
    public void testRoot() throws Exception {
        visitor.accept("", toNode("{}"));
            
        // this is allowable and the results are correct, so no need to special case it.
        verify(mockErrors).pushNestedPath("");
        verify(mockErrors).rejectValue("identifier", "is missing");
        verify(mockErrors).rejectValue("type", "is missing");
        verify(mockErrors).popNestedPath();
    }

    @Test
    public void testPath() throws Exception {
        visitor.accept("foo.bar[1]", toNode("{}"));
            
        verify(mockErrors).pushNestedPath("foo.bar[1]");
        verify(mockErrors).rejectValue("identifier", "is missing");
        verify(mockErrors).rejectValue("type", "is missing");
        verify(mockErrors).popNestedPath();
    }
    
    @Test
    public void testWithSubTypeValidator() throws Exception {
        validators.put("SubType", new AbstractValidator() {
            public void validate(Object target, Errors errors) {
                errors.rejectValue("bar", "is missing");
            }
        });
        
        visitor.accept("foo", toNode("{'type':'SubType'}"));
        
        verify(mockErrors).pushNestedPath("foo");
        verify(mockErrors).rejectValue("identifier", "is missing");
        verify(mockErrors).rejectValue("bar", "is missing");
        verify(mockErrors).popNestedPath();
    }
    
    @Test
    public void noValidationErrors() throws Exception { 
        validators.put("Bar", new AbstractValidator() {
            public void validate(Object target, Errors errors) {
                // no validation error
            }
        });
        visitor.accept("", toNode("{'identifier':'foo','type':'Bar'}"));
        verify(mockErrors, never()).rejectValue(any(), any());
    }
    
    @Test
    public void noValidatorForType() throws Exception {
        // Bar has no validator, this doesn't fail
        visitor.accept("", toNode("{'identifier':'foo','type':'Bar'}"));
        verify(mockErrors, never()).rejectValue(any(), any());
    }
    
    @Test
    public void nullValidatorsWorks() throws Exception {
        ConfigVisitor visitor = new ConfigVisitor(null, mockErrors);
        visitor.accept("", toNode("{'identifier':'foo','type':'Bar'}"));
        verify(mockErrors, never()).rejectValue(any(), any());
    }
    
    private JsonNode toNode(String string) throws Exception {
        return new ObjectMapper().readTree(TestUtils.createJson(string));
    }
}

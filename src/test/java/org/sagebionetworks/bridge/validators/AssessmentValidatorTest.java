package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dao.AssessmentDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.SubstudyService;

public class AssessmentValidatorTest extends Mockito {

    @Mock
    AssessmentDao mockAssessmentDao;
    
    @Mock
    SubstudyService mockSubstudyService;
    
    AssessmentValidator validator;
    
    Assessment assessment;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        assessment = AssessmentTest.createAssessment();
        
        when(mockAssessmentDao.getAssessmentRevisions(TEST_APP_ID, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<Assessment>(ImmutableList.of(), 0));
        
        validator = new AssessmentValidator(mockSubstudyService, TEST_APP_ID);
    }
    
    @Test
    public void validAssessment() {
        when(mockSubstudyService.getSubstudy(TEST_APP_ID, assessment.getOwnerId(), false))
            .thenReturn(Substudy.create());
        
        Validate.entityThrowingException(validator, assessment);
    }
    @Test
    public void validSharedAssessment() {
        validator = new AssessmentValidator(mockSubstudyService, SHARED_APP_ID);
        assessment.setOwnerId(TEST_APP_ID + ":" + OWNER_ID);
        
        when(mockSubstudyService.getSubstudy(TEST_APP_ID, OWNER_ID, false)).thenReturn(Substudy.create());
    
        Validate.entityThrowingException(validator, assessment);
    }
    @Test
    public void ownerIdInvalid() {
        assertValidatorMessage(validator, assessment, "ownerId", "is not a valid organization ID");
    }
    @Test
    public void guidNull() {
        assessment.setGuid(null);
        assertValidatorMessage(validator, assessment, "guid", CANNOT_BE_BLANK);
    }
    @Test
    public void guidEmpty() {
        assessment.setGuid("  ");
        assertValidatorMessage(validator, assessment, "guid", CANNOT_BE_BLANK);
    }
    @Test
    public void titleNull() {
        assessment.setTitle(null);
        assertValidatorMessage(validator, assessment, "title", CANNOT_BE_BLANK);
    }
    @Test
    public void titleEmpty() {
        assessment.setTitle("");
        assertValidatorMessage(validator, assessment, "title", CANNOT_BE_BLANK);
    }
    @Test
    public void osNameNull() {
        assessment.setOsName(null);
        assertValidatorMessage(validator, assessment, "osName", CANNOT_BE_BLANK);
    }
    @Test
    public void osNameEmpty() {
        assessment.setOsName("\n");
        assertValidatorMessage(validator, assessment, "osName", CANNOT_BE_BLANK);
    }
    @Test
    public void osNameInvalid() {
        assessment.setOsName("webOS");
        assertValidatorMessage(validator, assessment, "osName", "is not a supported platform");
    }
    @Test
    public void osNameUniversalIsValid() {
        when(mockSubstudyService.getSubstudy(TEST_APP_ID, assessment.getOwnerId(), false))
            .thenReturn(Substudy.create());
        
        assessment.setOsName("Universal");
        Validate.entityThrowingException(validator, assessment);
    }
    @Test
    public void identifierNull() {
        assessment.setIdentifier(null);
        assertValidatorMessage(validator, assessment, "identifier", CANNOT_BE_BLANK);
    }
    @Test
    public void identifierEmpty() {
        assessment.setIdentifier("   ");
        assertValidatorMessage(validator, assessment, "identifier", CANNOT_BE_BLANK);
    }
    @Test
    public void identifierInvalid() {
        assessment.setIdentifier("spaces are not allowed");
        assertValidatorMessage(validator, assessment, "identifier", BRIDGE_EVENT_ID_ERROR);
    }
    @Test
    public void revisionNegative() {
        assessment.setRevision(-3);
        assertValidatorMessage(validator, assessment, "revision", "cannot be negative");
    }
    @Test
    public void ownerIdNull() {
        assessment.setOwnerId(null);
        assertValidatorMessage(validator, assessment, "ownerId", CANNOT_BE_BLANK);
    }
    @Test
    public void ownerIdEmpty() {
        assessment.setOwnerId("\t");
        assertValidatorMessage(validator, assessment, "ownerId", CANNOT_BE_BLANK);
    }
    @Test
    public void propertyInfoInvalid() {
        PropertyInfo info = new PropertyInfo.Builder().build();
        Map<String, Set<PropertyInfo>> customizationFields = new HashMap<>();
        customizationFields.put("oneIdentifier", ImmutableSet.of(info));
        
        assessment.setCustomizationFields(customizationFields);
        
        assertValidatorMessage(validator, assessment, "customizationFields[oneIdentifier][0].propName",
                CANNOT_BE_BLANK);
        assertValidatorMessage(validator, assessment, "customizationFields[oneIdentifier][0].label", 
                CANNOT_BE_BLANK);
    }
}

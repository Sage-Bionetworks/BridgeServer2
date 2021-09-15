package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.BRIDGE_EVENT_ID_ERROR;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.DUPLICATE_LANG;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.INVALID_HEX_TRIPLET;

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
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.AssessmentTest;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;
import org.sagebionetworks.bridge.models.organizations.Organization;
import org.sagebionetworks.bridge.services.OrganizationService;

public class AssessmentValidatorTest extends Mockito {

    @Mock
    AssessmentDao mockAssessmentDao;
    
    @Mock
    OrganizationService mockOrganizationService;
    
    AssessmentValidator validator;
    
    Assessment assessment;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        assessment = AssessmentTest.createAssessment();
        
        when(mockAssessmentDao.getAssessmentRevisions(TEST_APP_ID, null, IDENTIFIER, 0, 1, true))
            .thenReturn(new PagedResourceList<Assessment>(ImmutableList.of(), 0));
        
        validator = new AssessmentValidator(TEST_APP_ID, mockOrganizationService);
    }
    
    @Test
    public void validAssessment() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, assessment.getOwnerId()))
            .thenReturn(Organization.create());
        
        Validate.entityThrowingException(validator, assessment);
    }
    @Test
    public void validAssessmentWithNoOptionalFields() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, assessment.getOwnerId()))
            .thenReturn(Organization.create());
        assessment.setColorScheme(null);
        assessment.setLabels(null);
        assessment.setMinutesToComplete(null);
        assessment.setCustomizationFields(null);
    
        Validate.entityThrowingException(validator, assessment);
    }
    @Test
    public void validAssessmentWithEmptyColorScheme() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, assessment.getOwnerId()))
            .thenReturn(Organization.create());
        assessment.setColorScheme(new ColorScheme(null, null, null, null));
        
        Validate.entityThrowingException(validator, assessment);
    }
    @Test
    public void validSharedAssessment() {
        validator = new AssessmentValidator(SHARED_APP_ID, mockOrganizationService);
        assessment.setOwnerId(TEST_APP_ID + ":" + TEST_OWNER_ID);
        
        when(mockOrganizationService.getOrganization(TEST_APP_ID, TEST_OWNER_ID)).thenReturn(Organization.create());
    
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
    public void minutesToCompleteNegative() {
        assessment.setMinutesToComplete(-1);
        assertValidatorMessage(validator, assessment, "minutesToComplete", CANNOT_BE_NEGATIVE);
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
        when(mockOrganizationService.getOrganization(TEST_APP_ID, assessment.getOwnerId()))
            .thenReturn(Organization.create());
        
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
    @Test
    public void colorSchemeInvalid() {
        // one test is enough to confirm this is being validated, full tests are in 
        // ValidatorUtilsTest
        ColorScheme scheme = new ColorScheme("#FFFF1G", null, null, null);
        assessment.setColorScheme(scheme);
        assertValidatorMessage(validator, assessment, "colorScheme.background", INVALID_HEX_TRIPLET);
    }
    
    @Test
    public void labelsEmptyIsValid() {
        when(mockOrganizationService.getOrganization(TEST_APP_ID, assessment.getOwnerId()))
            .thenReturn(Organization.create());
        
        assessment.setLabels(ImmutableList.of());
        Validate.entityThrowingException(validator, assessment);
    }

    @Test
    public void labelsInvalid() {
        assessment.setLabels(ImmutableList.of(new Label("en", "foo"), new Label("en", "bar")));
        assertValidatorMessage(validator, assessment, "labels[1].lang", DUPLICATE_LANG);
    }

    @Test
    public void labelsValueBlank() {
        assessment.setLabels(ImmutableList.of(new Label("en", "")));
        assertValidatorMessage(validator, assessment, "labels[0].value", CANNOT_BE_BLANK);
    }

    @Test
    public void labelsValueNull() {
        assessment.setLabels(ImmutableList.of(new Label("en", null)));
        assertValidatorMessage(validator, assessment, "labels[0].value", CANNOT_BE_BLANK);
    }
}

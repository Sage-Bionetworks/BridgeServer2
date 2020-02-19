package org.sagebionetworks.bridge.models.assessments;

import static com.google.common.collect.Sets.symmetricDifference;
import static org.sagebionetworks.bridge.TestConstants.CATEGORIES;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.CUSTOMIZATION_FIELDS;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.OWNER_ID;
import static org.sagebionetworks.bridge.TestConstants.TAGS;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

public class AssessmentTest {

    @Test
    public void revisionDefaultsToOne() {
        Assessment assessment = new Assessment();
        assertEquals(assessment.getRevision(), 1);
    }
    
    @Test
    public void testFields() {
        Assessment assessment = createAssessment();
        assertAssessment(assessment);
    }
   
    @Test
    public void createFactoryMethod() {
        AssessmentDto dto = AssessmentDtoTest.createAssessmentDto();
        Assessment assessment = Assessment.create(dto, "appId");
        assertAssessment(assessment);
    }
    
    public static Assessment createAssessment() {
        Assessment assessment = new Assessment();
        assessment.setGuid(GUID);
        assessment.setAppId("appId");
        assessment.setIdentifier(IDENTIFIER);
        assessment.setTitle("title");
        assessment.setCategories(CATEGORIES);
        assessment.setSummary("summary");
        assessment.setValidationStatus("validationStatus");
        assessment.setNormingStatus("normingStatus");
        assessment.setOsName(ANDROID);
        assessment.setOriginGuid("originGuid");
        assessment.setOwnerId(OWNER_ID);
        assessment.setTags(TAGS);
        assessment.setCustomizationFields(CUSTOMIZATION_FIELDS);
        assessment.setCreatedOn(CREATED_ON);
        assessment.setModifiedOn(MODIFIED_ON);
        assessment.setDeleted(true);
        assessment.setRevision(5);
        assessment.setVersion(8L);
        return assessment;
    }
    
    private void assertAssessment(Assessment assessment) {
        assertEquals(assessment.getGuid(), GUID);
        assertEquals(assessment.getAppId(), "appId");
        assertEquals(assessment.getIdentifier(), IDENTIFIER);
        assertEquals(assessment.getTitle(), "title");
        assertTrue(symmetricDifference(assessment.getCategories(), CATEGORIES).isEmpty());
        assertEquals(assessment.getSummary(), "summary");
        assertEquals(assessment.getValidationStatus(), "validationStatus");
        assertEquals(assessment.getNormingStatus(), "normingStatus");
        assertEquals(assessment.getOsName(), ANDROID);
        assertEquals(assessment.getOriginGuid(), "originGuid");
        assertEquals(assessment.getOwnerId(), OWNER_ID);
        assertTrue(symmetricDifference(assessment.getTags(), TAGS).isEmpty());
        assertEquals(assessment.getCustomizationFields(), CUSTOMIZATION_FIELDS);
        assertEquals(assessment.getCreatedOn(), CREATED_ON);
        assertEquals(assessment.getModifiedOn(), MODIFIED_ON);
        assertTrue(assessment.isDeleted());
        assertEquals(assessment.getRevision(), 5);
        assertEquals(assessment.getVersion(), 8);
    }
}

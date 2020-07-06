package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

import com.google.common.collect.ImmutableSet;

public class SubpopulationValidatorTest {

    SubpopulationValidator validator;
    
    @BeforeMethod
    public void before() {
        validator = new SubpopulationValidator(TestConstants.USER_DATA_GROUPS, TestConstants.USER_STUDY_IDS);
    }
    
    @Test
    public void testEntirelyValid() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setDefaultGroup(true);
        subpop.setRequired(true);
        subpop.setAppId(TEST_APP_ID);
        subpop.setVersion(3L);
        subpop.setGuidString("AAA");
        subpop.setDataGroupsAssignedWhileConsented(TestConstants.USER_DATA_GROUPS);
        subpop.setStudyIdsAssignedOnConsent(TestConstants.USER_STUDY_IDS);
        
        Criteria criteria = TestUtils.createCriteria(2, 4, ImmutableSet.of("group1"), ImmutableSet.of("group2"));
        criteria.setAllOfStudyIds(ImmutableSet.of("studyA"));
        criteria.setNoneOfStudyIds(ImmutableSet.of("studyB"));
        subpop.setCriteria(criteria);
        
        Validate.entityThrowingException(validator, subpop);
    }
    
    @Test
    public void testValidation() {
        Subpopulation subpop = Subpopulation.create();
        
        Criteria criteria = TestUtils.createCriteria(-10, -2, null, ImmutableSet.of("wrongGroup"));
        criteria.setAllOfStudyIds(ImmutableSet.of("studyC"));
        criteria.setNoneOfStudyIds(ImmutableSet.of("studyD"));
        subpop.setCriteria(criteria);
        
        subpop.setDataGroupsAssignedWhileConsented(ImmutableSet.of("group1", "dataGroup3"));
        subpop.setStudyIdsAssignedOnConsent(ImmutableSet.of("studyA", "studyC"));
        try {
            Validate.entityThrowingException(validator, subpop);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "minAppVersions.iphone_os", " cannot be negative");
            assertMessage(e, "maxAppVersions.iphone_os", " cannot be negative");
            assertMessage(e, "appId", " is required");
            assertMessage(e, "name", " is required");
            assertMessage(e, "guid", " is required");
            assertMessage(e, "noneOfGroups", " 'wrongGroup' is not in enumeration");
            assertMessage(e, "allOfStudyIds", " 'studyC' is not in enumeration");
            assertMessage(e, "noneOfStudyIds", " 'studyD' is not in enumeration");
            assertMessage(e, "dataGroupsAssignedWhileConsented", " 'dataGroup3' is not in enumeration: group1, group2");
            assertMessage(e, "studyIdsAssignedOnConsent", " 'studyC' is not in enumeration: studyA, studyB");
        }
    }
    
    @Test
    public void emptyListsOK() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuidString("AAA");
        subpop.setName("Name");
        subpop.setDataGroupsAssignedWhileConsented(ImmutableSet.of());
        subpop.setStudyIdsAssignedOnConsent(ImmutableSet.of());
        
        Validate.entityThrowingException(validator, subpop);
        assertTrue(subpop.getDataGroupsAssignedWhileConsented().isEmpty());
        assertTrue(subpop.getStudyIdsAssignedOnConsent().isEmpty());
    }
    
    @Test
    public void nullListsOK() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuidString("AAA");
        subpop.setName("Name");
        subpop.setDataGroupsAssignedWhileConsented(null);
        subpop.setStudyIdsAssignedOnConsent(null);
        
        Validate.entityThrowingException(validator, subpop);
        assertTrue(subpop.getDataGroupsAssignedWhileConsented().isEmpty());
        assertTrue(subpop.getStudyIdsAssignedOnConsent().isEmpty());
    }
    
    private void assertMessage(InvalidEntityException e, String propName, String error) {
        Map<String,List<String>> errors = e.getErrors();
        List<String> messages = errors.get(propName);
        assertTrue(messages.get(0).contains(propName + error));
    }
}

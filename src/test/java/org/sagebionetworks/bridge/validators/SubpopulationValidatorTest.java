package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

import com.google.common.collect.ImmutableSet;

public class SubpopulationValidatorTest {

    SubpopulationValidator validator;
    
    @BeforeMethod
    public void before() {
        validator = new SubpopulationValidator(USER_DATA_GROUPS, USER_STUDY_IDS);
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
        subpop.setDataGroupsAssignedWhileConsented(USER_DATA_GROUPS);
        subpop.setStudyIdsAssignedOnConsent(USER_STUDY_IDS);
        
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
        subpop.setStudyIdsAssignedOnConsent(ImmutableSet.of("studyC"));
        try {
            Validate.entityThrowingException(validator, subpop);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertValidatorMessage(validator, subpop, "criteria.minAppVersions.iphone_os", " cannot be negative");
            assertValidatorMessage(validator, subpop, "criteria.maxAppVersions.iphone_os", " cannot be negative");
            assertValidatorMessage(validator, subpop, "appId", CANNOT_BE_NULL);
            assertValidatorMessage(validator, subpop, "name", CANNOT_BE_NULL);
            assertValidatorMessage(validator, subpop, "guid", CANNOT_BE_NULL);
            assertValidatorMessage(validator, subpop, "criteria.noneOfGroups", " 'wrongGroup' is not in enumeration: group1, group2");
            assertValidatorMessage(validator, subpop, "criteria.allOfStudyIds", " 'studyC' is not in enumeration: studyA, studyB");
            assertValidatorMessage(validator, subpop, "criteria.noneOfStudyIds", " 'studyD' is not in enumeration: studyA, studyB");
            assertValidatorMessage(validator, subpop, "dataGroupsAssignedWhileConsented", " 'dataGroup3' is not in enumeration: group1, group2");
            assertValidatorMessage(validator, subpop, "studyIdsAssignedOnConsent", " 'studyC' is not in enumeration: studyA, studyB");
        }
    }
    
    @Test
    public void testStudyIdsAssignedOnConsentEmpty() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuidString("AAA");
        subpop.setStudyIdsAssignedOnConsent(ImmutableSet.of());
        
        assertValidatorMessage(validator, subpop, "studyIdsAssignedOnConsent", "cannot be empty");
    }
    
    @Test
    public void emptyListsOK() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuidString("AAA");
        subpop.setName("Name");
        subpop.setDataGroupsAssignedWhileConsented(ImmutableSet.of());
        subpop.setStudyIdsAssignedOnConsent(USER_STUDY_IDS);
        
        Validate.entityThrowingException(validator, subpop);
        assertTrue(subpop.getDataGroupsAssignedWhileConsented().isEmpty());
    }
    
    @Test
    public void nullListsOK() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuidString("AAA");
        subpop.setName("Name");
        subpop.setDataGroupsAssignedWhileConsented(null);
        subpop.setStudyIdsAssignedOnConsent(USER_STUDY_IDS);
        
        Validate.entityThrowingException(validator, subpop);
        assertTrue(subpop.getDataGroupsAssignedWhileConsented().isEmpty());
    }
    
    @Test
    public void test() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setAppId(TEST_APP_ID);
        subpop.setGuidString("AAA");
        subpop.setName("Name");
    }
}

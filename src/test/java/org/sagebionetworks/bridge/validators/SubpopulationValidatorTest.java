package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

import com.google.common.collect.ImmutableSet;

public class SubpopulationValidatorTest {

    SubpopulationValidator validator;
    
    @BeforeMethod
    public void before() {
        validator = new SubpopulationValidator(TestConstants.USER_DATA_GROUPS, TestConstants.USER_STUDY_IDS);
    }
    
    Subpopulation getSubpopulation() { 
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
        return subpop;
    }
    
    @Test
    public void testValid() {
        Validate.entityThrowingException(validator, getSubpopulation());
    }
    
    @Test
    public void appIdNull() {
        Subpopulation subpop = getSubpopulation();
        subpop.setAppId(null);
        assertValidatorMessage(validator, subpop, "appId", Validate.CANNOT_BE_NULL);
    }
    
    @Test
    public void nameNull() {
        Subpopulation subpop = getSubpopulation();
        subpop.setName(null);
        assertValidatorMessage(validator, subpop, "name", Validate.CANNOT_BE_BLANK);
    }
    
    @Test
    public void nameBlank() {
        Subpopulation subpop = getSubpopulation();
        subpop.setName(" ");
        assertValidatorMessage(validator, subpop, "name", Validate.CANNOT_BE_BLANK);
    }
    
    @Test
    public void guidNull() {
        Subpopulation subpop = getSubpopulation();
        subpop.setGuid(null);
        assertValidatorMessage(validator, subpop, "guid", Validate.CANNOT_BE_BLANK);
        
    }

    @Test
    public void guidBlank() {
        Subpopulation subpop = getSubpopulation();
        subpop.setGuidString("");
        assertValidatorMessage(validator, subpop, "guid", Validate.CANNOT_BE_BLANK);
    }
    
    @Test
    public void invalidDataGroup() {
        Subpopulation subpop = getSubpopulation();
        subpop.setDataGroupsAssignedWhileConsented(ImmutableSet.of("junk"));
        assertValidatorMessage(validator, subpop, "dataGroupsAssignedWhileConsented", "'junk' is not in enumeration: group1, group2");
    }
    
    @Test
    public void emptyDataGroups() {
        validator = new SubpopulationValidator(ImmutableSet.of(), ImmutableSet.of());
        
        Subpopulation subpop = getSubpopulation();
        subpop.setDataGroupsAssignedWhileConsented(ImmutableSet.of("junk"));
        assertValidatorMessage(validator, subpop, "dataGroupsAssignedWhileConsented", "'junk' is not in enumeration: <empty>");
    }

    @Test
    public void emptyStudyIds() {
        Subpopulation subpop = getSubpopulation();
        subpop.setStudyIdsAssignedOnConsent(ImmutableSet.of());
        assertValidatorMessage(validator, subpop, "studyIdsAssignedOnConsent", "cannot be empty");
    }
    
    @Test
    public void invalidStudyIds() { 
        Subpopulation subpop = getSubpopulation();
        subpop.setStudyIdsAssignedOnConsent(ImmutableSet.of("junk"));
        assertValidatorMessage(validator, subpop, "studyIdsAssignedOnConsent", "'junk' is not in enumeration: studyA, studyB");
    }
    
    @Test
    public void invalidCriteria() { 
        Subpopulation subpop = getSubpopulation();
        subpop.getCriteria().setAllOfGroups(ImmutableSet.of("junk"));
        assertValidatorMessage(validator, subpop, "criteria.allOfGroups", "'junk' is not in enumeration: group1, group2");
    }
}

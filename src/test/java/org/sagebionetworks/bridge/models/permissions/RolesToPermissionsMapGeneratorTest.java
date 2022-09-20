package org.sagebionetworks.bridge.models.permissions;

import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.models.permissions.AccessLevel.ADMIN;
import static org.sagebionetworks.bridge.models.permissions.AccessLevel.DELETE;
import static org.sagebionetworks.bridge.models.permissions.AccessLevel.EDIT;
import static org.sagebionetworks.bridge.models.permissions.AccessLevel.LIST;
import static org.sagebionetworks.bridge.models.permissions.AccessLevel.READ;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ASSESSMENT_LIBRARY;
import static org.sagebionetworks.bridge.models.permissions.EntityType.MEMBERS;
import static org.sagebionetworks.bridge.models.permissions.EntityType.ORGANIZATION;
import static org.sagebionetworks.bridge.models.permissions.EntityType.PARTICIPANTS;
import static org.sagebionetworks.bridge.models.permissions.EntityType.SPONSORED_STUDIES;
import static org.testng.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.sagebionetworks.bridge.Roles;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RolesToPermissionsMapGeneratorTest {
    
    @Test
    public void generateDeveloper() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        Map<String, Set<AccessLevel>> assesLib = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE));
        Map<String, Set<AccessLevel>> members = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> organization = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> sponsoredStudies = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE));
        expectedPermissionsMap.put(ASSESSMENT_LIBRARY, assesLib);
        expectedPermissionsMap.put(MEMBERS, members);
        expectedPermissionsMap.put(ORGANIZATION, organization);
        expectedPermissionsMap.put(SPONSORED_STUDIES, sponsoredStudies);
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(Roles.DEVELOPER), TEST_ORG_ID, USER_STUDY_IDS);
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
    
    @Test
    public void generateStudyDesigner() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        Map<String, Set<AccessLevel>> assesLib = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE));
        Map<String, Set<AccessLevel>> members = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> organization = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> sponsoredStudies = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE));
        expectedPermissionsMap.put(ASSESSMENT_LIBRARY, assesLib);
        expectedPermissionsMap.put(MEMBERS, members);
        expectedPermissionsMap.put(ORGANIZATION, organization);
        expectedPermissionsMap.put(SPONSORED_STUDIES, sponsoredStudies);
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(Roles.STUDY_DESIGNER), TEST_ORG_ID, USER_STUDY_IDS);
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
    
    @Test
    public void generateResearcher() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        Map<String, Set<AccessLevel>> assesLib = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> members = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> organization = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> sponsoredStudies = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT));
        Map<String, Set<AccessLevel>> participants = ImmutableMap.of(
                "studyA", ImmutableSet.of(LIST, READ, EDIT, DELETE),
                "studyB", ImmutableSet.of(LIST, READ, EDIT, DELETE));
        expectedPermissionsMap.put(ASSESSMENT_LIBRARY, assesLib);
        expectedPermissionsMap.put(MEMBERS, members);
        expectedPermissionsMap.put(ORGANIZATION, organization);
        expectedPermissionsMap.put(SPONSORED_STUDIES, sponsoredStudies);
        expectedPermissionsMap.put(PARTICIPANTS, participants);
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(Roles.RESEARCHER), TEST_ORG_ID, USER_STUDY_IDS);
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
    
    @Test
    public void generateStudyCoordinator() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        Map<String, Set<AccessLevel>> assesLib = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> members = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> organization = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> sponsoredStudies = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT));
        Map<String, Set<AccessLevel>> participants = ImmutableMap.of(
                "studyA", ImmutableSet.of(LIST, READ, EDIT, DELETE),
                "studyB", ImmutableSet.of(LIST, READ, EDIT, DELETE));
        expectedPermissionsMap.put(ASSESSMENT_LIBRARY, assesLib);
        expectedPermissionsMap.put(MEMBERS, members);
        expectedPermissionsMap.put(ORGANIZATION, organization);
        expectedPermissionsMap.put(SPONSORED_STUDIES, sponsoredStudies);
        expectedPermissionsMap.put(PARTICIPANTS, participants);
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(Roles.STUDY_COORDINATOR), TEST_ORG_ID, USER_STUDY_IDS);
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
    
    @Test
    public void generateOrgAdmin() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        Map<String, Set<AccessLevel>> assesLib = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, ADMIN));
        Map<String, Set<AccessLevel>> members = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE, ADMIN));
        Map<String, Set<AccessLevel>> organization = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE, ADMIN));
        Map<String, Set<AccessLevel>> sponsoredStudies = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, ADMIN));
        expectedPermissionsMap.put(ASSESSMENT_LIBRARY, assesLib);
        expectedPermissionsMap.put(MEMBERS, members);
        expectedPermissionsMap.put(ORGANIZATION, organization);
        expectedPermissionsMap.put(SPONSORED_STUDIES, sponsoredStudies);
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(Roles.ORG_ADMIN), TEST_ORG_ID, USER_STUDY_IDS);
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
    
    @Test
    public void generateAdmin() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        Map<String, Set<AccessLevel>> assesLib = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE, ADMIN));
        Map<String, Set<AccessLevel>> members = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE, ADMIN));
        Map<String, Set<AccessLevel>> organization = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE, ADMIN));
        Map<String, Set<AccessLevel>> sponsoredStudies = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE, ADMIN));
        Map<String, Set<AccessLevel>> participants = ImmutableMap.of(
                "studyA", ImmutableSet.of(LIST, READ, EDIT, DELETE, ADMIN),
                "studyB", ImmutableSet.of(LIST, READ, EDIT, DELETE, ADMIN));
        expectedPermissionsMap.put(ASSESSMENT_LIBRARY, assesLib);
        expectedPermissionsMap.put(MEMBERS, members);
        expectedPermissionsMap.put(ORGANIZATION, organization);
        expectedPermissionsMap.put(SPONSORED_STUDIES, sponsoredStudies);
        expectedPermissionsMap.put(PARTICIPANTS, participants);
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(Roles.ADMIN), TEST_ORG_ID, USER_STUDY_IDS);
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
    
    @Test
    public void generateNoRoles() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(), TEST_ORG_ID, USER_STUDY_IDS);
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
    
    @Test
    public void generateWorker() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(Roles.WORKER), TEST_ORG_ID, USER_STUDY_IDS);
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
    
    @Test
    public void generateNoStudies() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        Map<String, Set<AccessLevel>> assesLib = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> members = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> organization = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> sponsoredStudies = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT));
        expectedPermissionsMap.put(ASSESSMENT_LIBRARY, assesLib);
        expectedPermissionsMap.put(MEMBERS, members);
        expectedPermissionsMap.put(ORGANIZATION, organization);
        expectedPermissionsMap.put(SPONSORED_STUDIES, sponsoredStudies);
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(Roles.RESEARCHER), TEST_ORG_ID, ImmutableSet.of());
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
    
    @Test
    public void generateNoOrg() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        Map<String, Set<AccessLevel>> participants = ImmutableMap.of(
                "studyA", ImmutableSet.of(LIST, READ, EDIT, DELETE),
                "studyB", ImmutableSet.of(LIST, READ, EDIT, DELETE));
        expectedPermissionsMap.put(PARTICIPANTS, participants);
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(Roles.RESEARCHER), null, USER_STUDY_IDS);
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
    
    @Test
    public void generateMultipleRoles() {
        Map<EntityType, Map<String, Set<AccessLevel>>> expectedPermissionsMap = new HashMap<>();
        Map<String, Set<AccessLevel>> assesLib = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE));
        Map<String, Set<AccessLevel>> members = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> organization = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ));
        Map<String, Set<AccessLevel>> sponsoredStudies = ImmutableMap.of(TEST_ORG_ID, ImmutableSet.of(LIST, READ, EDIT, DELETE));
        Map<String, Set<AccessLevel>> participants = ImmutableMap.of(
                "studyA", ImmutableSet.of(LIST, READ, EDIT, DELETE),
                "studyB", ImmutableSet.of(LIST, READ, EDIT, DELETE));
        expectedPermissionsMap.put(ASSESSMENT_LIBRARY, assesLib);
        expectedPermissionsMap.put(MEMBERS, members);
        expectedPermissionsMap.put(ORGANIZATION, organization);
        expectedPermissionsMap.put(SPONSORED_STUDIES, sponsoredStudies);
        expectedPermissionsMap.put(PARTICIPANTS, participants);
        
        Map<EntityType, Map<String, Set<AccessLevel>>> returnedMap = RolesToPermissionsMapGenerator.INSTANCE
                .generate(ImmutableSet.of(Roles.RESEARCHER, Roles.DEVELOPER), TEST_ORG_ID, USER_STUDY_IDS);
        
        assertEquals(returnedMap, expectedPermissionsMap);
    }
}
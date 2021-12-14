package org.sagebionetworks.bridge.models.accounts;

import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Iterator;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.studies.EnrollmentInfo;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class UserSessionInfoTest {

    @Test
    public void userSessionInfoSerializesCorrectly() throws Exception {
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", TEST_USER_ID);
        en1.setExternalId("externalIdA");
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", TEST_USER_ID);
        en2.setExternalId("externalIdB");
        
        DateTime timestamp = DateTime.now(DateTimeZone.UTC);
                
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail("test@test.com")
                .withFirstName("first name")
                .withLastName("last name")
                .withNotifyByEmail(false)
                .withEncryptedHealthCode(TestConstants.ENCRYPTED_HEALTH_CODE)
                .withId("user-identifier")
                .withRoles(ImmutableSet.of(RESEARCHER))
                .withStudyIds(ImmutableSet.of("studyA"))
                .withExternalIds(ImmutableMap.of("studyA", "externalIdA"))
                .withSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .withDataGroups(Sets.newHashSet("foo"))
                .withEnrollments(ImmutableMap.of("studyA", EnrollmentInfo.create(en1), "studyB", EnrollmentInfo.create(en2)))
                .withOrgMembership(TEST_ORG_ID).build();
        
        Map<SubpopulationGuid, ConsentStatus> map = TestUtils
                .toMap(new ConsentStatus("Consent", "AAA", true, true, false, timestamp.getMillis()));
        
        UserSession session = new UserSession(participant);
        session.setConsentStatuses(map);
        session.setAuthenticated(true);
        session.setEnvironment(Environment.UAT);
        session.setInternalSessionToken("internal");
        session.setSessionToken("external");
        session.setReauthToken("reauthToken");
        session.setAppId(TEST_APP_ID);
        
        JsonNode node = UserSessionInfo.toJSON(session);
        assertEquals(node.get("firstName").textValue(), "first name");
        assertEquals(node.get("lastName").textValue(), "last name");
        assertEquals(node.get("authenticated").booleanValue(), session.isAuthenticated());
        assertEquals(node.get("signedMostRecentConsent").booleanValue(), ConsentStatus.isConsentCurrent(map));
        assertEquals(node.get("consented").booleanValue(), ConsentStatus.isUserConsented(map));
        assertEquals(node.get("sharingScope").textValue().toUpperCase(), participant.getSharingScope().name());
        assertEquals(node.get("sessionToken").textValue(), session.getSessionToken());
        assertEquals(node.get("username").textValue(), participant.getEmail());
        assertEquals(participant.getEmail(), node.get("email").textValue());
        assertEquals(node.get("roles").get(0).textValue(), "researcher");
        assertEquals(node.get("dataGroups").get(0).textValue(), "foo");
        assertEquals(node.get("environment").textValue(), "staging");
        assertEquals(node.get("reauthToken").textValue(), "reauthToken");
        assertEquals(node.get("id").textValue(), participant.getId());
        assertEquals(node.get("studyIds").size(), 1);
        assertEquals(node.get("studyIds").get(0).textValue(), "studyA");
        assertEquals(node.get("externalId").textValue(), "externalIdA");
        assertFalse(node.get("notifyByEmail").booleanValue());
        assertNull(node.get("healthCode"));
        assertNull(node.get("encryptedHealthCode"));
        assertNull(node.get("synapseAuthenticated"));
        assertEquals(node.get("externalIds").get("studyA").textValue(), "externalIdA");
        assertEquals(node.get("orgMembership").textValue(), TEST_ORG_ID);
        assertEquals(node.get("type").asText(), "UserSessionInfo");
        assertEquals(node.get("enrollments").get("studyA").get("externalId").textValue(), "externalIdA");
        assertEquals(node.get("enrollments").get("studyB").get("externalId").textValue(), "externalIdB");
        
        JsonNode consentMap = node.get("consentStatuses");
        
        JsonNode consentStatus = consentMap.get("AAA");
        assertEquals(consentStatus.get("name").textValue(), "Consent");
        assertEquals(consentStatus.get("subpopulationGuid").textValue(), "AAA");
        assertTrue(consentStatus.get("required").booleanValue());
        assertTrue(consentStatus.get("consented").booleanValue());
        assertFalse(consentStatus.get("signedMostRecentConsent").booleanValue());
        assertEquals(consentStatus.get("signedOn").textValue(), timestamp.toString());
        assertEquals(consentStatus.get("type").textValue(), "ConsentStatus");
        assertEquals(consentStatus.size(), 7);
        
        // ... and no things that shouldn't be there
        assertEquals(node.size(), 25);
    }
    
    @Test
    public void assertNoNullFields() {
        JsonNode node = UserSessionInfo.toJSON(new UserSession());
        for (Iterator<String> i = node.fieldNames(); i.hasNext();) {
            String fieldName = i.next();
            assertFalse(node.get(fieldName).isNull(), fieldName + " should not be null");
        }
    }
}

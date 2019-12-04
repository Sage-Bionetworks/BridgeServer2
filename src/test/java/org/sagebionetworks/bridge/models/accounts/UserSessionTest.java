package org.sagebionetworks.bridge.models.accounts;

import static java.lang.Boolean.TRUE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.SUPERADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.config.Environment.PROD;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.ALL_QUALIFIED_RESEARCHERS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class UserSessionTest {
    
    private static final DateTime TIMESTAMP = DateTime.now(DateTimeZone.UTC);
    
    @Test
    public void canSerialize() throws Exception {
        SubpopulationGuid guid = SubpopulationGuid.create("subpop-guid");
        ConsentStatus status = new ConsentStatus.Builder().withName("Name").withGuid(guid).withRequired(true).build();
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
        statuses.put(guid, status);
        
        StudyParticipant participant = new StudyParticipant.Builder()
            .withId("id")
            .withFirstName("firstName")
            .withLastName("lastName")
            .withEmail("email")
            .withHealthCode("healthCode")
            .withSharingScope(ALL_QUALIFIED_RESEARCHERS)
            .withRoles(ImmutableSet.of(ADMIN))
            .withDataGroups(ImmutableSet.of("group1", "group2")).build();
        
        UserSession session = new UserSession(participant);
        session.setSessionToken("ABC");
        session.setInternalSessionToken("BBB");
        session.setAuthenticated(true);
        session.setEnvironment(PROD);
        session.setIpAddress("ip address");
        session.setStudyIdentifier(new StudyIdentifierImpl("study-key"));
        session.setReauthToken("reauthToken");
        session.setConsentStatuses(statuses);
        
        String json = StudyParticipant.CACHE_WRITER.writeValueAsString(session);
        UserSession newSession = BridgeObjectMapper.get().readValue(json, UserSession.class);

        assertTrue(newSession.isAuthenticated());
        assertNull(newSession.getReauthToken());
        assertEquals(newSession.getSessionToken(), session.getSessionToken());
        assertEquals(newSession.getInternalSessionToken(), session.getInternalSessionToken());
        assertEquals(newSession.getEnvironment(), session.getEnvironment());
        assertEquals(newSession.getIpAddress(), session.getIpAddress());
        assertEquals(newSession.getStudyIdentifier(), session.getStudyIdentifier());
        assertEquals(newSession.getParticipant(), session.getParticipant());
    }
    
    @Test
    public void doesNotExposeHealthCodeInRedisSerialization() throws Exception {
        UserSession session = new UserSession(new StudyParticipant.Builder().withHealthCode("123abc").build());
        
        String json = StudyParticipant.CACHE_WRITER.writeValueAsString(session);
        assertFalse(json.contains("123abc"));
    }
    
    @Test
    public void testHealthCodeEncryption() throws IOException {
        UserSession session = new UserSession(new StudyParticipant.Builder()
                .withEmail("userEmail")
                .withId("userId")
                .withHealthCode("123abc").build());
        String sessionSer = StudyParticipant.CACHE_WRITER.writeValueAsString(session);
        assertNotNull(sessionSer);
        assertFalse(sessionSer.toLowerCase().contains("123abc"),
                "Health code should have been encrypted in the serialized string.");
        
        UserSession sessionDe = BridgeObjectMapper.get().readValue(sessionSer, UserSession.class);
        assertNotNull(sessionDe);
        assertEquals(sessionDe.getHealthCode(), "123abc");
    }
    
    @Test
    public void userIsInRole() {
        UserSession session = new UserSession(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(ADMIN, DEVELOPER)).build());

        assertTrue(session.isInRole(DEVELOPER));
        assertFalse(session.isInRole(RESEARCHER));
        assertFalse(session.isInRole(WORKER));
        assertTrue(session.isInRole(ADMIN));
        assertFalse(session.isInRole((Roles)null));
    }
    
    @Test
    public void userIsInRoleSet() {
        UserSession session = new UserSession(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(ADMIN, DEVELOPER)).build());
                
        assertTrue(session.isInRole(ImmutableSet.of(DEVELOPER)));
        assertFalse(session.isInRole(ImmutableSet.of(RESEARCHER)));
        assertFalse(session.isInRole(ImmutableSet.of(WORKER)));
        assertTrue(session.isInRole(ImmutableSet.of(ADMIN)));
        assertFalse(session.isInRole((Set<Roles>)null));
    }
    
    @Test
    public void superadminIsInRole() {
        UserSession session = new UserSession(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        assertTrue(session.isInRole(DEVELOPER));
        assertTrue(session.isInRole(RESEARCHER));
        assertTrue(session.isInRole(WORKER));
        assertTrue(session.isInRole(ADMIN));
    }
    
    @Test
    public void superadminIsInRoleSet() {
        UserSession session = new UserSession(new StudyParticipant.Builder()
                .withRoles(ImmutableSet.of(SUPERADMIN)).build());
        
        assertTrue(session.isInRole(ImmutableSet.of(DEVELOPER)));
        assertTrue(session.isInRole(ImmutableSet.of(RESEARCHER)));
        assertTrue(session.isInRole(ImmutableSet.of(WORKER)));
        assertTrue(session.isInRole(ImmutableSet.of(ADMIN)));
        assertTrue(session.isInRole(ImmutableSet.of(RESEARCHER, ADMIN)));
    }
    
    @Test
    public void immutableConsentStatuses() {
        UserSession session = new UserSession();
        assertTrue(session.getConsentStatuses() instanceof ImmutableMap);
        
        session.setConsentStatuses(new HashMap<>());
        assertTrue(session.getConsentStatuses() instanceof ImmutableMap);
    }
    
    @Test
    public void noConsentsProperlySetsBooleans() {
        UserSession session = new UserSession();
        assertFalse(session.doesConsent());
        assertFalse(session.hasSignedMostRecentConsent());
    }
    
    @Test
    public void hasUserConsentedWorks() {
        // Empty consent list... you are not considered consented
        UserSession session = new UserSession();
        session.setConsentStatuses(new HashMap<>());
        assertFalse(session.doesConsent());
        
        // All required consents are consented, even one that's not up-to-date
        session = new UserSession();
        session.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false, TIMESTAMP.getMillis()),
            new ConsentStatus("Name", "guid2", true, true, true, TIMESTAMP.getMillis()),
            new ConsentStatus("Name", "guid3", false, false, false, TIMESTAMP.getMillis())
        ));
        assertTrue(session.doesConsent());
        
        // A required consent is not consented
        session = new UserSession();
        session.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false, TIMESTAMP.getMillis()),
            new ConsentStatus("Name", "guid2", true, false, false, TIMESTAMP.getMillis()),
            new ConsentStatus("Name", "guid3", false, false, false, TIMESTAMP.getMillis())
        ));
        assertFalse(session.doesConsent());
    }
    
    @Test
    public void areConsentsUpToDateWorks() {
        // Empty consent list... you are not considered consented
        UserSession session = new UserSession();
        session.setConsentStatuses(new HashMap<>());
        assertFalse(session.hasSignedMostRecentConsent());
        
        // All required consents are consented, even one that's not up-to-date
        session = new UserSession();
        session.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false, TIMESTAMP.getMillis()),
            new ConsentStatus("Name", "guid2", true, true, true, TIMESTAMP.getMillis()),
            new ConsentStatus("Name", "guid3", false, false, false, TIMESTAMP.getMillis())
        ));
        assertFalse(session.hasSignedMostRecentConsent());
        
        // A required consent is not consented
        session = new UserSession();
        session.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, false, TIMESTAMP.getMillis()),
            new ConsentStatus("Name", "guid2", true, false, false, TIMESTAMP.getMillis()),
            new ConsentStatus("Name", "guid3", false, false, false, TIMESTAMP.getMillis())
        ));
        assertFalse(session.hasSignedMostRecentConsent());
        
        session = new UserSession();
        session.setConsentStatuses(TestUtils.toMap(
            new ConsentStatus("Name", "guid1", true, true, true, TIMESTAMP.getMillis()),
            new ConsentStatus("Name", "guid3", false, false, false, TIMESTAMP.getMillis())
        ));
        // Again, we don't count optional consents, only required consents.
        assertTrue(session.hasSignedMostRecentConsent());
    }
    
    @Test
    public void emailVerifiedStatusCorrectlySetForSerializedSessions() throws Exception {
        // If a persisted session is retrieved, we should set emailVerified correctly if we can
        String json = TestUtils.createJson("{"+
            "'authenticated':false,"+
            "'participant':{'dataGroups':[],"+
                "'attributes':{},"+
                "'consentHistories':{},"+
                "'roles':[],"+
                "'languages':[],"+
                "'status':'enabled'},"+ // STATUS = ENABLED so email has been verified
            "'consentStatuses':{}}");
        
        UserSession session = BridgeObjectMapper.get().readValue(json, UserSession.class);
        assertEquals(session.getParticipant().getStatus(), ENABLED);
        assertEquals(session.getParticipant().getEmailVerified(), TRUE);
    }
}

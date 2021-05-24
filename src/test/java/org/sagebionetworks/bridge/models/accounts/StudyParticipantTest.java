package org.sagebionetworks.bridge.models.accounts;

import static java.lang.Boolean.TRUE;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_NOTE;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.SPONSORS_AND_PARTNERS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

public class StudyParticipantTest {

    private static final String ACCOUNT_ID = "6278uk74xoQkXkrbh9vJnh";
    private static final DateTime CREATED_ON = DateTime.now();
    private static final DateTime CREATED_ON_UTC = CREATED_ON.withZone(DateTimeZone.UTC);
    private static final Phone PHONE = TestConstants.PHONE;
    private static final Set<Roles> ROLES = ImmutableSet.of(Roles.ADMIN, Roles.WORKER);
    private static final List<String> LANGS = ImmutableList.of("en","fr");
    private static final Set<String> STUDIES = ImmutableSet.of("studyA", "studyB");
    private static final Set<String> DATA_GROUPS = Sets.newHashSet("group1","group2");
    private static final DateTimeZone TIME_ZONE = DateTimeZone.forOffsetHours(4);
    private static final Map<String,String> ATTRIBUTES = ImmutableMap.<String,String>builder()
            .put("A", "B")
            .put("C", "D").build();
    
    @Test
    public void hashEquals() {
        EqualsVerifier.forClass(StudyParticipant.class).allFieldsShouldBeUsed()
                .withPrefabValues(JsonNode.class, TestUtils.getClientData(), TestUtils.getOtherClientData()).verify();
    }
    
    @Test
    public void canSerializeForCache() throws Exception {
        StudyParticipant participant = createParticipantWithHealthCodes();

        String json = StudyParticipant.CACHE_WRITER.writeValueAsString(participant);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("firstName").textValue(), "firstName");
        assertEquals(node.get("lastName").textValue(), "lastName");
        assertEquals(node.get("email").textValue(), "email@email.com");
        assertEquals(node.get("externalId").textValue(), "externalId");
        assertEquals(node.get("synapseUserId").textValue(), SYNAPSE_USER_ID);
        assertEquals(node.get("password").textValue(), "newUserPassword");
        assertEquals(node.get("sharingScope").textValue(), "sponsors_and_partners");
        assertTrue(node.get("notifyByEmail").asBoolean());
        assertNull(node.get("healthCode"));
        assertNotNull(node.get("encryptedHealthCode"));
        assertTrue(node.get("consented").booleanValue());
        assertEquals(node.get("status").textValue(), "enabled");
        assertEquals(node.get("createdOn").textValue(), CREATED_ON_UTC.toString());
        assertEquals(node.get("id").textValue(), ACCOUNT_ID);
        assertEquals(node.get("studyIds").get(0).textValue(), "studyA");
        assertEquals(node.get("studyIds").get(1).textValue(), "studyB");
        assertEquals(node.get("timeZone").textValue(), "+04:00");
        assertEquals(node.get("externalIds").get("studyA").textValue(), "externalIdA");
        assertEquals(node.get("orgMembership").textValue(), TEST_ORG_ID);
        assertEquals(node.get("type").textValue(), "StudyParticipant");
        assertEquals(node.get("note").textValue(), TEST_NOTE);
        
        JsonNode clientData = node.get("clientData");
        assertTrue(clientData.get("booleanFlag").booleanValue());
        assertEquals(clientData.get("stringValue").textValue(), "testString");
        assertEquals(clientData.get("intValue").intValue(), 4);

        Set<String> roleNames = Sets.newHashSet(
                Roles.ADMIN.name().toLowerCase(), Roles.WORKER.name().toLowerCase());
        ArrayNode rolesArray = (ArrayNode)node.get("roles");
        assertTrue(roleNames.contains(rolesArray.get(0).textValue()));
        assertTrue(roleNames.contains(rolesArray.get(1).textValue()));
        
        // This array the order is significant, it serializes LinkedHashSet
        ArrayNode langsArray = (ArrayNode)node.get("languages"); 
        assertEquals(langsArray.get(0).textValue(), "en");
        assertEquals(langsArray.get(1).textValue(), "fr");
        
        ArrayNode dataGroupsArray = (ArrayNode)node.get("dataGroups");
        assertTrue(DATA_GROUPS.contains(dataGroupsArray.get(0).textValue()));
        assertTrue(DATA_GROUPS.contains(dataGroupsArray.get(1).textValue()));

        assertEquals(node.get("attributes").get("A").textValue(), "B");
        assertEquals(node.get("attributes").get("C").textValue(), "D");
        assertEquals(node.size(), 28);
        
        StudyParticipant deserParticipant = BridgeObjectMapper.get().readValue(node.toString(), StudyParticipant.class);
        assertEquals(deserParticipant.getFirstName(), "firstName");
        assertEquals(deserParticipant.getLastName(), "lastName");
        assertEquals(deserParticipant.getEmail(), "email@email.com");
        assertEquals(deserParticipant.getExternalId(), "externalId");
        assertEquals(deserParticipant.getSynapseUserId(), SYNAPSE_USER_ID);
        assertEquals(deserParticipant.getPassword(), "newUserPassword");
        assertEquals(deserParticipant.getTimeZone(), TIME_ZONE);
        assertEquals(deserParticipant.getSharingScope(), SharingScope.SPONSORS_AND_PARTNERS);
        assertTrue(deserParticipant.isNotifyByEmail());
        assertEquals(deserParticipant.getDataGroups(), DATA_GROUPS);
        assertEquals(deserParticipant.getStudyIds(), STUDIES);
        assertEquals(deserParticipant.getHealthCode(), TestConstants.UNENCRYPTED_HEALTH_CODE);
        // This is encrypted with different series of characters each time, so just verify it is there.
        assertNotNull(deserParticipant.getEncryptedHealthCode());
        assertEquals(deserParticipant.getAttributes(), ATTRIBUTES);
        assertTrue(deserParticipant.isConsented());
        assertEquals(deserParticipant.getCreatedOn(), CREATED_ON_UTC);
        assertEquals(deserParticipant.getStatus(), AccountStatus.ENABLED);
        assertEquals(deserParticipant.getId(), ACCOUNT_ID);
        assertEquals(deserParticipant.getExternalIds().get("studyA"), "externalIdA");
        assertEquals(deserParticipant.getOrgMembership(), TEST_ORG_ID);
        assertEquals(deserParticipant.getNote(), TEST_NOTE);
        
        UserConsentHistory deserHistory = deserParticipant.getConsentHistories().get("AAA").get(0);
        assertEquals(deserHistory.getBirthdate(), "2002-02-02");
        assertEquals(deserHistory.getConsentCreatedOn(), 1000000L);
        assertEquals(deserHistory.getSignedOn(), 2000000L);
        assertEquals(deserHistory.getName(), "Test User");
        assertEquals(deserHistory.getSubpopulationGuid(), "AAA");
        assertEquals(deserHistory.getWithdrewOn(), new Long(3000000L));
    }
    
    @Test
    public void canSerializeForAPIWithNoHealthCode() throws Exception {
        StudyParticipant participant = createParticipantWithHealthCodes();

        String json = StudyParticipant.API_NO_HEALTH_CODE_WRITER.writeValueAsString(participant);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertNull(node.get("healthCode"));
        assertNull(node.get("encryptedHealthCode"));
    }
    
    @Test
    public void canSerializeForAPIWithHealthCode() throws Exception {
        StudyParticipant participant = createParticipantWithHealthCodes();

        String json = StudyParticipant.API_WITH_HEALTH_CODE_WRITER.writeValueAsString(participant);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(node.get("healthCode").asText(), TestConstants.UNENCRYPTED_HEALTH_CODE);
        assertNull(node.get("encryptedHealthCode"));
    }

    @Test
    public void canCopy() {
        StudyParticipant participant = makeParticipant().build();
        StudyParticipant copy = new StudyParticipant.Builder().copyOf(participant).build();
        
        assertEquals(copy.getFirstName(), "firstName");
        assertEquals(copy.getLastName(), "lastName");
        assertEquals(copy.getEmail(), "email@email.com");
        assertEquals(copy.getPhone().getNationalFormat(), PHONE.getNationalFormat());
        assertEquals(copy.getEmailVerified(), TRUE);
        assertEquals(copy.getPhoneVerified(), TRUE);
        assertEquals(copy.getExternalId(), "externalId");
        assertEquals(copy.getSynapseUserId(), SYNAPSE_USER_ID);
        assertEquals(copy.getPassword(), "newUserPassword");
        assertEquals(copy.getTimeZone(), TIME_ZONE);
        assertEquals(copy.getSharingScope(), SPONSORS_AND_PARTNERS);
        assertTrue(copy.isNotifyByEmail());
        assertEquals(copy.getDataGroups(), DATA_GROUPS);
        assertEquals(copy.getHealthCode(), "healthCode");
        assertEquals(copy.getAttributes(), ATTRIBUTES);
        assertTrue(copy.isConsented());
        assertEquals(copy.getCreatedOn(), CREATED_ON);
        assertEquals(copy.getStatus(), ENABLED);
        assertEquals(copy.getStudyIds(), STUDIES);
        assertEquals(copy.getId(), ACCOUNT_ID);
        assertEquals(copy.getClientData(), TestUtils.getClientData());
        assertEquals(copy.getNote(), TEST_NOTE);
        
        // And they are equal in the Java sense
        assertEquals(copy, participant);
    }
    
    @Test
    public void canCopyGetFirstName() {
        assertCopyField("firstName", (builder)-> verify(builder).withFirstName(any()));
    }
    @Test
    public void canCopyGetLastName() {
        assertCopyField("lastName", (builder)-> verify(builder).withLastName(any()));
    }
    @Test
    public void canCopyGetSharingScope() {
        assertCopyField("sharingScope", (builder)-> verify(builder).withSharingScope(any()));
    }
    @Test
    public void canCopyIsNotifyByEmail() {
        assertCopyField("notifyByEmail", (builder)-> verify(builder).withNotifyByEmail(any()));
    }
    @Test
    public void canCopyGetDataGroups() {
        assertCopyField("dataGroups", (builder)-> verify(builder).withDataGroups(any()));
    }
    @Test
    public void canCopyGetHealthCode() {
        assertCopyField("healthCode", (builder)-> verify(builder).withHealthCode(any()));
    }
    @Test
    public void canCopyGetAttributes() {
        assertCopyField("attributes", (builder)-> verify(builder).withAttributes(any()));
    }
    @Test
    public void canCopyGetConsentHistories() {
        assertCopyField("consentHistories", (builder)-> verify(builder).withConsentHistories(any()));
    }
    @Test
    public void canCopyIsConsented() {
        assertCopyField("consented", builder -> verify(builder).withConsented(true));
    }
    @Test
    public void canCopyGetRoles() {
        assertCopyField("roles", (builder)-> verify(builder).withRoles(any()));
    }
    @SuppressWarnings("unchecked")
    @Test
    public void canCopyGetLanguages() {
        assertCopyField("languages", (builder)-> verify(builder).withLanguages(any(List.class)));
    }
    @Test
    public void canCopyGetStatus() {
        assertCopyField("status", (builder)-> verify(builder).withStatus(any()));
    }
    @Test
    public void canCopyGetCreatedOn() {
        assertCopyField("createdOn", (builder)-> verify(builder).withCreatedOn(any()));
    }
    @Test
    public void canCopyGetId() {
        assertCopyField("id", (builder)-> verify(builder).withId(any()));
    }
    @Test
    public void canCopyGetTimeZone() {
        assertCopyField("timeZone", (builder)-> verify(builder).withTimeZone(any()));
    }
    @Test
    public void canCopyGetClientData() {
        assertCopyField("clientData", (builder)-> verify(builder).withClientData(any()));
    }
    @Test
    public void canCopyPhone() {
        assertCopyField("phone", (builder)-> verify(builder).withPhone(any()));
    }
    @Test
    public void canCopyEmailVerified() {
        assertCopyField("emailVerified", (builder)-> verify(builder).withEmailVerified(any()));
    }
    @Test
    public void canCopyPhoneVerified() {
        assertCopyField("phoneVerified", (builder)-> verify(builder).withPhoneVerified(any()));
    }
    @Test
    public void canCopyStudiesVerified() {
        assertCopyField("studyIds", (builder)-> verify(builder).withStudyIds(any()));
    }
    @Test
    public void canCopyExternalIdsVerified() { 
        assertCopyField("externalIds", (builder)-> verify(builder).withExternalIds(any()));
    }
    @Test
    public void canCopyGetSynapseUserId() {
        assertCopyField("synapseUserId", (builder) -> verify(builder).withSynapseUserId(any()));
    }
    @Test
    public void canCopyOrgMembership() {
        assertCopyField("orgMembership", (builder) -> verify(builder).withOrgMembership(any()));
    }
    @Test
    public void canCopyNote() {
        assertCopyField("note", (builder) -> verify(builder).withNote(any()));
    }
    
    @Test
    public void testNullResiliency() {
        // We don't remove nulls from the collections, at least not when reading them.
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(null)
                .withAttributes(null)
                .withRoles(null)
                .withLanguages(null).build();
        
        assertTrue(participant.getDataGroups().isEmpty());
        assertTrue(participant.getAttributes().isEmpty());
        assertTrue(participant.getRoles().isEmpty());
        assertTrue(participant.getLanguages().isEmpty());
        assertTrue(participant.getStudyIds().isEmpty());
    }
    
    @Test
    public void nullParametersBreakNothing() {
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com")
                .withPassword("password").withConsented(null).withStudyIds(null).build();
        
        assertTrue(participant.getRoles().isEmpty());
        assertTrue(participant.getDataGroups().isEmpty());
        assertTrue(participant.getStudyIds().isEmpty());
        assertNull(participant.isConsented());
    }
    
    @Test
    public void oldJsonParsesCorrectly() throws Exception {
        // Old clients will continue to submit a username, this will be ignored.
        String json = TestUtils.createJson("{'substudyIds': ['A', 'B'], 'email':'email@email.com',"
                +"'username':'username@email.com','password':'password','roles':[],'dataGroups':"
                +"[],'type':'SignUp'}");
        
        StudyParticipant participant = BridgeObjectMapper.get().readValue(json, StudyParticipant.class);
        assertEquals(participant.getEmail(), "email@email.com");
        assertEquals(participant.getPassword(), "password");
        assertEquals(participant.getStudyIds(), ImmutableSet.of("A", "B"));
    }
    
    @Test
    public void legacyAccountsWithoutEmailVerificationAreFixed() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.ENABLED).build();
        assertEquals(participant.getEmailVerified(), Boolean.TRUE);
        assertEquals(participant.getStatus(), AccountStatus.ENABLED);
        
        participant = new StudyParticipant.Builder().withStatus(AccountStatus.UNVERIFIED).build();
        assertEquals(participant.getEmailVerified(), Boolean.FALSE);
        assertEquals(participant.getStatus(), AccountStatus.UNVERIFIED);
        
        // WHen disabled, we don't absolutely know the status of email verification.
        participant = new StudyParticipant.Builder().withStatus(AccountStatus.DISABLED).build();
        assertNull(participant.getEmailVerified());
        assertEquals(participant.getStatus(), AccountStatus.DISABLED);
    }
    
    @Test
    public void ifEmailVerifiedIsSetItWillNotBeChanged() {
        // because emailVerified is set, it will not be changed by legacy fix
        StudyParticipant participant = new StudyParticipant.Builder()
                .withStatus(AccountStatus.ENABLED)
                .withEmailVerified(Boolean.FALSE).build();
        assertEquals(participant.getEmailVerified(), Boolean.FALSE);
        assertEquals(participant.getStatus(), AccountStatus.ENABLED);
    }
    

    private void assertCopyField(String fieldName, Consumer<StudyParticipant.Builder> predicate) {
        StudyParticipant participant = makeParticipant().build();
        StudyParticipant.Builder builder = spy(StudyParticipant.Builder.class);
        Set<String> fieldsToCopy = Sets.newHashSet(fieldName);
        
        builder.copyFieldsOf(participant, fieldsToCopy);
        verify(builder).copyFieldsOf(participant, fieldsToCopy);
        predicate.accept(builder);
        verifyNoMoreInteractions(builder);
    }
    
    private StudyParticipant createParticipantWithHealthCodes() {
        StudyParticipant.Builder builder = makeParticipant();
        builder.withHealthCode(null).withEncryptedHealthCode(TestConstants.ENCRYPTED_HEALTH_CODE);
        return builder.build();
    }

    private StudyParticipant.Builder makeParticipant() {
        JsonNode clientData = TestUtils.getClientData();
        
        StudyParticipant.Builder builder = new StudyParticipant.Builder()
                .withFirstName("firstName")
                .withLastName("lastName")
                .withEmail("email@email.com")
                .withPhone(PHONE)
                .withPhoneVerified(TRUE)
                .withEmailVerified(TRUE)
                .withExternalId("externalId")
                .withSynapseUserId(SYNAPSE_USER_ID)
                .withPassword("newUserPassword")
                .withSharingScope(SPONSORS_AND_PARTNERS)
                .withNotifyByEmail(true)
                .withDataGroups(DATA_GROUPS)
                .withHealthCode("healthCode")
                .withAttributes(ATTRIBUTES)
                .withConsented(true)
                .withRoles(ROLES)
                .withLanguages(LANGS)
                .withStudyIds(STUDIES)
                .withExternalIds(ImmutableMap.of("studyA","externalIdA"))
                .withCreatedOn(CREATED_ON)
                .withId(ACCOUNT_ID)
                .withStatus(AccountStatus.ENABLED)
                .withClientData(clientData)
                .withTimeZone(TIME_ZONE)
                .withOrgMembership(TEST_ORG_ID)
                .withNote(TEST_NOTE);
        
        Map<String,List<UserConsentHistory>> historiesMap = Maps.newHashMap();
        
        List<UserConsentHistory> histories = Lists.newArrayList();
        UserConsentHistory history = new UserConsentHistory.Builder()
                .withBirthdate("2002-02-02")
                .withConsentCreatedOn(1000000L)
                .withSignedOn(2000000L)
                .withName("Test User")
                .withSubpopulationGuid(SubpopulationGuid.create("AAA"))
                .withWithdrewOn(3000000L).build();
        histories.add(history);
        historiesMap.put("AAA", histories);
        builder.withConsentHistories(historiesMap);
        
        return builder;
    }
    
    @Test
    public void duplicateLanguagesAreRemoved() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withLanguages(Lists.newArrayList("en", "fr", "en", "de")).build();
        
        assertEquals(participant.getLanguages(), Lists.newArrayList("en","fr","de"));
    }
    
    @Test
    public void setExternalIdReturned() {
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId("oneExternalId").build();
        assertEquals(participant.getExternalId(), "oneExternalId");
    }
    
    @Test
    public void nullExternalIdMapReturnsNullExternalIdField() { 
        StudyParticipant participant = new StudyParticipant.Builder().withExternalIds(null).build();
        assertNull(participant.getExternalId());
    }
    
    @Test
    public void emptyExternalIdMapReturnsNullExternalIdField() {
        StudyParticipant participant = new StudyParticipant.Builder().withExternalIds(ImmutableMap.of()).build();
        assertNull(participant.getExternalId());
    }
    
    @Test
    public void externalIdRetrievedFromMap() { 
        StudyParticipant participant = new StudyParticipant.Builder()
                .withExternalIds(ImmutableMap.of("oneStudy", "oneExternalId")).build();
        assertEquals(participant.getExternalId(), "oneExternalId");
    }
}

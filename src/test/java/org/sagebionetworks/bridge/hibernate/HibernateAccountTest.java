package org.sagebionetworks.bridge.hibernate;

import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.LANGUAGES;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_EXTERNAL_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_ORG_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_DATA_GROUPS;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.DISABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.ENABLED;
import static org.sagebionetworks.bridge.models.accounts.AccountStatus.UNVERIFIED;
import static org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm.BCRYPT;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.ALL_QUALIFIED_RESEARCHERS;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class HibernateAccountTest {
    private static final Set<Enrollment> ENROLLMENTS = ImmutableSet
            .of(Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, TEST_EXTERNAL_ID));
    
    private static final SubpopulationGuid GUID1 = SubpopulationGuid.create("guid1");
    private static final SubpopulationGuid GUID2 = SubpopulationGuid.create("guid2");

    private static final long TIME1 = 100L;
    private static final long TIME2 = 200L;
    private static final long TIME3 = 300L;
    private static final long TIME4 = 400L;
    private static final long TIME5 = 500L;
    
    // We're only concerned with serializing this model...the StudyParticipant can
    // be used to deserialize an Account model sent from our SDK (it's a superset
    // of an account record).
    @Test
    public void canSerialize() throws Exception {
        Account account = new HibernateAccount();
        account.setId("id");
        account.setAppId(TEST_APP_ID);
        account.setOrgMembership("orgId");
        account.setEmail("email");
        account.setSynapseUserId("synapseUserId");
        account.setPhone(PHONE);
        account.setEmailVerified(true);
        account.setPhoneVerified(true);
        account.setAttributes(ImmutableMap.of("a", "b", "c", "d"));
        account.setCreatedOn(CREATED_ON);
        account.setModifiedOn(MODIFIED_ON);
        account.setFirstName("firstName");
        account.setLastName("lastName");
        account.setRoles(ImmutableSet.of(DEVELOPER, RESEARCHER));
        account.setStatus(ENABLED);
        account.setClientData(TestUtils.getClientData());
        account.setVersion(1);
        account.setTimeZone(DateTimeZone.UTC);
        account.setDataGroups(USER_DATA_GROUPS);
        account.setLanguages(LANGUAGES);
        account.setReauthToken("reauthToken");
        account.setHealthCode("healthCode");
        account.setPasswordAlgorithm(BCRYPT);
        account.setPasswordHash("hash");
        account.setPasswordModifiedOn(MODIFIED_ON);
        account.setReauthToken("reauthToken");
        account.setTimeZone(UTC);
        account.setSharingScope(ALL_QUALIFIED_RESEARCHERS);
        account.setNotifyByEmail(true);
        account.setMigrationVersion(3);
        
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", TEST_USER_ID);
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en1, en2));
        
        // Do this just to verify it is not included in the serialization.
        addConsentHistories(account);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(account);
        assertEquals(node.get("type").textValue(), "Account");
        assertEquals(node.size(), 19);
        assertEquals(node.get("id").textValue(), "id");
        assertEquals(node.get("orgMembership").textValue(), "orgId");
        assertEquals(node.get("email").textValue(), "email");
        assertEquals(node.get("synapseUserId").textValue(), "synapseUserId");
        assertEquals(node.get("phone").get("nationalFormat").textValue(), PHONE.getNationalFormat());
        assertEquals(node.get("phone").get("regionCode").textValue(), PHONE.getRegionCode());
        assertTrue(node.get("emailVerified").booleanValue());
        assertTrue(node.get("phoneVerified").booleanValue());
        assertEquals(node.get("attributes").get("a").textValue(), "b");
        assertEquals(node.get("attributes").get("c").textValue(), "d");
        assertEquals(node.get("createdOn").textValue(), CREATED_ON.toString());
        assertEquals(node.get("modifiedOn").textValue(), MODIFIED_ON.toString());
        assertEquals(node.get("firstName").textValue(), "firstName");
        assertEquals(node.get("lastName").textValue(), "lastName");
        assertEquals(toSet(node, "roles"), ImmutableSet.of("developer", "researcher"));
        assertEquals(node.get("status").textValue(), "enabled");
        assertNotNull(node.get("clientData"));
        assertEquals(node.get("version").intValue(), 1);
        assertEquals(toSet(node, "dataGroups"), ImmutableSet.of("group1", "group2"));
        assertEquals(toSet(node, "languages"), ImmutableSet.of("en", "fr"));
        
        // these should be null
        assertNull(node.get("appId"));
        assertNull(node.get("consents"));
        assertNull(node.get("healthCode"));
        assertNull(node.get("passwordAlgorithm"));
        assertNull(node.get("passwordHash"));
        assertNull(node.get("passwordModifiedOn"));
        assertNull(node.get("reauthToken"));
        assertNull(node.get("timeZone"));
        assertNull(node.get("sharingScope"));
        assertNull(node.get("notifyByEmail"));
        assertNull(node.get("migrationVersion"));
        assertNull(node.get("enrollments"));
        assertNull(node.get("reauthToken"));
        assertNull(node.get("activeConsentSignature"));
        assertNull(node.get("consentSignatureHistory"));
        assertNull(node.get("allConsentSignatureHistories"));
        assertNull(node.get("activeEnrollments"));
    }
    
    private Set<String> toSet(JsonNode node, String field) {
        Set<String> set = new HashSet<>();
        for (int i=0; i < node.get(field).size(); i++) {
            set.add(node.get(field).get(i).textValue());
        }
        return set;
    }
    
    @Test
    public void attributes() {
        HibernateAccount account = new HibernateAccount();

        // Can set and get attributes.
        Map<String, String> originalAttrMap = new HashMap<>();
        originalAttrMap.put("foo", "foo-value");
        account.setAttributes(originalAttrMap);

        Map<String, String> gettedAttrMap1 = account.getAttributes();
        assertEquals(gettedAttrMap1.size(), 1);
        assertEquals(gettedAttrMap1.get("foo"), "foo-value");

        // Putting values in the map reflect through to the account object.
        gettedAttrMap1.put("bar", "bar-value");

        Map<String, String> gettedAttrMap2 = account.getAttributes();
        assertEquals(gettedAttrMap2.size(), 2);
        assertEquals(gettedAttrMap2.get("foo"), "foo-value");
        assertEquals(gettedAttrMap2.get("bar"), "bar-value");

        // Setting attributes to null clears it and returns a new empty map.
        account.setAttributes(null);

        Map<String, String> gettedAttrMap3 = account.getAttributes();
        assertTrue(gettedAttrMap3.isEmpty());

        // Similarly, putting values to the map reflect through.
        gettedAttrMap3.put("baz", "baz-value");

        Map<String, String> gettedAttrMap4 = account.getAttributes();
        assertEquals(gettedAttrMap4.size(), 1);
        assertEquals(gettedAttrMap4.get("baz"), "baz-value");
    }

    @Test
    public void consents() {
        HibernateAccount account = new HibernateAccount();

        // Create dummy consents and keys.
        HibernateAccountConsentKey fooConsentKey = new HibernateAccountConsentKey("foo-guid", 1111);
        HibernateAccountConsentKey barConsentKey = new HibernateAccountConsentKey("bar-guid", 2222);
        HibernateAccountConsentKey bazConsentKey = new HibernateAccountConsentKey("baz-guid", 3333);

        HibernateAccountConsent fooConsent = new HibernateAccountConsent();
        HibernateAccountConsent barConsent = new HibernateAccountConsent();
        HibernateAccountConsent bazConsent = new HibernateAccountConsent();

        // Can set and get.
        Map<HibernateAccountConsentKey, HibernateAccountConsent> originalConsentMap = new HashMap<>();
        originalConsentMap.put(fooConsentKey, fooConsent);
        account.setConsents(originalConsentMap);

        Map<HibernateAccountConsentKey, HibernateAccountConsent> gettedConsentMap1 = account.getConsents();
        assertEquals(gettedConsentMap1.size(), 1);
        assertSame(gettedConsentMap1.get(fooConsentKey), fooConsent);

        // Putting values in the map reflect through to the account object.
        gettedConsentMap1.put(barConsentKey, barConsent);

        Map<HibernateAccountConsentKey, HibernateAccountConsent> gettedConsentMap2 = account.getConsents();
        assertEquals(gettedConsentMap2.size(), 2);
        assertSame(gettedConsentMap2.get(fooConsentKey), fooConsent);
        assertSame(gettedConsentMap2.get(barConsentKey), barConsent);

        // Setting to null clears the map. Getting again initializes a new empty map.
        account.setConsents(null);

        Map<HibernateAccountConsentKey, HibernateAccountConsent> gettedConsentMap3 = account.getConsents();
        assertTrue(gettedConsentMap3.isEmpty());

        // Similarly, putting values to the map reflect through.
        gettedConsentMap3.put(bazConsentKey, bazConsent);

        Map<HibernateAccountConsentKey, HibernateAccountConsent> gettedConsentMap4 = account.getConsents();
        assertEquals(gettedConsentMap4.size(), 1);
        assertSame(gettedConsentMap4.get(bazConsentKey), bazConsent);
    }

    @Test
    public void roles() {
        HibernateAccount account = new HibernateAccount();

        // Can set and get.
        account.setRoles(EnumSet.of(ADMIN));
        Set<Roles> gettedRoleSet1 = account.getRoles();
        assertEquals(gettedRoleSet1, EnumSet.of(ADMIN));

        // Putting values in the set reflect through to the account object.
        gettedRoleSet1.add(DEVELOPER);
        assertEquals(account.getRoles(), EnumSet.of(ADMIN, DEVELOPER));

        // Setting to null clears the set. Getting again initializes a new empty set.
        account.setRoles(null);
        Set<Roles> gettedRoleSet2 = account.getRoles();
        assertTrue(gettedRoleSet2.isEmpty());

        // Similarly, putting values to the set reflect through.
        gettedRoleSet2.add(RESEARCHER);
        assertEquals(account.getRoles(), EnumSet.of(RESEARCHER));
    }
    
    @Test
    public void accountSummaryConstructor() {
        HibernateAccount account = new HibernateAccount(new DateTime(123L), TEST_APP_ID, TEST_ORG_ID, "firstName",
                "lastName", "email", PHONE, "id", UNVERIFIED, SYNAPSE_USER_ID);

        assertEquals(account.getCreatedOn().getMillis(), 123L);
        assertEquals(account.getAppId(), TEST_APP_ID);
        assertEquals(account.getOrgMembership(), TEST_ORG_ID);
        assertEquals(account.getFirstName(), "firstName");
        assertEquals(account.getLastName(), "lastName");
        assertEquals(account.getEmail(), "email");
        assertEquals(account.getPhone(), PHONE);
        assertEquals(account.getId(), "id");
        assertEquals(account.getStatus(), ENABLED); // thanks to synapseUserId
        assertEquals(account.getSynapseUserId(), SYNAPSE_USER_ID);
    }
    
    @Test
    public void dataGroups() {
        HibernateAccount account = new HibernateAccount();
        assertTrue(account.getDataGroups().isEmpty());

        // Set works.
        account.setDataGroups(Sets.newHashSet("A","B"));
        assertEquals(account.getDataGroups(), Sets.newHashSet("A","B"));

        // Setting to null makes it an empty set.
        account.setDataGroups(null);
        assertTrue(account.getDataGroups().isEmpty());

        account.setDataGroups(Sets.newHashSet());
        assertTrue(account.getRoles().isEmpty());
    }
    
    @Test
    public void languages() {
        // Hibernate cannot deal with the LinkedHashSet type, so we store languages as a list to 
        // maintain their order. When transferred to an object implementing the Account class, these
        // are transferred to a LinkedHashSet, which also ensures the language codes are not 
        // duplicated in the ordered set.
        List<String> langs = Lists.newArrayList("en","fr");
        
        HibernateAccount account = new HibernateAccount();
        assertTrue(account.getLanguages().isEmpty());
        
        // Set works.
        account.setLanguages(langs);
        assertEquals(account.getLanguages(), langs);
        
        // Setting to null makes it an empty set.
        account.setLanguages(null);
        assertTrue(account.getLanguages().isEmpty());
    }
    
    @Test
    public void sharingDefaultsToNoSharing() {
        HibernateAccount account = new HibernateAccount();
        assertEquals(account.getSharingScope(), NO_SHARING);
    }
    
    @Test
    public void getNotifyByEmailDefaultsToTrue() {
        HibernateAccount account = new HibernateAccount();
        assertTrue(account.getNotifyByEmail());
    }

    @Test
    public void consentSignatureHistories() {
        HibernateAccount account = new HibernateAccount();
        addConsentHistories(account);
        
        // Test getAllConsentSignaturehistories()
        Map<SubpopulationGuid, List<ConsentSignature>> histories = account.getAllConsentSignatureHistories();
        
        List<ConsentSignature> history1 = histories.get(GUID1);
        assertEquals(history1.size(), 3);
        // Signed on values are copied over from keys
        assertEquals(history1.get(0).getSignedOn(), TIME1);
        assertEquals(history1.get(1).getSignedOn(), TIME2);
        assertEquals(history1.get(2).getSignedOn(), TIME3);
        
        List<ConsentSignature> history2 = histories.get(GUID2);
        assertEquals(history2.size(), 2);
        // Signed on values are copied over from keys
        assertEquals(history2.get(0).getSignedOn(), TIME4);
        assertEquals(history2.get(1).getSignedOn(), TIME5);
        
        // Test getConsentSignatureHistory(guid). Should produce identical results.
        history1 = account.getConsentSignatureHistory(GUID1);
        assertEquals(history1.size(), 3);
        // Signed on values are copied over from keys
        assertEquals(history1.get(0).getSignedOn(), TIME1);
        assertEquals(history1.get(1).getSignedOn(), TIME2);
        assertEquals(history1.get(2).getSignedOn(), TIME3);
        
        history2 = account.getConsentSignatureHistory(GUID2);
        assertEquals(history2.size(), 2);
        // Signed on values are copied over from keys
        assertEquals(history2.get(0).getSignedOn(), TIME4);
        assertEquals(history2.get(1).getSignedOn(), TIME5);
        
        // The last consent in the series was withdrawn, so this consent is not active.
        ConsentSignature sig1 = account.getActiveConsentSignature(GUID1);
        assertNull(sig1);
        
        ConsentSignature sig2 = account.getActiveConsentSignature(GUID2);
        assertEquals(history2.get(1), sig2);
        
        // Add a consent to the withdrawn series.
        ConsentSignature sig3 = new ConsentSignature.Builder().withBirthdate("1980-01-01")
                .withConsentCreatedOn(1L).withName("Name").withSignedOn(600L).build();
        
        List<ConsentSignature> signatures = Lists.newArrayList();
        signatures.addAll(history1);
        signatures.add(sig3);
        account.setConsentSignatureHistory(GUID1, signatures);
        
        sig1 = account.getActiveConsentSignature(GUID1);
        assertEquals(account.getAllConsentSignatureHistories().get(GUID1).get(3), sig1);
    }
    
    @Test
    public void collectionsNotNull() {
        HibernateAccount account = new HibernateAccount();
        assertTrue(account.getEnrollments().isEmpty());
        assertTrue(account.getAttributes().isEmpty());
        assertTrue(account.getConsents().isEmpty());
        assertTrue(account.getDataGroups().isEmpty());
        assertTrue(account.getLanguages().isEmpty());
        assertTrue(account.getRoles().isEmpty());
        assertTrue(account.getConsentSignatureHistory(SubpopulationGuid.create("nada")).isEmpty());
        assertTrue(account.getAllConsentSignatureHistories().isEmpty());
    }
    
    @Test
    public void getActiveEnrollments() {
        Enrollment en1 = Enrollment.create(TEST_APP_ID, "studyA", TEST_USER_ID);
        Enrollment en2 = Enrollment.create(TEST_APP_ID, "studyB", TEST_USER_ID);
        Enrollment en3 = Enrollment.create(TEST_APP_ID, "studyC", TEST_USER_ID);
        en2.setWithdrawnOn(CREATED_ON);
        Set<Enrollment> enrollments = ImmutableSet.of(en1, en2, en3);
        
        HibernateAccount account = new HibernateAccount();
        account.setEnrollments(enrollments);
        
        assertEquals(account.getActiveEnrollments(), ImmutableSet.of(en1, en3));
    }
    
    @Test
    public void statusUnverifiedByDefault() { 
        Account account = Account.create();
        assertEquals(account.getStatus(), UNVERIFIED);
    }
    
    @Test
    public void statusDisabled() {
        Account account = Account.create();
        account.setStatus(DISABLED);
        
        // even setting a verifiable pathway doesn't change this.
        account.setSynapseUserId(SYNAPSE_USER_ID);
        assertEquals(account.getStatus(), DISABLED);
    }
    
    @Test
    public void statusForExternalIdAccountEnabled() {
        Account account = Account.create();
        account.setPasswordHash("asdf");
        assertEquals(account.getStatus(), UNVERIFIED);
        
        account.setEnrollments(ENROLLMENTS);
        assertEquals(account.getStatus(), ENABLED);
    }
    
    @Test
    public void statusForEmailAccountEnabled() {
        Account account = Account.create();
        assertEquals(account.getStatus(), UNVERIFIED);
        account.setEmail(EMAIL);
        assertEquals(account.getStatus(), UNVERIFIED);
        
        account.setEmailVerified(true);
        assertEquals(account.getStatus(), ENABLED);
    }
    
    public void statusForPhoneAccountEnabled() {
        Account account = Account.create();
        assertEquals(account.getStatus(), UNVERIFIED);
        account.setPhone(PHONE);
        assertEquals(account.getStatus(), UNVERIFIED);
        
        account.setPhoneVerified(true);
        assertEquals(account.getStatus(), ENABLED);
    }
    
    public void statusForSynapseAccountEnabled() {
        Account account = Account.create();
        account.setSynapseUserId(SYNAPSE_USER_ID);
        
        assertEquals(account.getStatus(), ENABLED);
    }
    
    private HibernateAccountConsent getHibernateAccountConsent(Long withdrewOn) {
        HibernateAccountConsent consent = new HibernateAccountConsent();
        consent.setBirthdate("1980-01-01");
        consent.setConsentCreatedOn(1L);
        consent.setName("Test User");
        consent.setWithdrewOn(withdrewOn);
        return consent;
    }
    
    private void addConsentHistories(Account account) {
        HibernateAccountConsentKey key1A = new HibernateAccountConsentKey(GUID1.getGuid(), TIME1);
        HibernateAccountConsentKey key1B = new HibernateAccountConsentKey(GUID1.getGuid(), TIME2);
        HibernateAccountConsentKey key1C = new HibernateAccountConsentKey(GUID1.getGuid(), TIME3);
        HibernateAccountConsentKey key2A = new HibernateAccountConsentKey(GUID2.getGuid(), TIME4);
        HibernateAccountConsentKey key2B = new HibernateAccountConsentKey(GUID2.getGuid(), TIME5);
        
        HibernateAccountConsent consent1A = getHibernateAccountConsent(null);
        HibernateAccountConsent consent1B = getHibernateAccountConsent(null);
        HibernateAccountConsent consent1C = getHibernateAccountConsent(400L);
        HibernateAccountConsent consent2A = getHibernateAccountConsent(null);
        HibernateAccountConsent consent2B = getHibernateAccountConsent(null);
        
        // Add these out of order to verify that they are sorted by date of signing
        Map<HibernateAccountConsentKey, HibernateAccountConsent> consents = Maps.newHashMap();
        consents.put(key1A, consent1A);
        consents.put(key1C, consent1C);
        consents.put(key1B, consent1B);
        consents.put(key2B, consent2B);
        consents.put(key2A, consent2A);
        
        account.setConsents(consents);
    }
}

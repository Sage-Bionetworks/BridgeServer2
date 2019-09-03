package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.models.studies.OAuthProviderTest;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

/**
 * Main functionality we want to verify in this test is that study can be serialized with all values, 
 * but filtered in the API to exclude read-only studies when exposed to researchers.
 */
public class DynamoStudyTest {
    private static final List<AppleAppLink> APPLE_APP_LINKS = Lists.newArrayList(TestConstants.APPLE_APP_LINK);
    private static final List<AndroidAppLink> ANDROID_APP_LINKS = Lists.newArrayList(TestConstants.ANDROID_APP_LINK);

    @Test
    public void automaticCustomEventsIsNeverNull() {
        // Starts as empty
        Study study = Study.create();
        assertTrue(study.getAutomaticCustomEvents().isEmpty());

        // Set value works
        Map<String, String> dummyMap = ImmutableMap.of("3-days-after-enrollment", "P3D");
        study.setAutomaticCustomEvents(dummyMap);
        assertEquals(study.getAutomaticCustomEvents(), dummyMap);

        // Set to null makes it empty again
        study.setAutomaticCustomEvents(null);
        assertTrue(study.getAutomaticCustomEvents().isEmpty());
    }

    @Test
    public void uploadMetadataFieldDefListIsNeverNull() {
        // make field for test
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_V2).build());

        // starts as empty
        Study study = new DynamoStudy();
        assertTrue(study.getUploadMetadataFieldDefinitions().isEmpty());

        // set value works
        study.setUploadMetadataFieldDefinitions(fieldDefList);
        assertEquals(study.getUploadMetadataFieldDefinitions(), fieldDefList);

        // set to null makes it empty again
        study.setUploadMetadataFieldDefinitions(null);
        assertTrue(study.getUploadMetadataFieldDefinitions().isEmpty());
    }

    @Test
    public void reauthenticationEnabled() {
        // Starts as null.
        Study study = Study.create();
        assertNull(study.isReauthenticationEnabled());

        // Set to true.
        study.setReauthenticationEnabled(true);
        assertTrue(study.isReauthenticationEnabled());

        // Set to false.
        study.setReauthenticationEnabled(false);
        assertFalse(study.isReauthenticationEnabled());

        // Set back to null.
        study.setReauthenticationEnabled(null);
        assertNull(study.isReauthenticationEnabled());
    }

    @Test
    public void equalsHashCode() {
        // studyIdentifier is derived from the identifier
        EqualsVerifier.forClass(DynamoStudy.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS)
            .withPrefabValues(ObjectMapper.class, new ObjectMapper(), new ObjectMapper())
            .withPrefabValues(JsonFactory.class, new JsonFactory(), new JsonFactory()).verify();
    }

    @Test
    public void testToString() {
        // Basic test that toString doesn't crash.
        assertNotNull(Study.create().toString());
    }

    @Test
    public void studyFullySerializesForCaching() throws Exception {
        final DynamoStudy study = TestUtils.getValidStudy(DynamoStudyTest.class);
        
        OAuthProvider oauthProvider = new OAuthProvider("clientId", "secret", "endpoint",
                OAuthProviderTest.CALLBACK_URL, null);
        study.getOAuthProviders().put("myProvider", oauthProvider);

        study.setAutomaticCustomEvents(ImmutableMap.of("3-days-after-enrollment", "P3D"));
        study.setVersion(2L);
        study.setMinSupportedAppVersions(ImmutableMap.<String, Integer>builder().put(OperatingSystem.IOS, 2).build());
        study.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-metadata-field").withType(UploadFieldType.INT).build()));
        study.setAndroidAppLinks(ANDROID_APP_LINKS);
        study.setAppleAppLinks(APPLE_APP_LINKS);

        final JsonNode node = BridgeObjectMapper.get().valueToTree(study);

        assertTrue(node.get("autoVerificationEmailSuppressed").booleanValue());
        assertEqualsAndNotNull(study.getConsentNotificationEmail(), node.get("consentNotificationEmail").asText());
        assertFalse(node.get("participantIpLockingEnabled").booleanValue());
        assertTrue(node.get("studyIdExcludedInExport").booleanValue());
        assertEqualsAndNotNull(study.getSupportEmail(), node.get("supportEmail").asText());
        assertEqualsAndNotNull(study.getSynapseDataAccessTeamId(), node.get("synapseDataAccessTeamId").longValue());
        assertEqualsAndNotNull(study.getSynapseProjectId(), node.get("synapseProjectId").textValue());
        assertEqualsAndNotNull(study.getTechnicalEmail(), node.get("technicalEmail").asText());
        assertEqualsAndNotNull(study.getUploadValidationStrictness().toString().toLowerCase(),
                node.get("uploadValidationStrictness").textValue());
        assertTrue(node.get("usesCustomExportSchedule").asBoolean());
        assertEqualsAndNotNull(study.getSponsorName(), node.get("sponsorName").asText());
        assertEqualsAndNotNull(study.getName(), node.get("name").asText());
        assertEqualsAndNotNull(study.getShortName(), node.get("shortName").textValue());
        assertEqualsAndNotNull(study.isActive(), node.get("active").asBoolean());
        assertEqualsAndNotNull(study.getIdentifier(), node.get("identifier").asText());
        assertEqualsAndNotNull(study.getMinAgeOfConsent(), node.get("minAgeOfConsent").asInt());
        assertEqualsAndNotNull(study.getPasswordPolicy(), JsonUtils.asEntity(node, "passwordPolicy", PasswordPolicy.class));
        assertEqualsAndNotNull(study.getUserProfileAttributes(), JsonUtils.asStringSet(node, "userProfileAttributes"));
        assertEqualsAndNotNull(study.getTaskIdentifiers(), JsonUtils.asStringSet(node, "taskIdentifiers"));
        assertEqualsAndNotNull(study.getActivityEventKeys(), JsonUtils.asStringSet(node, "activityEventKeys"));
        assertEqualsAndNotNull(study.getDataGroups(), JsonUtils.asStringSet(node, "dataGroups"));
        assertEqualsAndNotNull(study.getVersion(), node.get("version").longValue());
        assertTrue(node.get("strictUploadValidationEnabled").asBoolean());
        assertTrue(node.get("healthCodeExportEnabled").asBoolean());
        assertTrue(node.get("emailVerificationEnabled").asBoolean());
        assertTrue(node.get("externalIdRequiredOnSignup").asBoolean());
        assertTrue(node.get("emailSignInEnabled").asBoolean());
        assertTrue(node.get("reauthenticationEnabled").booleanValue());
        assertTrue(node.get("autoVerificationPhoneSuppressed").booleanValue());
        assertTrue(node.get("verifyChannelOnSignInEnabled").booleanValue());
        assertEquals(node.get("accountLimit").asInt(), 0);
        assertFalse(node.get("disableExport").asBoolean());
        assertEqualsAndNotNull("Study", node.get("type").asText());
        assertEqualsAndNotNull(study.getPushNotificationARNs().get(OperatingSystem.IOS),
                node.get("pushNotificationARNs").get(OperatingSystem.IOS).asText());
        assertEqualsAndNotNull(study.getPushNotificationARNs().get(OperatingSystem.ANDROID),
                node.get("pushNotificationARNs").get(OperatingSystem.ANDROID).asText());
        
        JsonNode automaticCustomEventsNode = node.get("automaticCustomEvents");
        assertEquals(automaticCustomEventsNode.size(), 1);
        assertEquals(automaticCustomEventsNode.get("3-days-after-enrollment").textValue(), "P3D");

        JsonNode appleLink = node.get("appleAppLinks").get(0);
        assertEquals("studyId", appleLink.get("appID").textValue());
        assertEquals("/appId/", appleLink.get("paths").get(0).textValue());
        assertEquals("/appId/*", appleLink.get("paths").get(1).textValue());
        
        JsonNode androidLink = node.get("androidAppLinks").get(0);
        assertEquals(androidLink.get("namespace").textValue(), "namespace");
        assertEquals(androidLink.get("package_name").textValue(), "package_name");
        assertEquals(androidLink.get("sha256_cert_fingerprints").get(0).textValue(), "sha256_cert_fingerprints");

        // validate minAppVersion
        JsonNode supportedVersionsNode = JsonUtils.asJsonNode(node, "minSupportedAppVersions");
        assertNotNull(supportedVersionsNode);
        assertEqualsAndNotNull(
                study.getMinSupportedAppVersions().get(OperatingSystem.IOS), 
                supportedVersionsNode.get(OperatingSystem.IOS).intValue());

        // validate metadata field defs
        JsonNode metadataFieldDefListNode = node.get("uploadMetadataFieldDefinitions");
        assertEquals(metadataFieldDefListNode.size(), 1);
        JsonNode oneMetadataFieldDefNode = metadataFieldDefListNode.get(0);
        assertEquals(oneMetadataFieldDefNode.get("name").textValue(), "test-metadata-field");
        assertEquals(oneMetadataFieldDefNode.get("type").textValue(), "int");

        JsonNode providerNode = node.get("oAuthProviders").get("myProvider");
        assertEquals(providerNode.get("clientId").textValue(), "clientId");
        assertEquals(providerNode.get("secret").textValue(), "secret");
        assertEquals(providerNode.get("endpoint").textValue(), "endpoint");
        assertEquals(providerNode.get("callbackUrl").textValue(), OAuthProviderTest.CALLBACK_URL);
        assertEquals(providerNode.get("type").textValue(), "OAuthProvider");
        
        JsonNode defaultTemplates = node.get("defaultTemplates");
        assertEquals(defaultTemplates.get("email_account_exists").textValue(), "ABC-DEF");
        
        // Deserialize back to a POJO and verify.
        final Study deserStudy = BridgeObjectMapper.get().readValue(node.toString(), Study.class);
        assertEquals(deserStudy, study);
    }
    
    @Test
    public void testThatEmptyMinSupportedVersionMapperDoesNotThrowException() throws Exception {
        final DynamoStudy study = TestUtils.getValidStudy(DynamoStudyTest.class);
        study.setVersion(2L);

        final String json = BridgeObjectMapper.get().writeValueAsString(study);
        BridgeObjectMapper.get().readTree(json);

        // Deserialize back to a POJO and verify.
        final Study deserStudy = BridgeObjectMapper.get().readValue(json, Study.class);
        assertEquals(deserStudy, study);
    }
    
    @Test
    public void settingStringOrObjectStudyIdentifierSetsTheOther() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test-study");
        assertEquals(new StudyIdentifierImpl("test-study"), study.getStudyIdentifier());
        
        study.setIdentifier(null);
        assertNull(study.getStudyIdentifier());
    }
    
    void assertEqualsAndNotNull(Object expected, Object actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(actual, expected);
    }
    
}

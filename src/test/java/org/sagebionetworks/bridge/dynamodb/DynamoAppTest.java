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
import org.sagebionetworks.bridge.models.studies.App;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

/**
 * Main functionality we want to verify in this test is that study can be serialized with all values, 
 * but filtered in the API to exclude read-only studies when exposed to researchers.
 */
public class DynamoAppTest {
    private static final List<AppleAppLink> APPLE_APP_LINKS = Lists.newArrayList(TestConstants.APPLE_APP_LINK);
    private static final List<AndroidAppLink> ANDROID_APP_LINKS = Lists.newArrayList(TestConstants.ANDROID_APP_LINK);

    @Test
    public void automaticCustomEventsIsNeverNull() {
        // Starts as empty
        App app = App.create();
        assertTrue(app.getAutomaticCustomEvents().isEmpty());

        // Set value works
        Map<String, String> dummyMap = ImmutableMap.of("3-days-after-enrollment", "P3D");
        app.setAutomaticCustomEvents(dummyMap);
        assertEquals(app.getAutomaticCustomEvents(), dummyMap);

        // Set to null makes it empty again
        app.setAutomaticCustomEvents(null);
        assertTrue(app.getAutomaticCustomEvents().isEmpty());
    }

    @Test
    public void uploadMetadataFieldDefListIsNeverNull() {
        // make field for test
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_V2).build());

        // starts as empty
        App app = new DynamoApp();
        assertTrue(app.getUploadMetadataFieldDefinitions().isEmpty());

        // set value works
        app.setUploadMetadataFieldDefinitions(fieldDefList);
        assertEquals(app.getUploadMetadataFieldDefinitions(), fieldDefList);

        // set to null makes it empty again
        app.setUploadMetadataFieldDefinitions(null);
        assertTrue(app.getUploadMetadataFieldDefinitions().isEmpty());
    }

    @Test
    public void reauthenticationEnabled() {
        // Starts as null.
        App app = App.create();
        assertNull(app.isReauthenticationEnabled());

        // Set to true.
        app.setReauthenticationEnabled(true);
        assertTrue(app.isReauthenticationEnabled());

        // Set to false.
        app.setReauthenticationEnabled(false);
        assertFalse(app.isReauthenticationEnabled());

        // Set back to null.
        app.setReauthenticationEnabled(null);
        assertNull(app.isReauthenticationEnabled());
    }

    @Test
    public void equalsHashCode() {
        // studyIdentifier is derived from the identifier
        EqualsVerifier.forClass(DynamoApp.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS)
            .withPrefabValues(ObjectMapper.class, new ObjectMapper(), new ObjectMapper())
            .withPrefabValues(JsonFactory.class, new JsonFactory(), new JsonFactory()).verify();
    }

    @Test
    public void testToString() {
        // Basic test that toString doesn't crash.
        assertNotNull(App.create().toString());
    }

    @Test
    public void studyFullySerializesForCaching() throws Exception {
        final DynamoApp study = TestUtils.getValidApp(DynamoAppTest.class);
        
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
        assertEquals(appleLink.get("appID").textValue(), "studyId");
        assertEquals(appleLink.get("paths").get(0).textValue(), "/appId/");
        assertEquals(appleLink.get("paths").get(1).textValue(), "/appId/*");
        
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
        final App deserApp = BridgeObjectMapper.get().readValue(node.toString(), App.class);
        assertEquals(deserApp, study);
    }
    
    @Test
    public void testThatEmptyMinSupportedVersionMapperDoesNotThrowException() throws Exception {
        final DynamoApp study = TestUtils.getValidApp(DynamoAppTest.class);
        study.setVersion(2L);

        final String json = BridgeObjectMapper.get().writeValueAsString(study);
        BridgeObjectMapper.get().readTree(json);

        // Deserialize back to a POJO and verify.
        final App deserApp = BridgeObjectMapper.get().readValue(json, App.class);
        assertEquals(deserApp, study);
    }
    
    void assertEqualsAndNotNull(Object expected, Object actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(actual, expected);
    }
    
}

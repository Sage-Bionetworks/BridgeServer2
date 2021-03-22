package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.models.apps.AndroidAppLink;
import org.sagebionetworks.bridge.models.apps.AppleAppLink;
import org.sagebionetworks.bridge.models.apps.OAuthProvider;
import org.sagebionetworks.bridge.models.apps.OAuthProviderTest;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class AppValidatorTest {

    private static final AppValidator INSTANCE = AppValidator.INSTANCE;
    private static final String CALLBACK_URL = OAuthProviderTest.CALLBACK_URL;
    private static final String APP_ID = "appID";
    private static final String PATHS = "paths";
    private static final String NAMESPACE = "namespace";
    private static final String PACKAGE_NAME = "package_name";
    private static final String FINGERPRINTS = "sha256_cert_fingerprints";

    private DynamoApp app;
    
    @BeforeMethod
    public void createValidApp() {
        app = TestUtils.getValidApp(AppValidatorTest.class);
    }
    
    @Test
    public void acceptsValidApp() {
        AndroidAppLink androidAppLink = new AndroidAppLink("org.sagebionetworks.bridge", "APP", Lists.newArrayList(
                "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"));
        List<AndroidAppLink> androidAppLinks = Lists.newArrayList(androidAppLink);
        app.setAndroidAppLinks(androidAppLinks);
        
        AppleAppLink appleAppLink = new AppleAppLink("org.sagebionetworks.bridge.APP",
                Lists.newArrayList("/" + app.getIdentifier() + "/*"));
        List<AppleAppLink> appleAppLinks = Lists.newArrayList(appleAppLink);
        app.setAppleAppLinks(appleAppLinks);
        
        Validate.entityThrowingException(INSTANCE, app);
    }
    
    // While 2 is not a good length, we must allow it for legacy reasons.
    @Test
    public void minLengthCannotBeLessThan2() {
        app.setPasswordPolicy(new PasswordPolicy(1, false, false, false, false));
        assertValidatorMessage(INSTANCE, app, "passwordPolicy.minLength", "must be 2-999 characters");
    }

    @Test
    public void reauthEnabledNull() {
        app.setReauthenticationEnabled(null);
        assertValidatorMessage(INSTANCE, app, "reauthenticationEnabled", "is required");
    }

    @Test
    public void shortNameTooLong() {
        app.setShortName("ThisNameIsOverTenCharactersLong");
        assertValidatorMessage(INSTANCE, app, "shortName", "must be 10 characters or less");
    }
    
    @Test
    public void shortNameHasSpace() {
        app.setShortName("CRC Corps");
        assertValidatorMessage(INSTANCE, app, "shortName", "cannot contain spaces");
    }
    
    @Test
    public void sponsorNameRequired() {
        app.setSponsorName("");
        assertValidatorMessage(INSTANCE, app, "sponsorName", "is required");
    }
    
    @Test
    public void minLengthCannotBeMoreThan999() {
        app.setPasswordPolicy(new PasswordPolicy(1000, false, false, false, false));
        assertValidatorMessage(INSTANCE, app, "passwordPolicy.minLength", "must be 2-999 characters");
    }

    @Test
    public void cannotCreateIdentifierWithUppercase() {
        app.setIdentifier("Test");
        assertValidatorMessage(INSTANCE, app, "identifier", BridgeConstants.BRIDGE_IDENTIFIER_ERROR);
    }

    @Test
    public void cannotCreateInvalidIdentifierWithSpaces() {
        app.setIdentifier("test test");
        assertValidatorMessage(INSTANCE, app, "identifier", BridgeConstants.BRIDGE_IDENTIFIER_ERROR);
    }

    @Test
    public void identifierCanContainDashes() {
        app.setIdentifier("sage-pd");
        Validate.entityThrowingException(INSTANCE, app);
    }

    @Test
    public void acceptsEventKeysWithColons() {
        app.setActivityEventKeys(Sets.newHashSet("a-1", "b2"));
        Validate.entityThrowingException(INSTANCE, app);
    }

    @Test
    public void rejectEventKeysWithColons() {
        app.getCustomEvents().put("a-1", FUTURE_ONLY);
        app.getCustomEvents().put("b:2", FUTURE_ONLY);
        
        assertValidatorMessage(INSTANCE, app, "customEvents", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
    }

    @Test
    public void cannotCreateIdentifierWithColons() {
        app.getCustomEvents().put("a-1", FUTURE_ONLY);
        app.getCustomEvents().put("b:2", FUTURE_ONLY);
        
        assertValidatorMessage(INSTANCE, app, "customEvents", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
    }

    @Test
    public void acceptsMultipleValidSupportEmailAddresses() {
        app.setSupportEmail("test@test.com,test2@test.com");
        Validate.entityThrowingException(INSTANCE, app);
    }
    
    @Test
    public void rejectsInvalidSupportEmailAddresses() {
        app.setSupportEmail("test@test.com,asdf,test2@test.com");
        assertValidatorMessage(INSTANCE, app, "supportEmail", AppValidator.EMAIL_ERROR);
    }
    
    @Test
    public void requiresMissingSupportEmail() {
        app.setSupportEmail(null);
        assertValidatorMessage(INSTANCE, app, "supportEmail", "is required");
    }
    
    @Test
    public void acceptsMultipleValidTechnicalEmailAddresses() {
        app.setTechnicalEmail("test@test.com,test2@test.com");
        Validate.entityThrowingException(INSTANCE, app);
    }
    
    @Test
    public void rejectsInvalidTechnicalEmailAddresses() {
        app.setTechnicalEmail("test@test.com,asdf,test2@test.com");
        assertValidatorMessage(INSTANCE, app, "technicalEmail", AppValidator.EMAIL_ERROR);
    }
    
    @Test
    public void technicalEmailIsOptional() {
        app.setTechnicalEmail(null);
        Validate.entityThrowingException(INSTANCE, app);        
    }
    
    @Test
    public void supportEmailMustsBeValid() {
        app.setSupportEmail("email@email.com,email2@email.com,b");
        assertValidatorMessage(INSTANCE, app, "supportEmail", AppValidator.EMAIL_ERROR);
        
        app.setSupportEmail("email@email.com,email2@email.com");
        Validate.entityThrowingException(INSTANCE, app);
        
        // it is also required
        app.setSupportEmail("");
        assertValidatorMessage(INSTANCE, app, "supportEmail", "is required");
        
        app.setSupportEmail(null);
        assertValidatorMessage(INSTANCE, app, "supportEmail", "is required");
        
        app.setSupportEmail("alx@keywise.tech");
        Validate.entityThrowingException(INSTANCE, app);
    }
    
    @Test
    public void technicalEmailsMustBeValid() {
        app.setTechnicalEmail("");
        assertValidatorMessage(INSTANCE, app, "technicalEmail", AppValidator.EMAIL_ERROR);

        app.setTechnicalEmail("email@email.com,email2@email.com,b");
        assertValidatorMessage(INSTANCE, app, "technicalEmail", AppValidator.EMAIL_ERROR);
        
        app.setTechnicalEmail("email@email.com,email2@email.com");
        Validate.entityThrowingException(INSTANCE, app);
        
        // however they are not required
        app.setTechnicalEmail(null);
        Validate.entityThrowingException(INSTANCE, app);
    }

    @Test
    public void consentEmailsMustBeValid() {
        app.setConsentNotificationEmail("");
        assertValidatorMessage(INSTANCE, app, "consentNotificationEmail", AppValidator.EMAIL_ERROR);

        app.setConsentNotificationEmail("email@email.com,email2@email.com,b");
        assertValidatorMessage(INSTANCE, app, "consentNotificationEmail", AppValidator.EMAIL_ERROR);
        
        app.setConsentNotificationEmail("email@email.com,email2@email.com");
        Validate.entityThrowingException(INSTANCE, app);
        
        // however they are not required
        app.setConsentNotificationEmail(null);
        Validate.entityThrowingException(INSTANCE, app);
    }
    
    @Test
    public void validFieldDefList() {
        app.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-field").withType(UploadFieldType.INT).build()));
        Validate.entityThrowingException(INSTANCE, app);
    }

    @Test
    public void invalidFieldDef() {
        // This is tested in-depth in UploadFieldDefinitionListValidatorTest. Just test that we catch a non-trivial
        // error here.
        app.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder().withName(null)
                .withType(UploadFieldType.INT).build()));
        assertValidatorMessage(INSTANCE, app, "uploadMetadataFieldDefinitions[0].name", "is required");
    }

    @Test
    public void metadataFieldsTooManyBytes() {
        // A single LargeTextAttachment is enough to exceed the bytes limit.
        app.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder().withName("field")
                .withType(UploadFieldType.LARGE_TEXT_ATTACHMENT).build()));
        assertValidatorMessage(INSTANCE, app, "uploadMetadataFieldDefinitions",
                "cannot be greater than 2500 bytes combined");
    }

    @Test
    public void metadataFieldsTooManyColumns() {
        // A Multi-Choice field with 21 options should be enough to exceed the field limit.
        List<String> answerList = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            answerList.add("answer-" + i);
        }
        app.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder().withName("field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList(answerList).build()));
        assertValidatorMessage(INSTANCE, app, "uploadMetadataFieldDefinitions",
                "cannot be greater than 20 columns combined");
    }

    @Test
    public void rejectsInvalidConsentEmailAddresses() {
        app.setConsentNotificationEmail("test@test.com,asdf,test2@test.com");
        assertValidatorMessage(INSTANCE, app, "consentNotificationEmail", AppValidator.EMAIL_ERROR);
    }
    
    @Test
    public void cannotAddConflictingEmailAttribute() {
        app.getUserProfileAttributes().add("email");
        assertValidatorMessage(INSTANCE, app, "userProfileAttributes", "'email' conflicts with existing user profile property");
    }
    
    @Test
    public void cannotAddConflictingExternalIdAttribute() {
        app.getUserProfileAttributes().add("externalId");
        assertValidatorMessage(INSTANCE, app, "userProfileAttributes", "'externalId' conflicts with existing user profile property");
    }
    
    @Test
    public void userProfileAttributesCannotStartWithDash() {
        app.getUserProfileAttributes().add("-illegal");
        assertValidatorMessage(INSTANCE, app, "userProfileAttributes", "'-illegal' must contain only digits, letters, underscores and dashes, and cannot start with a dash");
    }
    
    @Test
    public void userProfileAttributesCannotContainSpaces() {
        app.getUserProfileAttributes().add("Game Points");
        assertValidatorMessage(INSTANCE, app, "userProfileAttributes", "'Game Points' must contain only digits, letters, underscores and dashes, and cannot start with a dash");
    }
    
    @Test
    public void userProfileAttributesCanBeJustADash() {
        app.getUserProfileAttributes().add("_");
        Validate.entityThrowingException(INSTANCE, app);
    }
    
    @Test
    public void userProfileAttributesCanBeJustADashAndLetter() {
        app.getUserProfileAttributes().add("_A");
        Validate.entityThrowingException(INSTANCE, app);
    }
    
    @Test
    public void userProfileAttributesCannotBeEmpty() {
        app.getUserProfileAttributes().add("");
        assertValidatorMessage(INSTANCE, app, "userProfileAttributes", "'' must contain only digits, letters, underscores and dashes, and cannot start with a dash");
    }
    
    @Test
    public void missingConsentNotificationEmailOK() {
        app.setConsentNotificationEmail(null);
        Validate.entityThrowingException(INSTANCE, app);
    }    
    
    @Test
    public void requiresPasswordPolicy() {
        app.setPasswordPolicy(null);
        assertValidatorMessage(INSTANCE, app, "passwordPolicy", "is required");
    }
    
    @Test
    public void cannotSetMinAgeOfConsentLessThanZero() {
        app.setMinAgeOfConsent(-100);
        assertValidatorMessage(INSTANCE, app, "minAgeOfConsent", "must be zero (no minimum age of consent) or higher");
    }
    
    @Test
    public void cannotSetAccountLimitLessThanZero() {
        app.setAccountLimit(-100);
        assertValidatorMessage(INSTANCE, app, "accountLimit", "must be zero (no limit set) or higher");
    }
    
    @Test
    public void shortListOfDataGroupsOK() {
        app.setDataGroups(Sets.newHashSet("beta_users", "production_users", "testers", "internal"));
        Validate.entityThrowingException(INSTANCE, app);
    }
    
    @Test
    public void longListOfDataGroupsInvalid() {
        // Make 25 data groups, each with at least 10 chars in length. This will be long enough to hit the limit.
        Set<String> dataGroupSet = new TreeSet<>();
        for (int i = 0; i < 25; i++) {
            dataGroupSet.add("data-group-" + i);
        }
        app.setDataGroups(dataGroupSet);
        assertValidatorMessage(INSTANCE, app, "dataGroups", "will not export to Synapse (string is over 250 characters: '" +
                BridgeUtils.COMMA_SPACE_JOINER.join(dataGroupSet) + "')");
    }
    
    @Test
    public void dataGroupCharactersRestricted() {
        app.setDataGroups(Sets.newHashSet("Liège"));
        assertValidatorMessage(INSTANCE, app, "dataGroups", "contains invalid tag 'Liège' (only letters, numbers, underscore and dash allowed)");
    }

    @Test
    public void publicAppWithoutExternalIdOnSignUpIsValid() {
        app.setExternalIdRequiredOnSignup(false);
        Validate.entityThrowingException(INSTANCE, app);
    }

    @Test
    public void nonPublicAppsMustRequireExternalIdOnSignUp() {
        app.setEmailVerificationEnabled(false);
        app.setExternalIdRequiredOnSignup(false);
        assertValidatorMessage(INSTANCE, app, "externalIdRequiredOnSignup",
                "cannot be disabled if email verification has been disabled");
    } 

    @Test
    public void oauthProviderRequiresClientId() {
        OAuthProvider provider = new OAuthProvider(null, "secret", "endpoint", CALLBACK_URL,
                null);
        app.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, app, "oauthProviders[vendor].clientId", "is required");
    }

    @Test
    public void oauthProviderRequiresSecret() {
        OAuthProvider provider = new OAuthProvider("clientId", null, "endpoint", CALLBACK_URL,
                null);
        app.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, app, "oauthProviders[vendor].secret", "is required");
    }
    
    @Test
    public void oauthProviderRequiresEndpoint() {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", null, CALLBACK_URL,
                null);
        app.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, app, "oauthProviders[vendor].endpoint", "is required");
    }
    
    @Test
    public void oauthProviderRequiresCallbackUrl() {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint",
                null, null);
        app.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, app, "oauthProviders[vendor].callbackUrl", "is required");
    }

    @Test
    public void oauthProviderWithOptionalIntrospectEndpoint() {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint",
                CALLBACK_URL, "http://example.com/introspect");
        app.getOAuthProviders().put("vendor", provider);
        Validate.entityThrowingException(INSTANCE, app);
    }

    @Test
    public void oauthProviderRequired() {
        app.getOAuthProviders().put("vendor", null);
        assertValidatorMessage(INSTANCE, app, "oauthProviders[vendor]", "is required");
    }
    
    @Test
    public void oauthProviderCannotBeCalledSynapse() {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint",
                CALLBACK_URL, "http://example.com/introspect");
        app.getOAuthProviders().put("synapse", provider);
        assertValidatorMessage(INSTANCE, app, "oauthProviders[synapse]", "is a reserved vendor ID");
    }
    
    @Test
    public void appleAppLinkAppIdCannotBeNull() {
        app.getAppleAppLinks().add(null);
        assertValidatorMessage(INSTANCE, app, "appleAppLinks[0]","cannot be null");
    }
    
    @Test
    public void appleAppLinkAppIdCannotBeEmpty() {
        app.getAppleAppLinks().add(new AppleAppLink(null, Lists.newArrayList("*")));
        assertValidatorMessage(INSTANCE, app, "appleAppLinks[0]."+APP_ID, "cannot be blank or null");
    }
    
    @Test
    public void appleAppLinkAppIdCannotBeDuplicated() {
        app.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*")));
        app.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*")));
        assertValidatorMessage(INSTANCE, app, "appleAppLinks","cannot contain duplicate entries");
    }
    
    @Test
    public void appleAppLinkPathsCannotBeNull() {
        app.getAppleAppLinks().add(new AppleAppLink("A", null));
        assertValidatorMessage(INSTANCE, app, "appleAppLinks[0]."+PATHS,"cannot be null or empty");
    }
    
    @Test
    public void appleAppLinkPathsCannotBeEmpty() {
        app.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList()));
        assertValidatorMessage(INSTANCE, app, "appleAppLinks[0]."+PATHS,"cannot be null or empty");
    }
    
    @Test
    public void appleAppLinkPathCannotBeNull() {
        app.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*", null)));
        assertValidatorMessage(INSTANCE, app, "appleAppLinks[0]."+PATHS+"[1]","cannot be blank or empty");
    }
    
    @Test
    public void appleAppLinkPathCannotBeEmpty() {
        app.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*", "")));
        assertValidatorMessage(INSTANCE, app, "appleAppLinks[0]."+PATHS+"[1]","cannot be blank or empty");
    }

    @Test
    public void androidAppLinkNamespaceCannotBeNull() {
        app.getAndroidAppLinks().add(new AndroidAppLink(null, "packageName", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, app, "androidAppLinks[0]."+NAMESPACE,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkNamespaceCannotBeEmpty() {
        app.getAndroidAppLinks().add(new AndroidAppLink("", "packageName", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, app, "androidAppLinks[0]."+NAMESPACE,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkPackageNameCannotBeNull() {
        app.getAndroidAppLinks().add(new AndroidAppLink("appId", null, Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, app, "androidAppLinks[0]."+PACKAGE_NAME,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkPackageNameCannotBeEmpty() {
        app.getAndroidAppLinks().add(new AndroidAppLink("appId", "", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, app, "androidAppLinks[0]."+PACKAGE_NAME,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkIdentifiersCannotBeDuplicated() {
        app.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("fingerprint")));
        app.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, app, "androidAppLinks","cannot contain duplicate entries");
    }
    
    @Test
    public void androidAppLinkFingerprintsCannotBeNull() {
        app.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", null));
        assertValidatorMessage(INSTANCE, app, "androidAppLinks[0]."+FINGERPRINTS,"cannot be null or empty");
    }
    
    @Test
    public void androidAppLinkFingerprintsCannotBeEmpty() {
        app.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList()));
        assertValidatorMessage(INSTANCE, app, "androidAppLinks[0]."+FINGERPRINTS,"cannot be null or empty");
    }
    
    @Test
    public void androidAppLinkFingerprintCannotBeNull() {
        app.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList((String)null)));
        assertValidatorMessage(INSTANCE, app, "androidAppLinks[0]."+FINGERPRINTS+"[0]","cannot be null or empty");
    }

    @Test
    public void androidAppLinkFingerprintCannotBeEmpty() {
        app.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("  ")));
        assertValidatorMessage(INSTANCE, app, "androidAppLinks[0]."+FINGERPRINTS+"[0]","cannot be null or empty");
    }
    
    @Test
    public void androidAppLinkFingerprintCannotBeInvalid() {
        app.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("asdf")));
        assertValidatorMessage(INSTANCE, app, "androidAppLinks[0]."+FINGERPRINTS+"[0]","is not a SHA 256 fingerprint");
    }
    
    @Test
    public void installAppLinksCannotBeNull() {
        app.getInstallLinks().put("foo", "");
        assertValidatorMessage(INSTANCE, app, "installLinks", "cannot be blank");
    }
    
    @Test
    public void installAppLinksCannotExceedSMSLength() {
        String msg = "";
        for (int i = 0; i < BridgeConstants.APP_LINK_MAX_LENGTH; i++) {
            msg += "A";
        }
        msg += "A";
        app.getInstallLinks().put("foo", msg);
        assertValidatorMessage(INSTANCE, app, "installLinks", "cannot be longer than " +
                BridgeConstants.APP_LINK_MAX_LENGTH + " characters");
    }
    
    @Test
    public void validAutomaticCustomEventWithCustomOriginEvent() {
        app.getCustomEvents().put("externalEvent", FUTURE_ONLY);
        app.getAutomaticCustomEvents().put("myEvent", "externalEvent:P-14D");
        Validate.entityThrowingException(INSTANCE, app);
    }

    // This was the original form of this configuration, it will not throw an error until we 
    // clarify the event we want to trigger off of
    @Test
    public void validAutomaticCustomEventWithNoEventSpecified() {
        app.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "P-14D"));
        assertValidatorMessage(INSTANCE, app, "automaticCustomEvents[myEvent]", "'null' is not a valid custom or system event ID");
    }
    
    @Test
    public void validAutomaticCustomEventForEnrollment() {
        app.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "enrollment:P-14D"));
        Validate.entityThrowingException(INSTANCE, app);
    }
    
    @Test
    public void validAutomaticCustomEventForActivitiesRetrieved() {
        app.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "activities_retrieved:P-14D"));
        Validate.entityThrowingException(INSTANCE, app);
    }

    @Test
    public void invalidAutomaticCustomEventKey() {
        app.setAutomaticCustomEvents(ImmutableMap.of("@not-valid", "activities_retrieved:P-14D"));
        assertValidatorMessage(INSTANCE, app, "automaticCustomEvents[@not-valid]", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
    }
    
    @Test
    public void invalidAutomaticCustomEventPeriod() {
        app.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "activities_retrieved:Pweeks"));
        assertValidatorMessage(INSTANCE, app, "automaticCustomEvents[myEvent]", "'Pweeks' is not a valid ISO 8601 period");
    }
    
    @Test
    public void invalidAutomaticCustomEventOriginEvent() {
        app.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "does-not-exist-event:P2W"));
        assertValidatorMessage(INSTANCE, app, "automaticCustomEvents[myEvent]", "'does-not-exist-event' is not a valid custom or system event ID");
    }
}

package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.models.studies.OAuthProviderTest;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class StudyValidatorTest {

    private static final StudyValidator INSTANCE = StudyValidator.INSTANCE;
    private static final String CALLBACK_URL = OAuthProviderTest.CALLBACK_URL;
    private static final String APP_ID = "appID";
    private static final String PATHS = "paths";
    private static final String NAMESPACE = "namespace";
    private static final String PACKAGE_NAME = "package_name";
    private static final String FINGERPRINTS = "sha256_cert_fingerprints";

    private DynamoStudy study;
    
    @BeforeMethod
    public void createValidStudy() {
        study = TestUtils.getValidStudy(StudyValidatorTest.class);
    }
    
    @Test
    public void acceptsValidStudy() {
        AndroidAppLink androidAppLink = new AndroidAppLink("org.sagebionetworks.bridge", "APP", Lists.newArrayList(
                "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"));
        List<AndroidAppLink> androidAppLinks = Lists.newArrayList(androidAppLink);
        study.setAndroidAppLinks(androidAppLinks);
        
        AppleAppLink appleAppLink = new AppleAppLink("org.sagebionetworks.bridge.APP",
                Lists.newArrayList("/" + study.getIdentifier() + "/*"));
        List<AppleAppLink> appleAppLinks = Lists.newArrayList(appleAppLink);
        study.setAppleAppLinks(appleAppLinks);
        
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    // While 2 is not a good length, we must allow it for legacy reasons.
    @Test
    public void minLengthCannotBeLessThan2() {
        study.setPasswordPolicy(new PasswordPolicy(1, false, false, false, false));
        assertValidatorMessage(INSTANCE, study, "passwordPolicy.minLength", "must be 2-999 characters");
    }

    @Test
    public void reauthEnabledNull() {
        study.setReauthenticationEnabled(null);
        assertValidatorMessage(INSTANCE, study, "reauthenticationEnabled", "is required");
    }

    @Test
    public void shortNameTooLong() {
        study.setShortName("ThisNameIsOverTenCharactersLong");
        assertValidatorMessage(INSTANCE, study, "shortName", "must be 10 characters or less");
    }
    
    @Test
    public void sponsorNameRequired() {
        study.setSponsorName("");
        assertValidatorMessage(INSTANCE, study, "sponsorName", "is required");
    }
    
    @Test
    public void minLengthCannotBeMoreThan999() {
        study.setPasswordPolicy(new PasswordPolicy(1000, false, false, false, false));
        assertValidatorMessage(INSTANCE, study, "passwordPolicy.minLength", "must be 2-999 characters");
    }

    @Test
    public void cannotCreateIdentifierWithUppercase() {
        study.setIdentifier("Test");
        assertValidatorMessage(INSTANCE, study, "identifier", BridgeConstants.BRIDGE_IDENTIFIER_ERROR);
    }

    @Test
    public void cannotCreateInvalidIdentifierWithSpaces() {
        study.setIdentifier("test test");
        assertValidatorMessage(INSTANCE, study, "identifier", BridgeConstants.BRIDGE_IDENTIFIER_ERROR);
    }

    @Test
    public void identifierCanContainDashes() {
        study.setIdentifier("sage-pd");
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void acceptsEventKeysWithColons() {
        study.setActivityEventKeys(Sets.newHashSet("a-1", "b2"));
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void rejectEventKeysWithColons() {
        study.setActivityEventKeys(Sets.newHashSet("a-1", "b:2"));
        assertValidatorMessage(INSTANCE, study, "activityEventKeys", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
    }

    @Test
    public void cannotCreateIdentifierWithColons() {
        study.setActivityEventKeys(Sets.newHashSet("a-1", "b:2"));
        assertValidatorMessage(INSTANCE, study, "activityEventKeys", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
    }

    @Test
    public void acceptsMultipleValidSupportEmailAddresses() {
        study.setSupportEmail("test@test.com,test2@test.com");
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void rejectsInvalidSupportEmailAddresses() {
        study.setSupportEmail("test@test.com,asdf,test2@test.com");
        assertValidatorMessage(INSTANCE, study, "supportEmail", StudyValidator.EMAIL_ERROR);
    }
    
    @Test
    public void requiresMissingSupportEmail() {
        study.setSupportEmail(null);
        assertValidatorMessage(INSTANCE, study, "supportEmail", "is required");
    }
    
    @Test
    public void acceptsMultipleValidTechnicalEmailAddresses() {
        study.setTechnicalEmail("test@test.com,test2@test.com");
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void rejectsInvalidTechnicalEmailAddresses() {
        study.setTechnicalEmail("test@test.com,asdf,test2@test.com");
        assertValidatorMessage(INSTANCE, study, "technicalEmail", StudyValidator.EMAIL_ERROR);
    }
    
    @Test
    public void technicalEmailIsOptional() {
        study.setTechnicalEmail(null);
        Validate.entityThrowingException(INSTANCE, study);        
    }
    
    @Test
    public void supportEmailMustsBeValid() {
        study.setSupportEmail("email@email.com,email2@email.com,b");
        assertValidatorMessage(INSTANCE, study, "supportEmail", StudyValidator.EMAIL_ERROR);
        
        study.setSupportEmail("email@email.com,email2@email.com");
        Validate.entityThrowingException(INSTANCE, study);
        
        // it is also required
        study.setSupportEmail("");
        assertValidatorMessage(INSTANCE, study, "supportEmail", "is required");
        
        study.setSupportEmail(null);
        assertValidatorMessage(INSTANCE, study, "supportEmail", "is required");
    }
    
    @Test
    public void technicalEmailsMustBeValid() {
        study.setTechnicalEmail("");
        assertValidatorMessage(INSTANCE, study, "technicalEmail", StudyValidator.EMAIL_ERROR);

        study.setTechnicalEmail("email@email.com,email2@email.com,b");
        assertValidatorMessage(INSTANCE, study, "technicalEmail", StudyValidator.EMAIL_ERROR);
        
        study.setTechnicalEmail("email@email.com,email2@email.com");
        Validate.entityThrowingException(INSTANCE, study);
        
        // however they are not required
        study.setTechnicalEmail(null);
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void consentEmailsMustBeValid() {
        study.setConsentNotificationEmail("");
        assertValidatorMessage(INSTANCE, study, "consentNotificationEmail", StudyValidator.EMAIL_ERROR);

        study.setConsentNotificationEmail("email@email.com,email2@email.com,b");
        assertValidatorMessage(INSTANCE, study, "consentNotificationEmail", StudyValidator.EMAIL_ERROR);
        
        study.setConsentNotificationEmail("email@email.com,email2@email.com");
        Validate.entityThrowingException(INSTANCE, study);
        
        // however they are not required
        study.setConsentNotificationEmail(null);
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void validFieldDefList() {
        study.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-field").withType(UploadFieldType.INT).build()));
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void invalidFieldDef() {
        // This is tested in-depth in UploadFieldDefinitionListValidatorTest. Just test that we catch a non-trivial
        // error here.
        study.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder().withName(null)
                .withType(UploadFieldType.INT).build()));
        assertValidatorMessage(INSTANCE, study, "uploadMetadataFieldDefinitions[0].name", "is required");
    }

    @Test
    public void metadataFieldsTooManyBytes() {
        // A single LargeTextAttachment is enough to exceed the bytes limit.
        study.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder().withName("field")
                .withType(UploadFieldType.LARGE_TEXT_ATTACHMENT).build()));
        assertValidatorMessage(INSTANCE, study, "uploadMetadataFieldDefinitions",
                "cannot be greater than 2500 bytes combined");
    }

    @Test
    public void metadataFieldsTooManyColumns() {
        // A Multi-Choice field with 21 options should be enough to exceed the field limit.
        List<String> answerList = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            answerList.add("answer-" + i);
        }
        study.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder().withName("field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList(answerList).build()));
        assertValidatorMessage(INSTANCE, study, "uploadMetadataFieldDefinitions",
                "cannot be greater than 20 columns combined");
    }

    @Test
    public void rejectsInvalidConsentEmailAddresses() {
        study.setConsentNotificationEmail("test@test.com,asdf,test2@test.com");
        assertValidatorMessage(INSTANCE, study, "consentNotificationEmail", StudyValidator.EMAIL_ERROR);
    }
    
    @Test
    public void cannotAddConflictingEmailAttribute() {
        study.getUserProfileAttributes().add("email");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'email' conflicts with existing user profile property");
    }
    
    @Test
    public void cannotAddConflictingExternalIdAttribute() {
        study.getUserProfileAttributes().add("externalId");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'externalId' conflicts with existing user profile property");
    }
    
    @Test
    public void userProfileAttributesCannotStartWithDash() {
        study.getUserProfileAttributes().add("-illegal");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'-illegal' must contain only digits, letters, underscores and dashes, and cannot start with a dash");
    }
    
    @Test
    public void userProfileAttributesCannotContainSpaces() {
        study.getUserProfileAttributes().add("Game Points");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'Game Points' must contain only digits, letters, underscores and dashes, and cannot start with a dash");
    }
    
    @Test
    public void userProfileAttributesCanBeJustADash() {
        study.getUserProfileAttributes().add("_");
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void userProfileAttributesCanBeJustADashAndLetter() {
        study.getUserProfileAttributes().add("_A");
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void userProfileAttributesCannotBeEmpty() {
        study.getUserProfileAttributes().add("");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'' must contain only digits, letters, underscores and dashes, and cannot start with a dash");
    }
    
    @Test
    public void missingConsentNotificationEmailOK() {
        study.setConsentNotificationEmail(null);
        Validate.entityThrowingException(INSTANCE, study);
    }    
    
    @Test
    public void requiresPasswordPolicy() {
        study.setPasswordPolicy(null);
        assertValidatorMessage(INSTANCE, study, "passwordPolicy", "is required");
    }
    
    @Test
    public void cannotSetMinAgeOfConsentLessThanZero() {
        study.setMinAgeOfConsent(-100);
        assertValidatorMessage(INSTANCE, study, "minAgeOfConsent", "must be zero (no minimum age of consent) or higher");
    }
    
    @Test
    public void cannotSetAccountLimitLessThanZero() {
        study.setAccountLimit(-100);
        assertValidatorMessage(INSTANCE, study, "accountLimit", "must be zero (no limit set) or higher");
    }
    
    @Test
    public void shortListOfDataGroupsOK() {
        study.setDataGroups(Sets.newHashSet("beta_users", "production_users", "testers", "internal"));
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void longListOfDataGroupsInvalid() {
        // Make 25 data groups, each with at least 10 chars in length. This will be long enough to hit the limit.
        Set<String> dataGroupSet = new TreeSet<>();
        for (int i = 0; i < 25; i++) {
            dataGroupSet.add("data-group-" + i);
        }
        study.setDataGroups(dataGroupSet);
        assertValidatorMessage(INSTANCE, study, "dataGroups", "will not export to Synapse (string is over 250 characters: '" +
                BridgeUtils.COMMA_SPACE_JOINER.join(dataGroupSet) + "')");
    }
    
    @Test
    public void dataGroupCharactersRestricted() {
        study.setDataGroups(Sets.newHashSet("Liège"));
        assertValidatorMessage(INSTANCE, study, "dataGroups", "contains invalid tag 'Liège' (only letters, numbers, underscore and dash allowed)");
    }

    @Test
    public void publicStudyWithoutExternalIdOnSignUpIsValid() {
        study.setExternalIdRequiredOnSignup(false);
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void nonPublicStudiesMustRequireExternalIdOnSignUp() {
        study.setEmailVerificationEnabled(false);
        study.setExternalIdRequiredOnSignup(false);
        assertValidatorMessage(INSTANCE, study, "externalIdRequiredOnSignup",
                "cannot be disabled if email verification has been disabled");
    } 

    @Test
    public void oauthProviderRequiresClientId() {
        OAuthProvider provider = new OAuthProvider(null, "secret", "endpoint", CALLBACK_URL,
                null);
        study.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, study, "oauthProviders[vendor].clientId", "is required");
    }

    @Test
    public void oauthProviderRequiresSecret() {
        OAuthProvider provider = new OAuthProvider("clientId", null, "endpoint", CALLBACK_URL,
                null);
        study.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, study, "oauthProviders[vendor].secret", "is required");
    }
    
    @Test
    public void oauthProviderRequiresEndpoint() {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", null, CALLBACK_URL,
                null);
        study.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, study, "oauthProviders[vendor].endpoint", "is required");
    }
    
    @Test
    public void oauthProviderRequiresCallbackUrl() {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint",
                null, null);
        study.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, study, "oauthProviders[vendor].callbackUrl", "is required");
    }

    @Test
    public void oauthProviderWithOptionalIntrospectEndpoint() {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint",
                CALLBACK_URL, "http://example.com/introspect");
        study.getOAuthProviders().put("vendor", provider);
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void oauthProviderRequired() {
        study.getOAuthProviders().put("vendor", null);
        assertValidatorMessage(INSTANCE, study, "oauthProviders[vendor]", "is required");
    }
    
    @Test
    public void appleAppLinkAppIdCannotBeNull() {
        study.getAppleAppLinks().add(null);
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]","cannot be null");
    }
    
    @Test
    public void appleAppLinkAppIdCannotBeEmpty() {
        study.getAppleAppLinks().add(new AppleAppLink(null, Lists.newArrayList("*")));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]."+APP_ID,"cannot be blank or null");
    }
    
    @Test
    public void appleAppLinkAppIdCannotBeDuplicated() {
        study.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*")));
        study.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*")));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks","cannot contain duplicate entries");
    }
    
    @Test
    public void appleAppLinkPathsCannotBeNull() {
        study.getAppleAppLinks().add(new AppleAppLink("A", null));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]."+PATHS,"cannot be null or empty");
    }
    
    @Test
    public void appleAppLinkPathsCannotBeEmpty() {
        study.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList()));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]."+PATHS,"cannot be null or empty");
    }
    
    @Test
    public void appleAppLinkPathCannotBeNull() {
        study.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*", null)));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]."+PATHS+"[1]","cannot be blank or empty");
    }
    
    @Test
    public void appleAppLinkPathCannotBeEmpty() {
        study.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*", "")));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]."+PATHS+"[1]","cannot be blank or empty");
    }

    @Test
    public void androidAppLinkNamespaceCannotBeNull() {
        study.getAndroidAppLinks().add(new AndroidAppLink(null, "packageName", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+NAMESPACE,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkNamespaceCannotBeEmpty() {
        study.getAndroidAppLinks().add(new AndroidAppLink("", "packageName", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+NAMESPACE,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkPackageNameCannotBeNull() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", null, Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+PACKAGE_NAME,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkPackageNameCannotBeEmpty() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+PACKAGE_NAME,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkIdentifiersCannotBeDuplicated() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("fingerprint")));
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks","cannot contain duplicate entries");
    }
    
    @Test
    public void androidAppLinkFingerprintsCannotBeNull() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", null));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+FINGERPRINTS,"cannot be null or empty");
    }
    
    @Test
    public void androidAppLinkFingerprintsCannotBeEmpty() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList()));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+FINGERPRINTS,"cannot be null or empty");
    }
    
    @Test
    public void androidAppLinkFingerprintCannotBeNull() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList((String)null)));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+FINGERPRINTS+"[0]","cannot be null or empty");
    }

    @Test
    public void androidAppLinkFingerprintCannotBeEmpty() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("  ")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+FINGERPRINTS+"[0]","cannot be null or empty");
    }
    
    @Test
    public void androidAppLinkFingerprintCannotBeInvalid() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("asdf")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+FINGERPRINTS+"[0]","is not a SHA 256 fingerprint");
    }
    
    @Test
    public void installAppLinksCannotBeNull() {
        study.getInstallLinks().put("foo", "");
        assertValidatorMessage(INSTANCE, study, "installLinks", "cannot be blank");
    }
    
    @Test
    public void installAppLinksCannotExceedSMSLength() {
        String msg = "";
        for (int i = 0; i < BridgeConstants.APP_LINK_MAX_LENGTH; i++) {
            msg += "A";
        }
        msg += "A";
        study.getInstallLinks().put("foo", msg);
        assertValidatorMessage(INSTANCE, study, "installLinks", "cannot be longer than " +
                BridgeConstants.APP_LINK_MAX_LENGTH + " characters");
    }
    
    @Test
    public void validAutomaticCustomEventWithCustomOriginEvent() {
        study.setActivityEventKeys(ImmutableSet.of("externalEvent"));
        study.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "externalEvent:P-14D"));
        Validate.entityThrowingException(INSTANCE, study);
    }

    // This was the original form of this configuration, it will not throw an error until we 
    // clarify the event we want to trigger off of
    @Test
    public void validAutomaticCustomEventWithNoEventSpecified() {
        study.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "P-14D"));
        assertValidatorMessage(INSTANCE, study, "automaticCustomEvents[myEvent]", "'null' is not a valid custom or system event ID");
    }
    
    @Test
    public void validAutomaticCustomEventForEnrollment() {
        study.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "enrollment:P-14D"));
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void validAutomaticCustomEventForActivitiesRetrieved() {
        study.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "activities_retrieved:P-14D"));
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void invalidAutomaticCustomEventKey() {
        study.setAutomaticCustomEvents(ImmutableMap.of("@not-valid", "activities_retrieved:P-14D"));
        assertValidatorMessage(INSTANCE, study, "automaticCustomEvents[@not-valid]", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
    }
    
    @Test
    public void invalidAutomaticCustomEventPeriod() {
        study.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "activities_retrieved:Pweeks"));
        assertValidatorMessage(INSTANCE, study, "automaticCustomEvents[myEvent]", "'Pweeks' is not a valid ISO 8601 period");
    }
    
    @Test
    public void invalidAutomaticCustomEventOriginEvent() {
        study.setAutomaticCustomEvents(ImmutableMap.of("myEvent", "does-not-exist-event:P2W"));
        assertValidatorMessage(INSTANCE, study, "automaticCustomEvents[myEvent]", "'does-not-exist-event' is not a valid custom or system event ID");
    }
}

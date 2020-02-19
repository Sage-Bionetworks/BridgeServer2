package org.sagebionetworks.bridge;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.TagUtils;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class TestConstants {

    public static final NotificationMessage NOTIFICATION_MESSAGE = new NotificationMessage.Builder()
            .withSubject("a subject").withMessage("a message").build();
    public static final DateTime TIMESTAMP = DateTime.parse("2015-01-27T00:38:32.486Z");
    public static final String REQUEST_ID = "request-id";
    public static final String UA = "Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4";
    public static final String IP_ADDRESS = "2.3.4.5";
    public static final String USER_ID = "userId";
    public static final String SYNAPSE_USER_ID = "12345";
    public static final DateTimeZone TIMEZONE_MSK = DateTimeZone.forOffsetHours(3);

    public static final String HEALTH_CODE = "oneHealthCode";
    public static final String ENCRYPTED_HEALTH_CODE = "TFMkaVFKPD48WissX0bgcD3esBMEshxb3MVgKxHnkXLSEPN4FQMKc01tDbBAVcXx94kMX6ckXVYUZ8wx4iICl08uE+oQr9gorE1hlgAyLAM=";
    public static final String UNENCRYPTED_HEALTH_CODE = "5a2192ee-f55d-4d01-a385-2d19f15a0880";
    
    public static final String DUMMY_IMAGE_DATA = "VGhpcyBpc24ndCBhIHJlYWwgaW1hZ2Uu";

    public static final byte[] MOCK_MD5 = { -104, 10, -30, -37, 25, -113, 92, -9, 69, -118, -46, -87, 11, -14, 38, -61 };
    public static final String MOCK_MD5_HEX_ENCODED = "980ae2db198f5cf7458ad2a90bf226c3";

    public static final String TEST_STUDY_IDENTIFIER = "api";
    public static final StudyIdentifier TEST_STUDY = new StudyIdentifierImpl(TEST_STUDY_IDENTIFIER);
    
    public static final String SHARED_STUDY_IDENTIFIER = "shared";
    public static final StudyIdentifier SHARED_STUDY = new StudyIdentifierImpl(SHARED_STUDY_IDENTIFIER);
    
    public static final AccountId ACCOUNT_ID = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
    public static final CriteriaContext TEST_CONTEXT = new CriteriaContext.Builder()
            .withUserId("user-id").withStudyIdentifier(TestConstants.TEST_STUDY).build();

    public static final int TIMEOUT = 10000;
    public static final String TEST_BASE_URL = "http://localhost:3333";
    public static final String API_URL = "/v3";
    public static final String SIGN_OUT_URL = API_URL + "/auth/signOut";
    public static final String SIGN_IN_URL = API_URL + "/auth/signIn";
    public static final String SCHEDULES_API = API_URL + "/schedules";
    public static final String SCHEDULED_ACTIVITIES_API = API_URL + "/activities";
    public static final String STUDIES_URL = API_URL + "/studies/";

    public static final String APPLICATION_JSON = "application/json";
    public static final String EMAIL = "email@email.com";
    public static final String PASSWORD = "password";
    public static final String SESSION_TOKEN = "sessionToken";
    
    public static final String ATTACHMENT_BUCKET = BridgeConfigFactory.getConfig().getProperty("attachment.bucket");
    public static final String UPLOAD_BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket");
    
    public static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
    
    /**
     * During tests, must sometimes pause because the underlying query uses a DynamoDB global 
     * secondary index, and this does not currently support consistent reads.
     */
    public static final int GSI_WAIT_DURATION = 2000;

    public static final ConsentStatus REQUIRED_SIGNED_CURRENT = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo1")).withRequired(true).withConsented(true)
            .withSignedMostRecentConsent(true).build();
    public static final ConsentStatus REQUIRED_SIGNED_OBSOLETE = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo2")).withRequired(true).withConsented(true)
            .withSignedMostRecentConsent(false).build();
    public static final ConsentStatus OPTIONAL_SIGNED_CURRENT = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo3")).withRequired(false).withConsented(true)
            .withSignedMostRecentConsent(true).build();
    public static final ConsentStatus OPTIONAL_SIGNED_OBSOLETE = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo4")).withRequired(false).withConsented(true)
            .withSignedMostRecentConsent(false).build();
    public static final ConsentStatus REQUIRED_UNSIGNED = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo5")).withRequired(true).withConsented(false)
            .withSignedMostRecentConsent(false).build();
    public static final ConsentStatus OPTIONAL_UNSIGNED = new ConsentStatus.Builder().withName("Name1")
            .withGuid(SubpopulationGuid.create("foo6")).withRequired(false).withConsented(false)
            .withSignedMostRecentConsent(false).build();
    
    public static final Map<SubpopulationGuid, ConsentStatus> CONSENTED_STATUS_MAP = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>()
            .put(SubpopulationGuid.create(REQUIRED_SIGNED_CURRENT.getSubpopulationGuid()), REQUIRED_SIGNED_CURRENT)
            .build();
    public static final Map<SubpopulationGuid, ConsentStatus> UNCONSENTED_STATUS_MAP = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>()
            .put(SubpopulationGuid.create(REQUIRED_UNSIGNED.getSubpopulationGuid()), REQUIRED_UNSIGNED).build();
    
    public static final ConsentSignature SIGNATURE = new ConsentSignature.Builder().withName("Jack Aubrey")
            .withBirthdate("1970-10-10").withImageData("data:asdf").withImageMimeType("image/png")
            .withSignedOn(TIMESTAMP.getMillis()).build();
    
    public static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create(REQUIRED_UNSIGNED.getSubpopulationGuid());
    
    public static final String GUID = "oneGuid";

    public static final Set<String> USER_DATA_GROUPS = ImmutableSet.of("group1","group2");

    public static final Set<String> USER_SUBSTUDY_IDS = ImmutableSet.of("substudyA","substudyB");
    
    public static final List<String> LANGUAGES = ImmutableList.of("en","fr");
    
    public static final Phone PHONE = new Phone("9712486796", "US");
    
    public static final Withdrawal WITHDRAWAL = new Withdrawal("reasons");
    
    public static final AndroidAppLink ANDROID_APP_LINK = new AndroidAppLink("namespace", "package_name",
            Lists.newArrayList("sha256_cert_fingerprints"));
    public static final AndroidAppLink ANDROID_APP_LINK_2 = new AndroidAppLink("namespace2", "package_name2",
            Lists.newArrayList("sha256_cert_fingerprints2"));
    public static final AndroidAppLink ANDROID_APP_LINK_3 = new AndroidAppLink("namespace3", "package_name3",
            Lists.newArrayList("sha256_cert_fingerprints3"));
    public static final AndroidAppLink ANDROID_APP_LINK_4 = new AndroidAppLink("namespace4", "package_name4",
            Lists.newArrayList("sha256_cert_fingerprints4"));
    public static final AppleAppLink APPLE_APP_LINK = new AppleAppLink("studyId",
            Lists.newArrayList("/appId/", "/appId/*"));
    public static final AppleAppLink APPLE_APP_LINK_2 = new AppleAppLink("studyId2",
            Lists.newArrayList("/appId2/", "/appId2/*"));
    public static final AppleAppLink APPLE_APP_LINK_3 = new AppleAppLink("studyId3",
            Lists.newArrayList("/appId3/", "/appId3/*"));
    public static final AppleAppLink APPLE_APP_LINK_4 = new AppleAppLink("studyId4",
            Lists.newArrayList("/appId4/", "/appId4/*"));
    
    public static final Activity ACTIVITY_1 = new Activity.Builder().withGuid("activity1guid").withLabel("Activity1")
            .withPublishedSurvey("identifier1", "AAA").build();
    
    public static final Activity ACTIVITY_2 = new Activity.Builder().withGuid("activity2guid").withLabel("Activity2")
                .withPublishedSurvey("identifier2", "BBB").build();
    
    public static final Activity ACTIVITY_3 = new Activity.Builder().withLabel("Activity3").withGuid("AAA")
            .withTask("tapTest").build();

    public static final String OWNER_ID = "oneOwnerId";
    public static final String IDENTIFIER = "oneIdentifier";
    public static final Set<String> STRING_TAGS = ImmutableSet.of("tag1", "tag2");
    public static final Set<String> STRING_CATEGORIES = ImmutableSet.of("cat1", "cat2");
    public static final Set<Tag> TAGS = TagUtils.toTagSet(STRING_TAGS,  "assessment.tag");
    public static final Set<Tag> CATEGORIES = TagUtils.toTagSet(STRING_CATEGORIES, "assessment.category");
    public static final DateTime CREATED_ON = TIMESTAMP;
    public static final DateTime MODIFIED_ON = CREATED_ON.plusHours(1);
    public static final Map<String, Set<String>> CUSTOMIZATION_FIELDS = ImmutableMap.of("node1",
            ImmutableSet.of("field1", "field2"));    
}

package org.sagebionetworks.bridge;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTimeZone;
import org.jsoup.safety.Whitelist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

public class BridgeConstants {
    // see https://owasp.org/www-community/OWASP_Validation_Regex_Repository
    public static final String OWASP_REGEXP_VALID_EMAIL = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    
    public static final TypeReference<Set<String>> STRING_SET_TYPEREF = new TypeReference<Set<String>>() {};
    public static final TypeReference<Map<String, Map<String, JsonNode>>> UPDATES_TYPEREF = new TypeReference<Map<String, Map<String, JsonNode>>>() {};

    public static final String SAGE_ID = "sage-bionetworks";
    public static final String SAGE_NAME = "Sage Bionetworks";
    
    public static final String SYNAPSE_OAUTH_VENDOR_ID = "synapse";

    public static final String ID_FIELD_NAME = "identifier";
    public static final String TYPE_FIELD_NAME = "type";
    
    public static final String SHARED_ASSESSMENTS_ERROR = "Only shared assessment APIs are enabled for the shared assessment library.";

    public static final String APP_ACCESS_EXCEPTION_MSG = "Account does not have access to that app.";
    
    public static final String SYNAPSE_OAUTH_CLIENT_SECRET = "synapse.oauth.client.secret";
    public static final String SYNAPSE_OAUTH_CLIENT_ID = "synapse.oauth.client.id";
    public static final String SYNAPSE_OAUTH_URL = "synapse.oauth.url";
    public static final int SYNAPSE_TIMEOUT = 10000;
    
    public static final String MAX_USERS_ERROR = "While app is in evaluation mode, it may not exceed %s accounts.";
    public static final String BRIDGE_IDENTIFIER_ERROR = "must contain only lower-case letters and/or numbers with optional dashes";
    public static final String BRIDGE_EVENT_ID_ERROR = "must contain only lower- or upper-case letters, numbers, dashes, and/or underscores";
    public static final String CALLER_NOT_MEMBER_ERROR = "Assessment must be associated to the caller’s organization.";
    public static final String NEGATIVE_OFFSET_ERROR = "offsetBy cannot be negative";
    public static final String NONPOSITIVE_REVISION_ERROR = "revision cannot be less than 1";

    // App ID for the test app, used in local tests and most integ tests.
    public static final String API_APP_ID = "api";

    /** A common string constraint we place on model identifiers. */
    public static final String BRIDGE_IDENTIFIER_PATTERN = "^[a-z0-9-]+$";

    // App ID used for the Shared Module Library
    public static final String SHARED_APP_ID = "shared";

    /** A common string constraint Synapse places on model identifiers. */
    public static final String SYNAPSE_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_-]+$";
    
    /** The pattern used to validate activity event keys and automatic custom event keys. */
    public static final String BRIDGE_EVENT_ID_PATTERN = "^[a-zA-Z0-9_-]+$";
    
    /** The pattern used to validate app config element IDs, which are similar to event IDs 
     * but can be “namespaced” with the prefix “bridge:” to mark and protect system-specific,
     * configurable enumerated value sets.
     */
    public static final String APP_CONFIG_ELEMENT_ID_PATTERN = "^(bridge:)?[a-zA-Z0-9_-]+$";
    
    /** The pattern of a valid JavaScript variable/object property name. */
    public  static final String JS_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_][a-zA-Z0-9_-]*$";
    
    public static final String BRIDGE_API_STATUS_HEADER = "Bridge-Api-Status";

    public static final String BRIDGE_DEPRECATED_STATUS = "you're calling a deprecated endpoint";

    public static final String WARN_NO_USER_AGENT =  "we can't parse your User-Agent header, cannot filter by application version";

    public static final String WARN_NO_ACCEPT_LANGUAGE = "you haven't included an Accept-Language header, cannot filter by language";

    public static final String SESSION_TOKEN_HEADER = "Bridge-Session";

    public static final String CLEAR_SITE_DATA_HEADER = "Clear-Site-Data";
    
    public static final String CLEAR_SITE_DATA_VALUE = "\"cache\", \"cookies\", \"storage\", \"executionContexts\"";

    /** Used by Heroku to pass in the request ID */
    public static final String X_REQUEST_ID_HEADER = "X-Request-Id";

    public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /** Limit the total length of JSON string that is submitted as client data for a scheduled activity. */
    public static final int CLIENT_DATA_MAX_BYTES = 8192;

    /** Used to cap the number of dupe records we fetch from DDB and the number of log messages we write. */
    public static final int DUPE_RECORDS_MAX_COUNT = 10;

    public static final String STUDY_PROPERTY = "study";
    
    public static final String APP_ID_PROPERTY = "appId";

    public static final DateTimeZone LOCAL_TIME_ZONE = DateTimeZone.forID("America/Los_Angeles");

    // 12 hrs after last activity
    public static final int BRIDGE_SESSION_EXPIRE_IN_SECONDS = 12 * 60 * 60;

    // 7 days
    public static final int SIGNED_CONSENT_DOWNLOAD_EXPIRE_IN_SECONDS = (7 * 24 * 60 * 60);
    
    // 5 minutes
    public static final int BRIDGE_UPDATE_ATTEMPT_EXPIRE_IN_SECONDS = 5 * 60;
    
    // 5 hrs
    public static final int BRIDGE_VIEW_EXPIRE_IN_SECONDS = 5 * 60 * 60;
    
    // 3 minutes
    public static final int APP_LINKS_EXPIRE_IN_SECONDS = 3* 60;
    
    // 1 minute
    public static final int BRIDGE_APP_EMAIL_STATUS_IN_SECONDS = 60;
    
    // 15 seconds
    public static final int REAUTH_TOKEN_CACHE_LOOKUP_IN_SECONDS = 15;

    // 3 days
    public static final int REAUTH_TOKEN_GRACE_PERIOD_SECONDS = (3*24*60*60);

    public static final String SCHEDULE_STRATEGY_PACKAGE = "org.sagebionetworks.bridge.models.schedules.";

    public static final String ASSETS_HOST = "assets.sagebridge.org";
    
    public static final String JSON_MIME_TYPE = "application/json; charset=utf-8";

    /** Per-request metrics expires in the cache after 120 seconds. */
    public static final int METRICS_EXPIRE_SECONDS = 2 * 60;
    
    public static final int API_MINIMUM_PAGE_SIZE = 5;
    
    public static final int API_DEFAULT_PAGE_SIZE = 50;
    
    public static final int API_MAXIMUM_PAGE_SIZE = 100;
    
    public static final String PAGE_SIZE_ERROR = "pageSize must be from "+API_MINIMUM_PAGE_SIZE+"-"+API_MAXIMUM_PAGE_SIZE+" records";
    
    public static final String TEST_USER_GROUP = "test_user";
    
    public static final String EXPIRATION_PERIOD_KEY = "expirationPeriod";
    
    public static final String CONSENT_URL = "consentUrl";
    
    public static final int ONE_DAY_IN_SECONDS = 60*60*24;

    /** We want app links to fit in a single SMS, so limit them to 140 chars. */
    public static final int APP_LINK_MAX_LENGTH = 140;

    /**
     * 11 character label as to who sent the SMS message. Only in some supported countries (not US):
     * https://support.twilio.com/hc/en-us/articles/223133767-International-support-for-Alphanumeric-Sender-ID
     */
    public static final String AWS_SMS_SENDER_ID = "AWS.SNS.SMS.SenderID";
    /** 
     * SMS type ("Promotional" or "Transactional"). 
     */
    public static final String AWS_SMS_TYPE = "AWS.SNS.SMS.SMSType";
    
    /**
     * This whitelist adds a few additional tags and attributes that are used by the CKEDITOR options 
     * we have displayed in the UI.
     */
    public static final Whitelist CKEDITOR_WHITELIST = Whitelist.relaxed()
            .preserveRelativeLinks(true)
            .addTags("hr", "s", "caption")
            .addAttributes(":all", "style", "scope", "class")
            .addAttributes("img", "src", "onerror", "alt", "brimg")
            .addAttributes("a", "target", "href")
            .addAttributes("table", "align", "border", "cellpadding", "cellspacing", "summary");
    

    /**
     * This list of zip code prefixes with less than 20,000 people was taken from 
     * https://www.johndcook.com/blog/2016/06/29/sparsely-populated-zip-codes/
     */
    public static final List<String> SPARSE_ZIP_CODE_PREFIXES = ImmutableList.of("036", "059", "063", 
            "102", "203", "556", "692", "790", "821", "823", "830", "831", "878", "879", "884", 
            "890", "893");

}

package org.sagebionetworks.bridge.cache;

import java.util.List;
import java.util.Objects;

import org.sagebionetworks.bridge.models.ThrottleRequestType;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * A utility to provide consistency, centralization, and type safety for the keys we use 
 * to store data in Redis. This makes it easier to determine which keys can be returned as 
 * part of the administrative API so we can keep PII data out of the cache keys we return.
 */
public final class CacheKey {
    
    private static final String[] PUBLIC_KEYS = new String[] { "emailVerificationStatus", 
            "AppConfigList", "channel-throttling", "lock", "App", "Subpopulation", 
            "SubpopulationList", "view" };
    
    public final static boolean isPublic(String key) {
        for (String suffix : PUBLIC_KEYS) {
            if (key.endsWith(":"+suffix)) {
                return true;
            }
        }
        return false;
    }
    public static final CacheKey etag(Class<?> model, String... keys) {
        return new CacheKey(COLON_JOINER.join(keys), model.getSimpleName(), "Etag");
    }
    public static final CacheKey publicStudy(String appId, String studyId) {
        return new CacheKey(appId, studyId, "PublicStudy");
    }
    public static final CacheKey scheduleModificationTimestamp(String appId, String studyId) {
        return new CacheKey(studyId, appId, "ScheduleModifiedOnByStudy");
    }
    public static final CacheKey orgSponsoredStudies(String appId, String orgId) {
        return new CacheKey(orgId, appId, "OrgSponsoredStudies");
    }
    public static final CacheKey tagList() {
        return new CacheKey("TagList");
    }
    public static final CacheKey reauthTokenLookupKey(String userId, String appId) {
        return new CacheKey(userId, appId, "ReauthToken");
    }
    public static final CacheKey shortenUrl(String token) {
        return new CacheKey(token, "ShortenedUrl");
    }
    public static final CacheKey appConfigList(String appId) {
        return new CacheKey(appId, "AppConfigList");
    }
    /**
     * Cache key for mapping a used channel sign-in token to the cached session token, used for when a second channel
     * sign-in call is made within the grace period.
     */
    public static CacheKey channelSignInToSessionToken(String signInToken) {
        return new CacheKey(signInToken, "channel-signin-to-session-token");
    }

    public static CacheKey channelThrottling(ThrottleRequestType throttleType, String userId) {
        return new CacheKey(userId, throttleType.name().toLowerCase(), "channel-throttling");
    }
    public static final CacheKey emailSignInRequest(SignIn signIn) {
        return new CacheKey(signIn.getEmail(), signIn.getAppId(), "signInRequest");
    }
    /** The email verification status from Amazon SES, which we cache for a short time. Not involved with 
     * verification of an individual's email address. So we do return it through the cache API.
     */
    public static final CacheKey emailVerification(String email) {
        return new CacheKey(email, "emailVerificationStatus");
    }
    public static final CacheKey itp(SubpopulationGuid subpopGuid, String appId, Phone phone) {
        return new CacheKey(subpopGuid.getGuid(), phone.getNumber(), appId, "itp");
    }
    public static final CacheKey itp(SubpopulationGuid subpopGuid, String appId, String email) {
        return new CacheKey(subpopGuid.getGuid(), email, appId, "itp");
    }
    public static final CacheKey lock(String value, Class<?> clazz) {
        return new CacheKey(value, clazz.getCanonicalName(), "lock");
    }
    public static final CacheKey passwordResetForEmail(String sptoken, String appId) {
        return new CacheKey(sptoken, appId); // no type, not great
    }
    public static final CacheKey passwordResetForPhone(String sptoken, String appId) { 
        return new CacheKey(sptoken, "phone", appId); // no type, not great
    }
    public static final CacheKey phoneSignInRequest(SignIn signIn) {
        return new CacheKey(signIn.getPhone().getNumber(), signIn.getAppId(),"phoneSignInRequest");
    }
    public static final CacheKey requestInfo(String userId) {
        return new CacheKey(userId, "request-info");
    }
    public static final CacheKey app(String appId) {
        return new CacheKey(appId, "App");
    }    
    public static final CacheKey subpop(SubpopulationGuid subpopGuid, String appId) {
        return new CacheKey(subpopGuid.getGuid(), appId, "Subpopulation");
    }
    public static final CacheKey subpopList(String appId) {
        return new CacheKey(appId, "SubpopulationList");
    }
    public static final CacheKey userIdToSession(String userId) {
        return new CacheKey(userId, "session2", "user");
    }
    public static final CacheKey tokenToUserId(String sessionToken) {
        return new CacheKey(sessionToken, "session2");
    }
    
    public static final CacheKey verificationToken(String sptoken) {
        return new CacheKey(sptoken); // no type, not great
    }
    public static final CacheKey viewKey(Class<?> clazz, String... elements) {
        List<String> list = Lists.newArrayList(elements);
        list.add(clazz.getSimpleName());
        list.add("view");
        return new CacheKey(COLON_JOINER.join(list));
    }
    
    private static final Joiner COLON_JOINER = Joiner.on(":");
    
    private final String key;
    
    private CacheKey(String... elements) {
        this.key = COLON_JOINER.join(elements);
    }
    @Override
    public String toString() {
        return key;
    }
    @Override
    public int hashCode() {
        return Objects.hashCode(key);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        CacheKey other = (CacheKey) obj;
        return Objects.equals(this.key, other.key);
    }
}

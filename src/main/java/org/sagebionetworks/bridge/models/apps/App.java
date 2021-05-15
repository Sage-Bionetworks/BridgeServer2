package org.sagebionetworks.bridge.models.apps;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.upload.ExporterVersion;
import org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

/**
 * A Bridge app.
 *
 */
@JsonDeserialize(as=DynamoApp.class)
public interface App extends BridgeEntity {
    ObjectWriter APP_LIST_WRITER = new BridgeObjectMapper().writer(
        new SimpleFilterProvider().addFilter("filter",
        SimpleBeanPropertyFilter.filterOutAllExcept("name", "identifier")));

    /** Convenience method for creating an App using a concrete implementation. */
    static App create() {
        return new DynamoApp();
    }

    /**
     * The display name of the app (will be seen by participants in email). This name makes the 
     * most sense when it starts with "The".
     */
    String getName();
    void setName(String name);

    /**
     * A short display name for SMS messages and other highly constrained UIs. 10 characters of less. 
     */
    String getShortName();
    void setShortName(String shortName);
    
    /**
     * The name of the institution or research group that owns the app. 
     */
    String getSponsorName();
    void setSponsorName(String sponsorName);
    
    /**
     * A string that uniquely identifies the app, and serves as a domain within which accounts are 
     * scoped for that app. By convention, should be an institution acronym or tag, a dash, and then 
     * an acronym or short phrase for the app. For example "uw-asthma" or "ohsu-molemapper". Cannot
     * be changed once created.
     */
    String getIdentifier();
    void setIdentifier(String identifier);
    
    /**
     * DynamoDB version number for optimistic locking of record.
     */
    Long getVersion();
    void setVersion(Long version);

    /**
     * Custom events that should be generated for participant upon enrollment. The key in this map is the eventKey, and
     * the value is the offset after the enrollment event (eg, "P1D" for one day after enrollment, "P2W" for 2 weeks
     * after enrollment). Note that this API will automatically pre-pend "custom:" in front of the event key when
     * generating the eventId (eg, eventKey "studyBurstStart" becomes event ID "custom:studyBurstStart").
     */
    Map<String, String> getAutomaticCustomEvents();

    /** @see #getAutomaticCustomEvents */
    void setAutomaticCustomEvents(Map<String, String> automaticCustomEvents);

    /**
     * True if the automatic email verification email on sign-up should be suppressed. False if the email should be
     * sent on sign-up. This is generally used in conjunction with email sign-in, where sending a separate email
     * verification email would be redundant.
     */
    boolean isAutoVerificationEmailSuppressed();

    /** @see #isAutoVerificationEmailSuppressed */
    void setAutoVerificationEmailSuppressed(boolean autoVerificationEmailSuppressed);

    /**
     * Selects the version of the exporter to be used by the app. If not set, defaults to LEGACY_EXPORTER. Can only be
     * set during app creation time, or updated by a super_admin.
     */
    ExporterVersion getExporterVersion();

    /** @see #getExporterVersion */
    void setExporterVersion(ExporterVersion exporterVersion);

    /**
     * True if sessions for unprivileged participant accounts should be locked to an IP address. (Privileged account
     * sessions are _always_ locked to an IP address.)
     */
    boolean isParticipantIpLockingEnabled();

    /** @see #isParticipantIpLockingEnabled */
    void setParticipantIpLockingEnabled(boolean participantIpLockingEnabled);

    /**
     * If true, the channel (email or phone number) used to sign in will be checked and must be verified
     * for sign in to succeed. This is false for legacy apps but set to true for newer apps. 
     */
    boolean isVerifyChannelOnSignInEnabled();
    
    /** @see #isVerifyChannelOnSignInEnabled */
    void setVerifyChannelOnSignInEnabled(boolean verifyChannelOnSignInEnabled);
    
    /**
     * True if we create and return a reauthentication token in the session that can be used to reauthenticate 
     * without a password. False otherwise.
     */
    Boolean isReauthenticationEnabled();
    
    void setReauthenticationEnabled(Boolean reauthenticationEnabled);
    
    /**
     * User must confirm that they are at least this many years old in order to
     * participate in the app. 
     */
    int getMinAgeOfConsent();
    void setMinAgeOfConsent(int minAge);

    /**
     * <p>
     * True if the Bridge Exporter should include the appId prefix in the "originalTable" field in the appVersion
     * (now "Health Data Summary") table in Synapse. This exists primarily because we want to remove redundant prefixes
     * from the Synapse tables (to improve reporting), but we don't want to break existing apps or partition
     * existing data.
     * </p>
     * <p>
     * The setting is "reversed" so we don't have to backfill a bunch of old apps.
     * </p>
     * <p>
     * This is a "hidden" setting, primarily to support back-compat for old apps. New apps should be created with
     * this flag set to true, and only admins can change the flag.
     * </p>
     */
    boolean isAppIdExcludedInExport();

    /** @see #isAppIdExcludedInExport */
    void setAppIdExcludedInExport(boolean appIdExcludedInExport);

    /**
     * The email address that will be given to study participants and other end user for all support 
     * requests and queries (technical, app-related, etc.). This can be a comma-separated list of 
     * email addresses.
     */
    String getSupportEmail();
    void setSupportEmail(String email);

    /** Synapse team ID that is granted read access to exported health data records. */
    Long getSynapseDataAccessTeamId();

    /** @see #getSynapseDataAccessTeamId */
    void setSynapseDataAccessTeamId(Long teamId);

    /** The Synapse project to export health data records to. */
    String getSynapseProjectId();

    /** @see #getSynapseProjectId */
    void setSynapseProjectId(String projectId);

    /**
     * Set a limit on the number of accounts that can be created for this app. This is intended 
     * to establish evaluation apps with limited accounts or enrollment. The value should be 
     * set to 0 for production apps (there is a runtime cost to enforcing this limit). If 
     * value is zero, no limit is enforced.
     */
    int getAccountLimit();
    void setAccountLimit(int accountLimit);
    
    /**
     * The email address for a technical contact who can coordinate with the Bridge Server team on 
     * issues related either to client development or hand-offs of the app data through the 
     * Bridge server. This can be a comma-separated list of email addresses.
     */
    String getTechnicalEmail();
    void setTechnicalEmail(String email);

    /**
     * By default, all apps are exported using the default nightly schedule. Some apps may need custom schedules
     * for hourly or on-demand exports. To prevent this app from being exported twice (once by the custom schedule,
     * once by the default schedule), you should set this attribute to true.
     */
    boolean getUsesCustomExportSchedule();

    /** @see #getUsesCustomExportSchedule */
    void setUsesCustomExportSchedule(boolean usesCustomExportSchedule);

    /**
     * <p>
     * Metadata fields can be configured for any app. This metadata will be implicitly added to every schema and
     * automatically added to every Synapse table.
     * </p>
     * <p>
     * All metadata field definitions are implicitly optional. The "required" field in metadata field definitions is
     * ignored.
     * </p>
     */
    List<UploadFieldDefinition> getUploadMetadataFieldDefinitions();

    /** @see #getUploadMetadataFieldDefinitions */
    void setUploadMetadataFieldDefinitions(List<UploadFieldDefinition> uploadMetadataFieldDefinitions);

    /**
     * How strictly to validate health data and uploads. If this and {@link #isStrictUploadValidationEnabled} are
     * specified, this enum takes precedence.
     */
    UploadValidationStrictness getUploadValidationStrictness();

    /** @see #getUploadValidationStrictness */
    void setUploadValidationStrictness(UploadValidationStrictness uploadValidationStrictness);

    /**
     * Copies of all consent agreements, as well as rosters of all participants in a app, or any 
     * other app governance issues, will be emailed to this address. This can be a comma-separated 
     * list of email addresses. 
     */
    String getConsentNotificationEmail();
    void setConsentNotificationEmail(String email);

    /** True if the consent notification email is verified. False if not. */
    Boolean isConsentNotificationEmailVerified();

    /** @see #isConsentNotificationEmailVerified */
    void setConsentNotificationEmailVerified(Boolean verified);

    /**
     * Extension attributes that can be accepted on the UserProfile object for this app. These 
     * attributes will be exported with the participant roster. 
     */
    Set<String> getUserProfileAttributes();
    void setUserProfileAttributes(Set<String> attributes);

    /**
     * The enumerated task identifiers that can be used when scheduling tasks for this app. These are provided 
     * through the UI to prevent errors when creating schedules. 
     */
    Set<String> getTaskIdentifiers();
    void setTaskIdentifiers(Set<String> taskIdentifiers);

    /**
     * These are now deprecated in favor of customEvents. If added they will be readable from that mapping 
     * as FUTURE_ONLY events.  
     */
    @Deprecated
    Set<String> getActivityEventKeys();
    @Deprecated
    void setActivityEventKeys(Set<String> activityEventKeys);
    
    /**
     * The configuration of custom activity events for this app. The key is the ID of the event, and and the value
     * is the update type. All event IDs declared in the activityEventKeys field of App that are not in the 
     * customEvents map will be added with the update type of FUTURE_ONLY, the default for custom events before 
     * this could be customized.
     */
    Map<String,ActivityEventUpdateType> getCustomEvents();
    void setCustomEvents(Map<String,ActivityEventUpdateType> customEvents);

    /**
     * The enumerated set of data group strings that can be assigned to users in this app. This enumeration ensures 
     * the values are meaningful to the app and the data groups cannot be filled maliciously with junk tags. 
     */
    Set<String> getDataGroups();
    void setDataGroups(Set<String> dataGroups);
    
    /**
     * The password policy for users signing up for this app. 
     */
    PasswordPolicy getPasswordPolicy();
    void setPasswordPolicy(PasswordPolicy passwordPolicy);

    /**
     * Is this app active? Currently not in use, a de-activated app will be hidden from the 
     * app APIs and will no longer be available for use (a logical delete).
     */
    boolean isActive();
    void setActive(boolean active);

    /** True if uploads in this app should fail on strict validation errors. */
    boolean isStrictUploadValidationEnabled();

    /** @see #isStrictUploadValidationEnabled */
    void setStrictUploadValidationEnabled(boolean enabled);
    
    /** True if we allow users in this app to send an email with a link to sign into the app. */ 
    boolean isEmailSignInEnabled();
    
    /** @see #isEmailSignInEnabled */
    void setEmailSignInEnabled(boolean emailSignInEnabled);
    
    /** True if we allow users in this app to send an SMS message with a token that can be used to sign into the app. */ 
    boolean isPhoneSignInEnabled();
    
    /** @see #isPhoneSignInEnabled */
    void setPhoneSignInEnabled(boolean phoneSignInEnabled);
    
    /** True if this app will export the healthCode when generating a participant roster. */
    boolean isHealthCodeExportEnabled();
    
    /** @see #isHealthCodeExportEnabled(); */
    void setHealthCodeExportEnabled(boolean enabled);
    
    /** True if this app requires users to verify their email addresses in order to sign up. 
     * True by default.
     */
    boolean isEmailVerificationEnabled();
    
    /** @see #isEmailVerificationEnabled(); */
    void setEmailVerificationEnabled(boolean enabled);
    
    /** 
     * True if the external ID must be provided when the user signs up. If validation is also 
     * enabled, this app is configured to use lab codes if desired (username and password auto-
     * generated from the external ID). If this is false, the external ID is not required when 
     * submitting a sign up. 
     */
    boolean isExternalIdRequiredOnSignup();
    
    /** @see #isExternalIdRequiredOnSignup(); */
    void setExternalIdRequiredOnSignup(boolean externalIdRequiredOnSignup);
    
    /**
     * Minimum supported app version number. If set, user app clients pointing to an older version will 
     * fail with an httpResponse status code of 410.
     */
    Map<String, Integer> getMinSupportedAppVersions();
	
	/** @see #getMinSupportedAppVersions(); */
    void setMinSupportedAppVersions(Map<String, Integer> map);
    
    /**
     * A map between operating system names, and the platform ARN necessary to register a device to 
     * receive mobile push notifications for this app, on that platform.
     */
    Map<String, String> getPushNotificationARNs();

    /** @see #getPushNotificationARNs(); */
    void setPushNotificationARNs(Map<String, String> pushNotificationARNs);
    
    /**
     * A map between operating system names, and a link to send via SMS to acquire the app. 
     * This can be either to an app store, or an intermediate web page that will route to a final 
     * app or appstore.
     */
    Map<String, String> getInstallLinks();
    
    /** @see #getInstallLinks(); */
    void setInstallLinks(Map<String, String> installLinks);

    /** The flag to disable exporting or not. */
    boolean getDisableExport();

    /** @see #getDisableExport */
    void setDisableExport(boolean disable);
    
    /** Get the OAuth providers for access tokens. */
    Map<String, OAuthProvider> getOAuthProviders();
    
    /** @see #getOAuthProviders */
    void setOAuthProviders(Map<String, OAuthProvider> providers);

    List<AppleAppLink> getAppleAppLinks();
    void setAppleAppLinks(List<AppleAppLink> appleAppLinks);
    
    List<AndroidAppLink> getAndroidAppLinks();
    void setAndroidAppLinks(List<AndroidAppLink> androidAppLinks);
    
    /** If the phone number must be verified, do we suppress sending an SMS message on sign up? */
    boolean isAutoVerificationPhoneSuppressed();
    
    /** Should a new phone number be verified? */
    void setAutoVerificationPhoneSuppressed(boolean autoVerificationPhoneSuppressed);
    
    /** Get the default templates specified by type of template. */
    Map<String,String> getDefaultTemplates();
    
    /** @see #getDefaultTemplates */
    void setDefaultTemplates(Map<String,String> defaultTemplates);
}

package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedJson;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.apps.AndroidAppLink;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.AppleAppLink;
import org.sagebionetworks.bridge.models.apps.OAuthProvider;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.upload.ExporterVersion;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;

@DynamoDBTable(tableName = "Study")
@BridgeTypeName("App")
@JsonFilter("filter")
public final class DynamoApp implements App {
    
    public static class AppleLinksMarshaller extends ListMarshaller<AppleAppLink> {
        private static final TypeReference<List<AppleAppLink>> FIELD_LIST_TYPE =
                new TypeReference<List<AppleAppLink>>() {};
        @Override
        public TypeReference<List<AppleAppLink>> getTypeReference() {
            return FIELD_LIST_TYPE;
        }
    }
    
    public static class AndroidLinksMarshaller extends ListMarshaller<AndroidAppLink> {
        private static final TypeReference<List<AndroidAppLink>> FIELD_LIST_TYPE =
                new TypeReference<List<AndroidAppLink>>() {};
        @Override
        public TypeReference<List<AndroidAppLink>> getTypeReference() {
            return FIELD_LIST_TYPE;
        }
    }
    
    private String name;
    private String shortName;
    private String sponsorName;
    private String identifier;
    private Map<String, String> automaticCustomEvents;
    private boolean autoVerificationEmailSuppressed;
    private ExporterVersion exporterVersion;
    private boolean participantIpLockingEnabled;
    private boolean appIdExcludedInExport;
    private String supportEmail;
    private Long synapseDataAccessTeamId;
    private String synapseProjectId;
    private String technicalEmail;
    private boolean usesCustomExportSchedule;
    private List<UploadFieldDefinition> uploadMetadataFieldDefinitions;
    private UploadValidationStrictness uploadValidationStrictness;
    private String consentNotificationEmail;
    private Boolean consentNotificationEmailVerified;
    private int minAgeOfConsent;
    private int accountLimit;
    private Long version;
    private boolean active;
    private Set<String> profileAttributes;
    private Set<String> taskIdentifiers;
    private Set<String> activityEventKeys;
    private Set<String> dataGroups;
    private PasswordPolicy passwordPolicy;
    private boolean strictUploadValidationEnabled;
    private boolean healthCodeExportEnabled;
    private boolean emailVerificationEnabled;
    private boolean externalIdValidationEnabled;
    private boolean emailSignInEnabled;
    private boolean phoneSignInEnabled;
    private boolean externalIdRequiredOnSignup;
    private Boolean reauthenticationEnabled;
    private boolean autoVerificationPhoneSuppressed;
    private boolean verifyChannelOnSignInEnabled;
    private Map<String, Integer> minSupportedAppVersions;
    private Map<String, String> pushNotificationARNs;
    private Map<String, String> installLinks;
    private Map<String, OAuthProvider> oauthProviders;
    private boolean disableExport;
    private List<AppleAppLink> appleAppLinks;
    private List<AndroidAppLink> androidAppLinks;
    private Map<String, String> defaultTemplates;

    public DynamoApp() {
        automaticCustomEvents = new HashMap<>();
        uploadMetadataFieldDefinitions = new ArrayList<>();
        profileAttributes = new HashSet<>();
        taskIdentifiers = new HashSet<>();
        activityEventKeys = new HashSet<>();
        dataGroups = new HashSet<>();
        minSupportedAppVersions = new HashMap<>();
        pushNotificationARNs = new HashMap<>();
        installLinks = new HashMap<>();
        oauthProviders = new HashMap<>();
        appleAppLinks = new ArrayList<>();
        androidAppLinks = new ArrayList<>();
        defaultTemplates = new HashMap<>();
    }

    /** {@inheritDoc} */
    @Override
    public String getSponsorName() {
        return sponsorName;
    }

    @Override
    public void setSponsorName(String sponsorName) {
        this.sponsorName = sponsorName;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    @Override
    public String getShortName() {
        return shortName;
    }

    @Override
    public void setShortName(String shortName) {
        this.shortName = shortName;
    }
    
    /** {@inheritDoc} */
    @Override
    @DynamoDBHashKey
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /** {@inheritDoc} */
    @Override
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> getAutomaticCustomEvents() {
        return automaticCustomEvents;
    }

    /** {@inheritDoc} */
    @Override
    public void setAutomaticCustomEvents(Map<String, String> automaticCustomEvents) {
        this.automaticCustomEvents = automaticCustomEvents == null ? new HashMap<>() : automaticCustomEvents;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAutoVerificationEmailSuppressed() {
        return autoVerificationEmailSuppressed;
    }

    /** {@inheritDoc} */
    @Override
    public void setAutoVerificationEmailSuppressed(boolean autoVerificationEmailSuppressed) {
        this.autoVerificationEmailSuppressed = autoVerificationEmailSuppressed;
    }

    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public ExporterVersion getExporterVersion() {
        return exporterVersion != null ? exporterVersion : ExporterVersion.LEGACY_EXPORTER;
    }

    @Override
    public void setExporterVersion(ExporterVersion exporterVersion) {
        this.exporterVersion = exporterVersion;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isParticipantIpLockingEnabled() {
        return participantIpLockingEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setParticipantIpLockingEnabled(boolean participantIpLockingEnabled) {
        this.participantIpLockingEnabled = participantIpLockingEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isVerifyChannelOnSignInEnabled() {
        return verifyChannelOnSignInEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setVerifyChannelOnSignInEnabled(boolean verifyChannelOnSignInEnabled) {
        this.verifyChannelOnSignInEnabled = verifyChannelOnSignInEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public Boolean isReauthenticationEnabled() {
        return reauthenticationEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setReauthenticationEnabled(Boolean reauthenticationEnabled) {
        this.reauthenticationEnabled = reauthenticationEnabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public int getMinAgeOfConsent() {
        return minAgeOfConsent;
    }

    @Override
    public void setMinAgeOfConsent(int minAge) {
        this.minAgeOfConsent = minAge;
    }

    /** {@inheritDoc} */
    @DynamoDBAttribute(attributeName = "studyIdExcludedInExport")
    @Override
    public boolean isAppIdExcludedInExport() {
        return appIdExcludedInExport;
    }

    /** {@inheritDoc} */
    @Override
    public void setAppIdExcludedInExport(boolean appIdExcludedInExport) {
        this.appIdExcludedInExport = appIdExcludedInExport;
    }
    
    // for backwards compatibility, we must continue to expose this property
    @DynamoDBIgnore
    public boolean isStudyIdExcludedInExport() {
        return appIdExcludedInExport;
    }

    /** {@inheritDoc} */
    @Override
    public String getSupportEmail() {
        return supportEmail;
    }

    @Override
    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }

    /** {@inheritDoc} */
    @Override
    public Long getSynapseDataAccessTeamId() {
        return synapseDataAccessTeamId;
    }

    /** {@inheritDoc} */
    @Override
    public void setSynapseDataAccessTeamId(Long teamId) {
        this.synapseDataAccessTeamId = teamId;
    }

    /** {@inheritDoc} */
    @Override
    public String getSynapseProjectId() {
        return synapseProjectId;
    }

    /** {@inheritDoc} */
    @Override
    public void setSynapseProjectId(String projectId) {
        this.synapseProjectId = projectId;
    }

    /** {@inheritDoc} */
    @Override
    public String getTechnicalEmail() {
        return technicalEmail;
    }

    @Override
    public void setTechnicalEmail(String technicalEmail) {
        this.technicalEmail = technicalEmail;
    }

    /** {@inheritDoc} */
    @Override
    public boolean getUsesCustomExportSchedule() {
        return usesCustomExportSchedule;
    }

    /** {@inheritDoc} */
    @Override
    public void setUsesCustomExportSchedule(boolean usesCustomExportSchedule) {
        this.usesCustomExportSchedule = usesCustomExportSchedule;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter = DynamoUploadSchema.FieldDefinitionListMarshaller.class)
    @Override
    public List<UploadFieldDefinition> getUploadMetadataFieldDefinitions() {
        return uploadMetadataFieldDefinitions;
    }

    /** {@inheritDoc} */
    @Override
    public void setUploadMetadataFieldDefinitions(List<UploadFieldDefinition> uploadMetadataFieldDefinitions) {
        this.uploadMetadataFieldDefinitions = (uploadMetadataFieldDefinitions == null) ? new ArrayList<>() :
                uploadMetadataFieldDefinitions;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public UploadValidationStrictness getUploadValidationStrictness() {
        return uploadValidationStrictness;
    }

    /** {@inheritDoc} */
    @Override
    public void setUploadValidationStrictness(UploadValidationStrictness uploadValidationStrictness) {
        this.uploadValidationStrictness = uploadValidationStrictness;
    }

    /** {@inheritDoc} */
    @Override
    public String getConsentNotificationEmail() {
        return consentNotificationEmail;
    }

    @Override
    public void setConsentNotificationEmail(String consentNotificationEmail) {
        this.consentNotificationEmail = consentNotificationEmail;
    }

    /** {@inheritDoc} */
    @Override
    public Boolean isConsentNotificationEmailVerified() {
        return consentNotificationEmailVerified;
    }

    /** {@inheritDoc} */
    @Override
    public void setConsentNotificationEmailVerified(Boolean verified) {
        this.consentNotificationEmailVerified = verified;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    @Override
    public Set<String> getUserProfileAttributes() {
        return profileAttributes;
    }

    @Override
    public void setUserProfileAttributes(Set<String> profileAttributes) {
        this.profileAttributes = (profileAttributes == null) ? new HashSet<>() : profileAttributes;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    @Override
    public Set<String> getTaskIdentifiers() {
        return taskIdentifiers;
    }

    @Override
    public void setTaskIdentifiers(Set<String> taskIdentifiers) {
        this.taskIdentifiers = (taskIdentifiers == null) ? new HashSet<>() : taskIdentifiers;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter = StringSetMarshaller.class)
    @Override
    public Set<String> getActivityEventKeys() {
        return activityEventKeys;
    }

    /** {@inheritDoc} */
    @Override
    public void setActivityEventKeys(Set<String> activityEventKeys) {
        this.activityEventKeys = (activityEventKeys==null) ? new HashSet<>() : activityEventKeys;
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=StringSetMarshaller.class)
    @Override
    public Set<String> getDataGroups() {
        return dataGroups;
    }

    @Override
    public void setDataGroups(Set<String> dataGroups) {
        this.dataGroups = (dataGroups == null) ? new HashSet<>() : dataGroups;
    }
    
    /** {@inheritDoc} */
    @DynamoDBTypeConvertedJson
    @Override
    public PasswordPolicy getPasswordPolicy() {
        return passwordPolicy;
    }

    @Override
    public void setPasswordPolicy(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isStrictUploadValidationEnabled() {
        return strictUploadValidationEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setStrictUploadValidationEnabled(boolean enabled) {
        this.strictUploadValidationEnabled = enabled;
    }
    
    /** {@inheritDoc} */
    @Override 
    public boolean isEmailSignInEnabled() {
        return emailSignInEnabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public void setEmailSignInEnabled(boolean enabled){
        this.emailSignInEnabled = enabled;
    }
    
    /** {@inheritDoc} */
    @Override 
    public boolean isPhoneSignInEnabled() {
        return phoneSignInEnabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public void setPhoneSignInEnabled(boolean enabled){
        this.phoneSignInEnabled = enabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean isHealthCodeExportEnabled() {
        return healthCodeExportEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public void setHealthCodeExportEnabled(boolean enabled) {
        this.healthCodeExportEnabled = enabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean isEmailVerificationEnabled() {
        return emailVerificationEnabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public void setEmailVerificationEnabled(boolean enabled) {
        this.emailVerificationEnabled = enabled;
    }
    
    /** {@inheritDoc} */
    @Override
    public Map<String,Integer> getMinSupportedAppVersions() {
        return minSupportedAppVersions;
    }

    @Override
    public void setMinSupportedAppVersions(Map<String,Integer> map) {
        this.minSupportedAppVersions = (map == null) ? new HashMap<>() : map;
    }
    
    /** {@inheritDoc} */
    @Override
    public Map<String,String> getPushNotificationARNs() {
        return pushNotificationARNs;
    }

    @Override
    public void setPushNotificationARNs(Map<String,String> map) {
        this.pushNotificationARNs = (map == null) ? new HashMap<>() : map;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String,String> getInstallLinks() {
        return installLinks;
    }

    @Override
    public void setInstallLinks(Map<String,String> map) {
        this.installLinks = (map == null) ? new HashMap<>() : map;
    }
    
    @Override 
    public boolean getDisableExport() {
        return this.disableExport;
    }

    @Override public void setDisableExport(boolean disable) {
        this.disableExport = disable;
    }

    @Override
    public boolean isExternalIdRequiredOnSignup() {
        return externalIdRequiredOnSignup;
    }

    @Override
    public void setExternalIdRequiredOnSignup(boolean externalIdRequiredOnSignup) {
        this.externalIdRequiredOnSignup = externalIdRequiredOnSignup;
    }
    
    @Override
    public int getAccountLimit() {
        return accountLimit;
    }
    
    @Override
    public void setAccountLimit(int accountLimit) {
        this.accountLimit = accountLimit;
    }
    
    @JsonProperty("oAuthProviders")
    @DynamoDBTypeConverted(converter = OAuthProviderMapMarshaller.class)
    @Override
    public Map<String, OAuthProvider> getOAuthProviders() {
        return oauthProviders;
    }
    
    @Override
    public void setOAuthProviders(Map<String, OAuthProvider> oauthProviders) {
        this.oauthProviders = (oauthProviders == null) ? new HashMap<>() : oauthProviders;
    }
    
    @DynamoDBTypeConverted(converter = AppleLinksMarshaller.class)
    @Override
    public List<AppleAppLink> getAppleAppLinks() {
        return appleAppLinks;
    }
    
    @Override
    public void setAppleAppLinks(List<AppleAppLink> appleAppLinks) {
        this.appleAppLinks = appleAppLinks;
    }
    
    @DynamoDBTypeConverted(converter = AndroidLinksMarshaller.class)
    @Override
    public List<AndroidAppLink> getAndroidAppLinks() {
        return androidAppLinks;
    }
    
    @Override
    public void setAndroidAppLinks(List<AndroidAppLink> androidAppLinks) {
        this.androidAppLinks = androidAppLinks;
    }
    
    @Override
    public boolean isAutoVerificationPhoneSuppressed() {
        return autoVerificationPhoneSuppressed;
    }
    
    @Override
    public void setAutoVerificationPhoneSuppressed(boolean autoVerificationPhoneSuppressed) {
        this.autoVerificationPhoneSuppressed = autoVerificationPhoneSuppressed;
    }
    
    @Override
    public Map<String,String> getDefaultTemplates() {
        return defaultTemplates;
    }
    
    @Override
    public void setDefaultTemplates(Map<String,String> defaultTemplates) {
        this.defaultTemplates = (defaultTemplates == null) ? new HashMap<>() : defaultTemplates;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, shortName, sponsorName, identifier, automaticCustomEvents,
                autoVerificationEmailSuppressed, exporterVersion, participantIpLockingEnabled, appIdExcludedInExport,
                supportEmail, synapseDataAccessTeamId, synapseProjectId, technicalEmail, usesCustomExportSchedule,
                uploadMetadataFieldDefinitions, uploadValidationStrictness, consentNotificationEmail,
                consentNotificationEmailVerified, minAgeOfConsent, accountLimit, version, active, profileAttributes,
                taskIdentifiers, activityEventKeys, dataGroups, passwordPolicy, strictUploadValidationEnabled,
                healthCodeExportEnabled, emailVerificationEnabled, externalIdValidationEnabled, emailSignInEnabled,
                phoneSignInEnabled, externalIdRequiredOnSignup, minSupportedAppVersions, pushNotificationARNs,
                installLinks, disableExport, oauthProviders, appleAppLinks, androidAppLinks, reauthenticationEnabled,
                autoVerificationPhoneSuppressed, verifyChannelOnSignInEnabled, defaultTemplates);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoApp other = (DynamoApp) obj;

        return (Objects.equals(identifier, other.identifier)
                && Objects.equals(automaticCustomEvents, other.automaticCustomEvents)
                && Objects.equals(autoVerificationEmailSuppressed, other.autoVerificationEmailSuppressed)
                && Objects.equals(exporterVersion, other.exporterVersion)
                && Objects.equals(participantIpLockingEnabled, other.participantIpLockingEnabled)
                && Objects.equals(appIdExcludedInExport, other.appIdExcludedInExport)
                && Objects.equals(supportEmail, other.supportEmail)
                && Objects.equals(uploadMetadataFieldDefinitions, other.uploadMetadataFieldDefinitions)
                && Objects.equals(uploadValidationStrictness, other.uploadValidationStrictness)
                && Objects.equals(minAgeOfConsent, other.minAgeOfConsent) 
                && Objects.equals(name, other.name)
                && Objects.equals(shortName, other.shortName)
                && Objects.equals(passwordPolicy, other.passwordPolicy) 
                && Objects.equals(active, other.active))
                && Objects.equals(consentNotificationEmail, other.consentNotificationEmail)
                && Objects.equals(consentNotificationEmailVerified, other.consentNotificationEmailVerified)
                && Objects.equals(version, other.version)
                && Objects.equals(profileAttributes, other.profileAttributes)
                && Objects.equals(taskIdentifiers, other.taskIdentifiers)
                && Objects.equals(activityEventKeys, other.activityEventKeys)
                && Objects.equals(dataGroups, other.dataGroups)
                && Objects.equals(sponsorName, other.sponsorName)
                && Objects.equals(synapseDataAccessTeamId, other.synapseDataAccessTeamId)
                && Objects.equals(synapseProjectId, other.synapseProjectId)
                && Objects.equals(technicalEmail, other.technicalEmail)
                && Objects.equals(usesCustomExportSchedule, other.usesCustomExportSchedule)
                && Objects.equals(strictUploadValidationEnabled, other.strictUploadValidationEnabled)
                && Objects.equals(healthCodeExportEnabled, other.healthCodeExportEnabled)
                && Objects.equals(externalIdValidationEnabled, other.externalIdValidationEnabled)
                && Objects.equals(emailVerificationEnabled, other.emailVerificationEnabled)
                && Objects.equals(externalIdRequiredOnSignup, other.externalIdRequiredOnSignup)
                && Objects.equals(minSupportedAppVersions, other.minSupportedAppVersions)
                && Objects.equals(pushNotificationARNs, other.pushNotificationARNs)
                && Objects.equals(installLinks, other.installLinks)
                && Objects.equals(disableExport, other.disableExport)
                && Objects.equals(emailSignInEnabled, other.emailSignInEnabled)
                && Objects.equals(phoneSignInEnabled, other.phoneSignInEnabled)
                && Objects.equals(accountLimit, other.accountLimit)
                && Objects.equals(oauthProviders, other.oauthProviders)
                && Objects.equals(appleAppLinks, other.appleAppLinks)
                && Objects.equals(androidAppLinks, other.androidAppLinks)
                && Objects.equals(reauthenticationEnabled,  other.reauthenticationEnabled)
                && Objects.equals(autoVerificationPhoneSuppressed, other.autoVerificationPhoneSuppressed)
                && Objects.equals(verifyChannelOnSignInEnabled,  other.verifyChannelOnSignInEnabled)
                && Objects.equals(defaultTemplates, other.defaultTemplates);
    }

    @Override
    public String toString() {
        return String.format(
                "DynamoApp [name=%s, shortName=%s, active=%s, sponsorName=%s, identifier=%s, automaticCustomEvents=%s"
                        + "autoVerificationEmailSuppressed=%b, minAgeOfConsent=%s, exporterVersion=%s, participantIpLockingEnabled=%b, "
                        + "appIdExcludedInExport=%b, supportEmail=%s, synapseDataAccessTeamId=%s, synapseProjectId=%s, "
                        + "technicalEmail=%s, uploadValidationStrictness=%s, consentNotificationEmail=%s, "
                        + "consentNotificationEmailVerified=%s, version=%s, userProfileAttributes=%s, taskIdentifiers=%s, "
                        + "activityEventKeys=%s, dataGroups=%s, passwordPolicy=%s, strictUploadValidationEnabled=%s, "
                        + "healthCodeExportEnabled=%s, emailVerificationEnabled=%s, externalIdValidationEnabled=%s, "
                        + "externalIdRequiredOnSignup=%s, minSupportedAppVersions=%s, usesCustomExportSchedule=%s, "
                        + "pushNotificationARNs=%s, installLinks=%s, disableExport=%s, emailSignInEnabled=%s, "
                        + "phoneSignInEnabled=%s, accountLimit=%s, oauthProviders=%s, appleAppLinks=%s, androidAppLinks=%s, "
                        + "reauthenticationEnabled=%s, autoVerificationPhoneSuppressed=%s, verifyChannelOnSignInEnabled=%s, "
                        + "defaultTemplates=%s]",
                name, shortName, active, sponsorName, identifier, automaticCustomEvents,
                autoVerificationEmailSuppressed, minAgeOfConsent, exporterVersion, participantIpLockingEnabled, appIdExcludedInExport,
                supportEmail, synapseDataAccessTeamId, synapseProjectId, technicalEmail, uploadValidationStrictness,
                consentNotificationEmail, consentNotificationEmailVerified, version, profileAttributes, taskIdentifiers,
                activityEventKeys, dataGroups, passwordPolicy, strictUploadValidationEnabled, healthCodeExportEnabled,
                emailVerificationEnabled, externalIdValidationEnabled, externalIdRequiredOnSignup,
                minSupportedAppVersions, usesCustomExportSchedule, pushNotificationARNs, installLinks, disableExport,
                emailSignInEnabled, phoneSignInEnabled, accountLimit, oauthProviders, appleAppLinks, androidAppLinks,
                reauthenticationEnabled, autoVerificationPhoneSuppressed, verifyChannelOnSignInEnabled,
                defaultTemplates);
    }
}

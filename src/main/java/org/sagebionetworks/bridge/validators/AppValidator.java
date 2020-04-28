package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.commons.validator.routines.EmailValidator;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.Tuple;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.apps.AndroidAppLink;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.AppleAppLink;
import org.sagebionetworks.bridge.models.apps.OAuthProvider;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.upload.UploadFieldSize;
import org.sagebionetworks.bridge.upload.UploadUtil;

import com.google.common.collect.Sets;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class AppValidator implements Validator {
    public static final AppValidator INSTANCE = new AppValidator();
    
    private static final int MAX_SYNAPSE_LENGTH = 250;
    private static final int METADATA_MAX_BYTES = 2500;
    private static final int METADATA_MAX_COLUMNS = 20;
    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile("^[0-9a-fA-F:]{95,95}$");
    protected static final String EMAIL_ERROR = "is not a comma-separated list of email addresses";
    
    private static final Set<String> RESERVED_ATTR_NAMES = Sets.newHashSet();
    static {
        Field[] fields = StudyParticipant.class.getDeclaredFields();
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                RESERVED_ATTR_NAMES.add(field.getName());
            }
        }
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return App.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        App app = (App)obj;
        if (isBlank(app.getIdentifier())) {
            errors.rejectValue("identifier", "is required");
        } else {
            if (!app.getIdentifier().matches(BridgeConstants.BRIDGE_IDENTIFIER_PATTERN)) {
                errors.rejectValue("identifier", BridgeConstants.BRIDGE_IDENTIFIER_ERROR);
            }
            if (app.getIdentifier().length() < 2) {
                errors.rejectValue("identifier", "must be at least 2 characters");
            }
        }
        if (app.getActivityEventKeys().stream()
                .anyMatch(k -> !k.matches(BridgeConstants.BRIDGE_EVENT_ID_PATTERN))) {
            errors.rejectValue("activityEventKeys", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
        }
        if (app.getAutomaticCustomEvents() != null) {
            for (Map.Entry<String, String> entry : app.getAutomaticCustomEvents().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                // Validate that the key follows the same rules for activity event keys
                if (!key.matches(BridgeConstants.BRIDGE_EVENT_ID_PATTERN)) {
                    errors.rejectValue("automaticCustomEvents["+key+"]", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
                }
                
                Tuple<String> autoEventSpec = BridgeUtils.parseAutoEventValue(value);
                
                String originEventKey = autoEventSpec.getLeft();
                if (!specifiesValidEventKey(app.getActivityEventKeys(), originEventKey)) {
                    errors.rejectValue("automaticCustomEvents["+key+"]", "'" + originEventKey + "' is not a valid custom or system event ID");
                }
                String periodString = autoEventSpec.getRight();
                try {
                    Period.parse(periodString);
                } catch(DateTimeParseException e) {
                    errors.rejectValue("automaticCustomEvents["+key+"]", "'" + periodString + "' is not a valid ISO 8601 period");
                }
            }
        }
        if (isBlank(app.getName())) {
            errors.rejectValue("name", "is required");
        }
        if (app.isReauthenticationEnabled() == null) {
            errors.rejectValue("reauthenticationEnabled", "is required");
        }
        if (app.getShortName() != null && app.getShortName().length() > 10) {
            errors.rejectValue("shortName", "must be 10 characters or less");
        }
        if (isBlank(app.getSponsorName())) {
            errors.rejectValue("sponsorName", "is required");
        }
        if (isBlank(app.getSupportEmail())) {
            errors.rejectValue("supportEmail", "is required");
        } else {
            validateEmail(errors, app.getSupportEmail(), "supportEmail");
        }
        validateEmail(errors, app.getTechnicalEmail(), "technicalEmail");
        validateEmail(errors, app.getConsentNotificationEmail(), "consentNotificationEmail");

        // uploadMetadatafieldDefinitions
        List<UploadFieldDefinition> uploadMetadataFieldDefList = app.getUploadMetadataFieldDefinitions();
        if (!uploadMetadataFieldDefList.isEmpty()) {
            UploadFieldDefinitionListValidator.INSTANCE.validate(app.getUploadMetadataFieldDefinitions(), errors,
                    "uploadMetadataFieldDefinitions");

            // Check max size for metadata fields.
            UploadFieldSize fieldSize = UploadUtil.calculateFieldSize(uploadMetadataFieldDefList);
            if (fieldSize.getNumBytes() > METADATA_MAX_BYTES) {
                errors.rejectValue("uploadMetadataFieldDefinitions",
                        "cannot be greater than 2500 bytes combined");
            }
            if (fieldSize.getNumColumns() > METADATA_MAX_COLUMNS) {
                errors.rejectValue("uploadMetadataFieldDefinitions",
                        "cannot be greater than 20 columns combined");
            }
        }
        // These *should* be set if they are null, with defaults
        if (app.getPasswordPolicy() == null) {
            errors.rejectValue("passwordPolicy", "is required");
        } else {
            errors.pushNestedPath("passwordPolicy");
            PasswordPolicy policy = app.getPasswordPolicy();
            if (!isInRange(policy.getMinLength(), 2)) {
                errors.rejectValue("minLength", "must be 2-"+PasswordPolicy.FIXED_MAX_LENGTH+" characters");
            }
            errors.popNestedPath();
        }
        if (app.getMinAgeOfConsent() < 0) {
            errors.rejectValue("minAgeOfConsent", "must be zero (no minimum age of consent) or higher");
        }
        if (app.getAccountLimit() < 0) {
            errors.rejectValue("accountLimit", "must be zero (no limit set) or higher");
        }
        
        for (String userProfileAttribute : app.getUserProfileAttributes()) {
            if (RESERVED_ATTR_NAMES.contains(userProfileAttribute)) {
                String msg = String.format("'%s' conflicts with existing user profile property", userProfileAttribute);
                errors.rejectValue("userProfileAttributes", msg);
            }
            // For backwards compatibility, we require this to be a valid JavaScript identifier.
            if (!userProfileAttribute.matches(BridgeConstants.JS_IDENTIFIER_PATTERN)) {
                String msg = String.format("'%s' must contain only digits, letters, underscores and dashes, and cannot start with a dash", userProfileAttribute);
                errors.rejectValue("userProfileAttributes", msg);
            }
        }
        validateDataGroupNamesAndFitForSynapseExport(errors, app.getDataGroups());

        // emailVerificationEnabled=true (public app):
        //     externalIdValidationEnabled and externalIdRequiredOnSignup can vary independently
        // emailVerificationEnabled=false:
        //     externalIdValidationEnabled and externalIdRequiredOnSignup must both be true
        if (!app.isEmailVerificationEnabled() && !app.isExternalIdRequiredOnSignup()) {
            errors.rejectValue("externalIdRequiredOnSignup", "cannot be disabled if email verification has been disabled");
        }

        // Links in installedLinks are length-constrained by SMS.
        if (!app.getInstallLinks().isEmpty()) {
            for (Map.Entry<String,String> entry : app.getInstallLinks().entrySet()) {
                if (isBlank(entry.getValue())) {
                    errors.rejectValue("installLinks", "cannot be blank");
                } else if (entry.getValue().length() > BridgeConstants.APP_LINK_MAX_LENGTH) {
                    errors.rejectValue("installLinks", "cannot be longer than " +
                            BridgeConstants.APP_LINK_MAX_LENGTH + " characters");
                }
            }
        }        
        
        for (Map.Entry<String, OAuthProvider> entry : app.getOAuthProviders().entrySet()) {
            String fieldName = "oauthProviders["+entry.getKey()+"]";
            OAuthProvider provider = entry.getValue();
            if (provider == null) {
                errors.rejectValue(fieldName, "is required");
            } else {
                errors.pushNestedPath(fieldName);
                if (isBlank(provider.getClientId())) {
                    errors.rejectValue("clientId", "is required");
                }
                if (isBlank(provider.getSecret())) {
                    errors.rejectValue("secret", "is required");
                }
                if (isBlank(provider.getEndpoint())) {
                    errors.rejectValue("endpoint", "is required");
                }
                if (isBlank(provider.getCallbackUrl())) {
                    errors.rejectValue("callbackUrl", "is required");
                }
                errors.popNestedPath();
            }
        }
        
        // app link configuration is not required, but if it is provided, we validate it
        if (app.getAppleAppLinks() != null && !app.getAppleAppLinks().isEmpty()) {
            validateAppLinks(errors, "appleAppLinks", app.getAppleAppLinks(), (AppleAppLink link) -> {
                if (isBlank(link.getAppId())) {
                    errors.rejectValue("appID", "cannot be blank or null");
                }
                if (link.getPaths() == null || link.getPaths().isEmpty()) {
                    errors.rejectValue("paths", "cannot be null or empty");
                } else {
                    for (int j=0; j < link.getPaths().size(); j++) {
                        String path =  link.getPaths().get(j);
                        if (isBlank(path)) {
                            errors.rejectValue("paths["+j+"]", "cannot be blank or empty");
                        }
                    }                        
                }
                return link.getAppId();
            });
        }
        if (app.getAndroidAppLinks() != null && !app.getAndroidAppLinks().isEmpty()) {
            validateAppLinks(errors, "androidAppLinks", app.getAndroidAppLinks(), (AndroidAppLink link) -> {
                if (isBlank(link.getNamespace())) {
                    errors.rejectValue("namespace", "cannot be blank or null");
                }
                if (isBlank(link.getPackageName())) {
                    errors.rejectValue("package_name", "cannot be blank or null");
                }
                if (link.getFingerprints() == null || link.getFingerprints().isEmpty()) {
                    errors.rejectValue("sha256_cert_fingerprints", "cannot be null or empty");
                } else {
                    for (int i=0; i < link.getFingerprints().size(); i++) {
                        String fingerprint = link.getFingerprints().get(i);
                        if (isBlank(fingerprint)) {
                            errors.rejectValue("sha256_cert_fingerprints["+i+"]", "cannot be null or empty");
                        } else if (!FINGERPRINT_PATTERN.matcher(fingerprint).matches()){
                            errors.rejectValue("sha256_cert_fingerprints["+i+"]", "is not a SHA 256 fingerprint");
                        }
                    }
                }
                return link.getNamespace() + "." + link.getPackageName();
            });
        }
    }
    
    private boolean specifiesValidEventKey(Set<String> customKeys, String proposedKey) {
        for (ActivityEventObjectType type : ActivityEventObjectType.UNARY_EVENTS) {
            if (type.name().toLowerCase().equals(proposedKey)) {
                return true;
            }
        }
        return customKeys.contains(proposedKey);
    }
    
    private void validateEmail(Errors errors, String emailString, String fieldName) {
        if (emailString != null) {
            Set<String> emails = BridgeUtils.commaListToOrderedSet(emailString);
            // The "if" clause catches cases like "" which are weeded out of the ordered set
            if (emails.isEmpty()) {
                errors.rejectValue(fieldName, EMAIL_ERROR);
            } else {
                for (String email : emails) {
                    if (!EmailValidator.getInstance().isValid(email)) {
                        errors.rejectValue(fieldName, EMAIL_ERROR);
                    }
                }
            }
        }
    }
    
    private <T> void validateAppLinks(Errors errors, String propName, List<T> appLinks, Function<T,String> itemValidator) {
        boolean hasNotRecordedDuplicates = true;
        Set<String> uniqueAppIDs = Sets.newHashSet();
        for (int i=0; i < appLinks.size(); i++) {
            T link = appLinks.get(i);
            String fieldName = propName+"["+i+"]";
            if (link == null) {
                errors.rejectValue(fieldName, "cannot be null");
            } else {
                errors.pushNestedPath(fieldName);
                String id = itemValidator.apply(link);
                errors.popNestedPath();
                if (id != null && hasNotRecordedDuplicates && !uniqueAppIDs.add(id)) {
                    errors.rejectValue(propName, "cannot contain duplicate entries");
                    hasNotRecordedDuplicates = false;
                }
            }
        }
    }
    
    private boolean isInRange(int value, int min) {
        return (value >= min && value <= PasswordPolicy.FIXED_MAX_LENGTH);
    }
    
    private void validateDataGroupNamesAndFitForSynapseExport(Errors errors, Set<String> dataGroups) {
        if (dataGroups != null) {
            for (String group : dataGroups) {
                if (!group.matches(BridgeConstants.SYNAPSE_IDENTIFIER_PATTERN)) {
                    errors.rejectValue("dataGroups", "contains invalid tag '"+group+"' (only letters, numbers, underscore and dash allowed)");
                }
            }
            String ser = COMMA_SPACE_JOINER.join(dataGroups);
            if (ser.length() > MAX_SYNAPSE_LENGTH) {
                errors.rejectValue("dataGroups", "will not export to Synapse (string is over "+MAX_SYNAPSE_LENGTH+" characters: '" + ser + "')");
            }
        }
    }

}

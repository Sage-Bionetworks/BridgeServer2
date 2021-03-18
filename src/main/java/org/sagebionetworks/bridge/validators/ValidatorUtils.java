package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DurationFieldType;
import org.joda.time.Period;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.HasLang;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;

public class ValidatorUtils {
    
    static final String WRONG_PERIOD = "%s can only specify minute, hour, day, or week duration units";
    static final String WRONG_LONG_PERIOD = "%s can only specify day or week duration units";
    static final String DUPLICATE_LANG = "%s is a duplicate message under the same language code";
    static final String INVALID_LANG = "%s is not a valid ISO 639 alpha-2 or alpha-3 language code";
    static final String INVALID_HEX_TRIPLET = "%s is not in hex triplet format (ie #FFFFF format)";
    static final String HEX_TRIPLET_FORMAT = "^#[0-9a-fA-F]{6}$";

    private static final Set<DurationFieldType> FIXED_LENGTH_DURATIONS = ImmutableSet.of(DurationFieldType.minutes(),
            DurationFieldType.hours(), DurationFieldType.days(), DurationFieldType.weeks());
    
    private static final Set<DurationFieldType> FIXED_LENGTH_LONG_DURATIONS = ImmutableSet.of(DurationFieldType.days(),
            DurationFieldType.weeks());
    
    public static boolean participantHasValidIdentifier(StudyParticipant participant) {
        Phone phone = participant.getPhone();
        String email = participant.getEmail();
        String anyExternalId = participant.getExternalIds().isEmpty() ? null : 
            Iterables.getFirst(participant.getExternalIds().entrySet(), null).getValue();
        String synapseUserId = participant.getSynapseUserId();
        return (email != null || isNotBlank(anyExternalId) || phone != null || isNotBlank(synapseUserId));
    }
    
    public static boolean accountHasValidIdentifier(Account account) {
        Phone phone = account.getPhone();
        String email = account.getEmail();
        String synapseUserId = account.getSynapseUserId();
        Set<String> externalIds = BridgeUtils.collectExternalIds(account);
        return (email != null || !externalIds.isEmpty() || phone != null || isNotBlank(synapseUserId));
    }

    public static void validatePassword(Errors errors, PasswordPolicy passwordPolicy, String password) {
        if (StringUtils.isBlank(password)) {
            errors.rejectValue("password", "is required");
        } else {
            if (passwordPolicy.getMinLength() > 0 && password.length() < passwordPolicy.getMinLength()) {
                errors.rejectValue("password", "must be at least "+passwordPolicy.getMinLength()+" characters");
            }
            if (passwordPolicy.isNumericRequired() && !password.matches(".*\\d+.*")) {
                errors.rejectValue("password", "must contain at least one number (0-9)");
            }
            if (passwordPolicy.isSymbolRequired() && !password.matches(".*\\p{Punct}+.*")) {
                errors.rejectValue("password", "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
            }
            if (passwordPolicy.isLowerCaseRequired() && !password.matches(".*[a-z]+.*")) {
                errors.rejectValue("password", "must contain at least one lowercase letter (a-z)");
            }
            if (passwordPolicy.isUpperCaseRequired() && !password.matches(".*[A-Z]+.*")) {
                errors.rejectValue("password", "must contain at least one uppercase letter (A-Z)");
            }
        }
    }
    
    private static void validateLanguageSet(Errors errors, List<? extends HasLang> items, String fieldName) {
        if (items.isEmpty()) {
            return;
        }
        Set<String> visited = new HashSet<>();
        for (int i=0; i < items.size(); i++) {
            HasLang item = items.get(i);
            errors.pushNestedPath(fieldName + "[" + i + "]");
            
            if (isBlank(item.getLang())) {
                errors.rejectValue("lang", CANNOT_BE_BLANK);
            } else {
                if (visited.contains(item.getLang())) {
                    errors.rejectValue("lang", DUPLICATE_LANG);
                }
                visited.add(item.getLang());
                
                Locale locale = new Locale.Builder().setLanguageTag(item.getLang()).build();
                if (!LocaleUtils.isAvailableLocale(locale)) {
                    errors.rejectValue("lang", INVALID_LANG);
                }
            }
            errors.popNestedPath();
        }
    }
    
    public static void validateLabels(Errors errors, List<Label> labels) {
        if (labels == null || labels.isEmpty()) {
            return;
        }
        validateLanguageSet(errors, labels, "labels");    
        for (int j=0; j < labels.size(); j++) {
            Label label = labels.get(j);

            if (isBlank(label.getValue())) {
                errors.pushNestedPath("labels[" + j + "]");
                errors.rejectValue("value", CANNOT_BE_BLANK);
                errors.popNestedPath();
            }
        }
    }
    
    public static void validateMessages(Errors errors, List<NotificationMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        validateLanguageSet(errors, messages, "messages");
        boolean englishDefault = false;
        for (int j=0; j < messages.size(); j++) {
            NotificationMessage message = messages.get(j);
            
            if ("en".equalsIgnoreCase(message.getLang())) {
                englishDefault = true;
            }
            errors.pushNestedPath("messages[" + j + "]");
            if (isBlank(message.getSubject())) {
                errors.rejectValue("subject", CANNOT_BE_BLANK);
            } else if (message.getSubject().length() > 40) {
                errors.rejectValue("subject", "must be 40 characters or less");
            }
            if (isBlank(message.getMessage())) {
                errors.rejectValue("message", CANNOT_BE_BLANK);
            } else if (message.getMessage().length() > 60) {
                errors.rejectValue("message", "must be 60 characters or less");
            }
            errors.popNestedPath();
            if (!englishDefault) {
                errors.rejectValue("messages", "must include an English-language message as a default");
            }
        }
    }
    
    public static final void validateColorScheme(Errors errors, ColorScheme cs, String fieldName) {
        if (cs != null) {
            errors.pushNestedPath(fieldName);
            if (cs.getBackground() != null && !cs.getBackground().matches(HEX_TRIPLET_FORMAT)) {
                errors.rejectValue("background", INVALID_HEX_TRIPLET);
            }
            if (cs.getForeground() != null && !cs.getForeground().matches(HEX_TRIPLET_FORMAT)) {
                errors.rejectValue("foreground", INVALID_HEX_TRIPLET);
            }
            if (cs.getActivated() != null && !cs.getActivated().matches(HEX_TRIPLET_FORMAT)) {
                errors.rejectValue("activated", INVALID_HEX_TRIPLET);
            }
            if (cs.getInactivated() != null && !cs.getInactivated().matches(HEX_TRIPLET_FORMAT)) {
                errors.rejectValue("inactivated", INVALID_HEX_TRIPLET);
            }
            errors.popNestedPath();
        }
    }

    public static final int periodInMinutes(Period period) {
        int minutes = period.getMinutes();
        minutes += (period.getHours() * 60);
        minutes += (period.getDays() * 24 * 60);
        minutes += (period.getWeeks() * 7 * 24 * 60);
        return minutes;
    }
    
    public static void validateFixedLengthPeriod(Errors errors, Period period, String fieldName, boolean required) {
        validateDuration(FIXED_LENGTH_DURATIONS, errors, period, fieldName, WRONG_PERIOD, required);
    }

    public static void validateFixedLengthLongPeriod(Errors errors, Period period, String fieldName, boolean required) {
        validateDuration(FIXED_LENGTH_LONG_DURATIONS, errors, period, fieldName, WRONG_LONG_PERIOD, required);
    }

    private static void validateDuration(Set<DurationFieldType> durations, Errors errors, Period period,
            String fieldName, String error, boolean required) {
        if (period == null) {
            if (required) {
                errors.rejectValue(fieldName, CANNOT_BE_NULL);
            }
            return;
        }
        DurationFieldType[] fields = period.getFieldTypes();
        for (DurationFieldType type : fields) {
            if (!durations.contains(type) && period.get(type) != 0) {
                errors.rejectValue(fieldName, error);
                break;
            }
        }
        int[] values = period.getValues();
        for (int i=0; i < values.length; i++) {
            if (values[i] < 0) {
                errors.rejectValue(fieldName, CANNOT_BE_NEGATIVE);
                break;
            }
        }
    }
    
}

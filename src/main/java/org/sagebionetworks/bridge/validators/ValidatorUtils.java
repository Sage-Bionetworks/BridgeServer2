package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.DUPLICATE_LANG;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_LANG;

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
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.schedules2.Localized;

public class ValidatorUtils {
    
    private static final Set<DurationFieldType> PROHIBITED_DURATIONS = ImmutableSet.of(DurationFieldType.centuries(),
            DurationFieldType.eras(), DurationFieldType.halfdays(), DurationFieldType.millis(),
            DurationFieldType.months(), DurationFieldType.seconds(), DurationFieldType.weekyears(),
            DurationFieldType.years());
    
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

    public static void validateLanguageSet(Errors errors, List<? extends Localized> items, String fieldName) {
        if (items.isEmpty()) {
            return;
        }
        Set<String> visited = new HashSet<>();
        for (int i=0; i < items.size(); i++) {
            Localized item = items.get(i);
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
    
    public static boolean validatePeriod(Period period) {
        if (period != null) {
            for (DurationFieldType type : PROHIBITED_DURATIONS) {
                if (PROHIBITED_DURATIONS.contains(type) && period.get(type) > 0) {
                    return false;
                }
            }
        }
        return true;
    }
}

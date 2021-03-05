package org.sagebionetworks.bridge.validators;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.WEEKS;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DurationFieldType;
import org.joda.time.Period;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;

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
    
    public static void validatePeriod(Period period, String fieldName, Errors errors) {
        for (DurationFieldType type : PROHIBITED_DURATIONS) {
            if (PROHIBITED_DURATIONS.contains(type) && period.get(type) > 0) {
                errors.rejectValue(fieldName, 
                        "can only specify minute, hour, day, or week duration units");
            }
        }
    }
}

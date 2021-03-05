package org.sagebionetworks.bridge.validators;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.WEEKS;

import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validates that a period of time is expressed in units that can be handled
 * relative to a LocalTime, and not units that require a real-world starting
 * time to calculate accurately. These are not allowed in the v2 of our 
 * scheduler.
 */
public class PeriodValidator implements Validator {

    public static final PeriodValidator INSTANCE = new PeriodValidator();
    private static final Set<ChronoUnit> ALLOWABLE_UNITS = ImmutableSet.of(MINUTES, HOURS, DAYS, WEEKS);

    @Override
    public boolean supports(Class<?> clazz) {
        return PeriodValidator.class.isAssignableFrom(clazz);
    }
    

    @Override
    public void validate(Object object, Errors errors) {
        validate(object, errors.getNestedPath(), errors);
    }

    public void validate(Object object, String fieldName, Errors errors) {
        Period period = (Period)object;
        
        for (ChronoUnit unit : ChronoUnit.values()) {
            if (!ALLOWABLE_UNITS.contains(unit) && period.get(unit) > 0) {
                errors.rejectValue(fieldName, "can only contain minute, hour, day, or week duration units");
            }
        }
    }
}

package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestConstants.EMAIL;
import static org.sagebionetworks.bridge.TestConstants.PHONE;
import static org.sagebionetworks.bridge.TestConstants.SYNAPSE_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NEGATIVE;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_NULL;
import static org.sagebionetworks.bridge.validators.Validate.WRONG_LONG_PERIOD;
import static org.sagebionetworks.bridge.validators.Validate.WRONG_PERIOD;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedLongPeriod;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateFixedPeriod;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.joda.time.Period;
import org.mockito.Mockito;
import org.springframework.validation.Errors;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.Enrollment;

public class ValidatorUtilsTest extends Mockito {
    
    @Test
    public void participantHasValidIdentifierValidEmail() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(EMAIL).build();
        assertTrue(ValidatorUtils.participantHasValidIdentifier(participant));
    }
    
    @Test
    public void participantHasValidIdentifierValiPhone() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withPhone(PHONE).build();
        assertTrue(ValidatorUtils.participantHasValidIdentifier(participant));
    }

    @Test
    public void participantHasValidIdentifierValidExternalIds() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withExternalIds(ImmutableMap.of(TEST_STUDY_ID, "extId")).build();
        assertTrue(ValidatorUtils.participantHasValidIdentifier(participant));
    }
    
    @Test
    public void participantHasValidIdentifierValidSynapseUserId() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withSynapseUserId(SYNAPSE_USER_ID).build();
        assertTrue(ValidatorUtils.participantHasValidIdentifier(participant));
    }
    
    @Test
    public void participantHasValidIdentifierInvalid() {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withExternalIds(ImmutableMap.of()).build();
        assertFalse(ValidatorUtils.participantHasValidIdentifier(participant));
        
        participant = new StudyParticipant.Builder().build();
        assertFalse(ValidatorUtils.participantHasValidIdentifier(participant));
    }

    @Test
    public void accountHasValidIdentifierValidEmail() {
        Account account = Account.create();
        account.setEmail(EMAIL);
        assertTrue(ValidatorUtils.accountHasValidIdentifier(account));
    }
    
    @Test
    public void accountHasValidIdentifierValiPhone() {
        Account account = Account.create();
        account.setPhone(PHONE);
        assertTrue(ValidatorUtils.accountHasValidIdentifier(account));
    }
    
    @Test
    public void accountHasValidIdentifierValidEnrollment() {
        Account account = Account.create();
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        Enrollment en2 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID, "externalID");
        account.setEnrollments(ImmutableSet.of(en1, en2));

        assertTrue(ValidatorUtils.accountHasValidIdentifier(account));
    }
    
    @Test
    public void accountHasValidIdentifierValidSynapseUserId() {
        Account account = Account.create();
        account.setSynapseUserId(SYNAPSE_USER_ID);
        assertTrue(ValidatorUtils.accountHasValidIdentifier(account));
    }
    
    @Test
    public void accountHasValidIdentifierInvalid() {
        Account account = Account.create();
        account.setEnrollments(null);
        assertFalse(ValidatorUtils.accountHasValidIdentifier(account));
        
        account = Account.create();
        Enrollment en1 = Enrollment.create(TEST_APP_ID, TEST_STUDY_ID, TEST_USER_ID);
        account.setEnrollments(ImmutableSet.of(en1));
        assertFalse(ValidatorUtils.accountHasValidIdentifier(account));

        account = Account.create();
        account.setEnrollments(ImmutableSet.of());
        assertFalse(ValidatorUtils.accountHasValidIdentifier(account));
    }
    
    // Only minutes, hours, days, and weeks are allowed for the more fine-grained Duration
    // fields, and days or weeks for the longer Duration fields.
    
    @Test
    public void validateFixedPeriodMonthsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3M");
        
        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_PERIOD);
    }

    @Test
    public void validateFixedPeriodSecondsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("PT180S");
        
        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_PERIOD);
    }

    @Test
    public void validateFixedPeriodYearsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3Y");
        
        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_PERIOD);
    }
    
    @Test
    public void validateFixedPeriodValid() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W");
        
        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors, never()).rejectValue(any(), any());
    }
    
    @Test
    public void validateFixedPeriodValidMinutesPeriod() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("PT30M");

        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors, never()).rejectValue(any(), any());
    }
    
    @Test
    public void validateFixedPeriodMixedWorks() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W3DT30M");

        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void validateFixedPeriodMixedFails() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3Y2W3DT30M");

        validateFixedPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_PERIOD);
    }

    @Test
    public void validateFixedPeriodRequiredIsMissing() {
        Errors errors = mock(Errors.class);

        validateFixedPeriod(errors, null, "period", true);
        
        verify(errors).rejectValue("period", CANNOT_BE_NULL);
    }
    
    @Test
    public void validateFixedPeriodCannotBeNegative() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W-3D");

        validateFixedPeriod(errors, period, "period", true);

        // Despite adding up to a positive value, we don't allow it. 
        verify(errors).rejectValue("period", CANNOT_BE_NEGATIVE);
    }

    // For the long period, only days and weeks are allowed.
    
    @Test
    public void validateFixedLongPeriodMonthsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3M");
        
        validateFixedLongPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_LONG_PERIOD);
    }

    @Test
    public void validateFixedLongPeriodSecondsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("PT180S");
        
        validateFixedLongPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_LONG_PERIOD);
    }

    @Test
    public void validateFixedLongPeriodYearsProhibited() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3Y");
        
        validateFixedLongPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_LONG_PERIOD);
    }
    
    @Test
    public void validateFixedLongWeeksValid() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W");
        
        validateFixedLongPeriod(errors, period, "period", false);
        
        verify(errors, never()).rejectValue(any(), any());
    }
    
    @Test
    public void validateFixedLongDaysValid() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P12D");
        
        validateFixedLongPeriod(errors, period, "period", false);
        
        verify(errors, never()).rejectValue(any(), any());
    }
    
    @Test
    public void validateFixedLongPeriodMixedWorks() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W3D");

        validateFixedLongPeriod(errors, period, "period", false);
        
        verify(errors, never()).rejectValue(any(), any());
    }

    @Test
    public void validateFixedLongPeriodMixedFails() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P3Y2W3DT30M");

        validateFixedLongPeriod(errors, period, "period", false);
        
        verify(errors).rejectValue("period", WRONG_LONG_PERIOD);
    }

    @Test
    public void validateFixedLongPeriodRequiredIsMissing() {
        Errors errors = mock(Errors.class);

        validateFixedLongPeriod(errors, null, "period", true);
        
        verify(errors).rejectValue("period", CANNOT_BE_NULL);
    }
    
    @Test
    public void validateFixedLongPeriodCannotBeNegative() {
        Errors errors = mock(Errors.class);
        Period period = Period.parse("P2W-3D");

        validateFixedLongPeriod(errors, period, "period", true);

        // Despite adding up to a positive value, we don't allow it. 
        verify(errors).rejectValue("period", CANNOT_BE_NEGATIVE);
    }    
    
    @Test
    public void periodInMinutes() {
        Period period = Period.parse("P3W2DT10H14M"); // 33,734 minutes
        assertEquals(ValidatorUtils.periodInMinutes(period), 33734);
        
        period = Period.parse("P0W0DT0H0M"); // 0 minutes
        assertEquals(ValidatorUtils.periodInMinutes(period), 0);
    }
}
